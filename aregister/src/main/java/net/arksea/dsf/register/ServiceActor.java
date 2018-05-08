package net.arksea.dsf.register;

import akka.actor.*;
import akka.dispatch.OnComplete;
import akka.japi.Creator;
import akka.pattern.Patterns;
import net.arksea.dsf.DSF;
import net.arksea.dsf.store.IRegisterStore;
import net.arksea.dsf.store.Instance;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scala.concurrent.duration.Duration;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 *
 * Created by xiaohaixing on 2018/4/18.
 */
public class ServiceActor extends AbstractActor {
    public final static String ACTOR_NAME_PRE = "dsfService-";
    private final Logger logger = LogManager.getLogger(ServiceActor.class);

    private final String serviceName;
    private String serialId; //识别实例集合是否变化的ID，用于减少同步消息的分发
    private final Map<String,String> attributes = new HashMap<>();
    private Map<String, InstanceInfo> instances = new HashMap<>();
    private Map<ActorRef, SubscriberInfo> subscriberMap = new HashMap<>();
    private final IRegisterStore store;
    private Cancellable loadServiceInfoTimer;  //从Store更新服务实例定时器
    private Cancellable checkAliveTimer;
    private static final int LOAD_SVC_DELAY_SECONDS = 300; //从注册服务器更新实例列表的周期(s)
    private static final int CHECK_ALIVE_SECONDS = 60; //测试服务是否存活

    public static Props props(String serviceId, IRegisterStore store) {
        return Props.create(ServiceActor.class, new Creator<ServiceActor>() {
            @Override
            public ServiceActor create() throws Exception {
                return new ServiceActor(serviceId, store);
            }
        });
    }

    public ServiceActor(String serviceName, IRegisterStore store) {
        this.serviceName = serviceName;
        this.store = store;
        this.serialId = "";
    }

    @Override
    public void preStart() {
        logger.trace("ServiceActor preStart : {}", serviceName);
        loadServiceInfo();
        handleCheckServiceAlive(null);
        loadServiceInfoTimer = context().system().scheduler().schedule(
            Duration.create(LOAD_SVC_DELAY_SECONDS, TimeUnit.SECONDS),
            Duration.create(LOAD_SVC_DELAY_SECONDS,TimeUnit.SECONDS),
            self(),new LoadServiceInfo(),context().dispatcher(),self());
        checkAliveTimer = context().system().scheduler().schedule(
            Duration.create(CHECK_ALIVE_SECONDS, TimeUnit.SECONDS),
            Duration.create(CHECK_ALIVE_SECONDS, TimeUnit.SECONDS),
            self(),new CheckServiceAlive(),context().dispatcher(),self());
    }

    //re-subscribe when restart
    @Override
    public void postStop() {
        if (loadServiceInfoTimer != null) {
            loadServiceInfoTimer.cancel();
            loadServiceInfoTimer = null;
        }
        if (checkAliveTimer != null) {
            checkAliveTimer.cancel();
            checkAliveTimer = null;
        }
        logger.trace("ServiceActor postStop : {}", serviceName);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(SubscriberTerminated.class,  this::handleSubscriberTerminated)
            .match(DSF.SyncSvcInstances.class,   this::handleSyncSvcInstances)
            .match(DSF.GetSvcInstances.class,   this::handleGetSvcInstances)
            .match(DSF.RegService.class,        this::handleRegService)
            .match(DSF.UnregService.class,      this::handleUnregService)
            .match(DSF.SubService.class,        this::handleSubService)
            .match(DSF.UnsubService.class,      this::handleUnsubService)
            .match(LoadServiceInfo.class,       this::handleLoadServiceInfo)
            .match(CheckServiceAlive.class,     this::handleCheckServiceAlive)
            .match(ServiceAlive.class,          this::handleServiceAlive)
            .build();
    }

    //-------------------------------------------------------------------------------
    private void handleRegService(DSF.RegService msg) {
        instances.put(msg.getAddr(),new InstanceInfo(msg.getName(),msg.getAddr(),msg.getPath(),true));
        this.serialId = makeSerialId();
        subscriberMap.keySet().forEach(actor -> {
            actor.tell(msg, self());
        });
        logger.info("Service REG : {}@{}", msg.getName(), msg.getAddr());
    }

    //-------------------------------------------------------------------------------
    private void handleUnregService(DSF.UnregService msg) {
        instances.remove(msg.getAddr());
        this.serialId = makeSerialId();
        subscriberMap.keySet().forEach(actor -> {
            actor.tell(msg, self());
        });
        logger.info("Service UNREG : {}@{}", msg.getName(), msg.getAddr());
    }

    //-------------------------------------------------------------------------------
    private void handleGetSvcInstances(DSF.GetSvcInstances msg) {
        logger.trace("Service.handleGetSvcInstances({}),instances.size={}", msg.getName(),instances.size());
        getSvcInstances();
    }
    private void handleSyncSvcInstances(DSF.SyncSvcInstances msg) {
        logger.trace("Service.handleSyncSvcInstances({},{}),instances.size={},", msg.getName(),msg.getSerialId(),instances.size());
        subscribeService(msg.getSubscriber(), sender());
        if (!this.serialId.equals(msg.getSerialId())) {
            getSvcInstances();
        }
    }
    private void getSvcInstances() {
        DSF.SvcInstances.Builder builder = DSF.SvcInstances.newBuilder()
            .setName(serviceName)
            .setSerialId(serialId);
        this.instances.forEach((addr,it) -> {
            builder.addInstances(
                DSF.Instance.newBuilder()
                    .setAddr(addr)
                    .setPath(it.path)
                    .setOnline(it.online)
                    .build());
        });
        sender().tell(builder.build(), self());
    }

    //-------------------------------------------------------------------------------
    private void handleSubService(DSF.SubService msg) {
        subscribeService(msg.getSubscriber(), sender());
    }
    private void subscribeService(String subscriberName, ActorRef subscriber) {
        if (!subscriberMap.containsKey(subscriber)) {
            logger.info("{} subscribe {} : {}",subscriberName, serviceName, subscriber);
            context().watchWith(subscriber, new SubscriberTerminated(subscriber));
            subscriberMap.put(subscriber, new SubscriberInfo(subscriberName));
        }
    }
    //-------------------------------------------------------------------------------
    private void handleUnsubService(DSF.UnsubService msg) {
        SubscriberInfo info = subscriberMap.remove(sender());
        logger.info("{} unsubscribe {} : {}", info.name, serviceName, sender());
    }
    //-------------------------------------------------------------------------------
    class SubscriberTerminated {
        public final ActorRef subscriber;
        SubscriberTerminated(ActorRef subscriber) {
            this.subscriber = subscriber;
        }
    }
    private void handleSubscriberTerminated(SubscriberTerminated msg) {
        logger.info("Subscriber terminated : {}, client={}", msg.subscriber,msg.subscriber);
        subscriberMap.remove(msg.subscriber);
        context().unwatch(msg.subscriber);
    }
    //-------------------------------------------------------------------------------
    private static int MAX_HISTORY_COUNT = 3;   //保存历史数据的周期数
    class InstanceInfo {
        public final String name;
        public final String addr;
        public final String path;
        public boolean online;
        public int lastHistoryIndex;
        public final ArrayList<Boolean> aliveHistory;

        public InstanceInfo(String name, String addr, String path, boolean online) {
            this.name = name;
            this.addr = addr;
            this.path = path;
            this.online = online;
            this.lastHistoryIndex = 0;
            aliveHistory = new ArrayList<>(MAX_HISTORY_COUNT);
            for (int i = 0; i< MAX_HISTORY_COUNT; ++i) {
                aliveHistory.add(online);
            }
        }
    }
    //-------------------------------------------------------------------------------
    class LoadServiceInfo {
    }
    private void handleLoadServiceInfo(LoadServiceInfo msg) {
        loadServiceInfo();
    }
    /**
     * 加载实例信息
     */
    private void loadServiceInfo() {
        List<Instance> list = store.getServiceInstances(serviceName);
        logger.trace("Load service info, instance size = {}", list.size());
        Map<String, InstanceInfo> newInstances = new HashMap<>();
        list.forEach(it -> {
            InstanceInfo old = instances.remove(it.getAddr());
            if (old == null) {
                newInstances.put(it.getAddr(), new InstanceInfo(serviceName,it.getAddr(),it.getPath(), false));
                this.serialId = makeSerialId();
                logger.info("Service ADD : {}@{}, online={}", serviceName, it.getAddr(), false);
            } else {
                newInstances.put(it.getAddr(), old);
            }
        });
        if (instances.size() > 0) {
            this.serialId = makeSerialId();
            for (String addr : instances.keySet()) {
                logger.info("Service DEL : {}@{}", serviceName, addr);
            }
            this.instances.clear();
        }
        this.instances = newInstances;
    }
    //-------------------------------------------------------------------------------
    private DSF.Ping ping = DSF.Ping.getDefaultInstance();
    private class CheckServiceAlive {}
    private void handleCheckServiceAlive(CheckServiceAlive msg) {
        this.instances.forEach((addr,it) -> {
            checkServiceAlive(it);
        });
    }
    private void checkServiceAlive(InstanceInfo instance) {
        logger.trace("Check servcie alive: {}@{} ",instance.name, instance.addr);
        ActorSelection service = context().actorSelection(instance.path);
        ActorRef self = self();
        Patterns.ask(service, ping, 5000).onComplete(new OnComplete<Object>() {
            @Override
            public void onComplete(Throwable failure, Object success) throws Throwable {
                boolean online = failure == null && success instanceof DSF.Pong;
                self.tell(new ServiceAlive(instance.addr, online), ActorRef.noSender());
            }
        }, context().dispatcher());
    }
    //------------------------------------------------------------------------------------
    class ServiceAlive {
        public final String addr;
        public final boolean online;
        public ServiceAlive(String addr, boolean online) {
            this.addr = addr;
            this.online = online;
        }
    }
    private void handleServiceAlive(ServiceAlive msg) {
        InstanceInfo info = instances.get(msg.addr);
        if (info != null) {
            info.aliveHistory.set(info.lastHistoryIndex, msg.online);
            if (++info.lastHistoryIndex >= MAX_HISTORY_COUNT) {
                info.lastHistoryIndex = 0;
            }
            int offlineCount = 0;
            for (boolean b : info.aliveHistory) {
                if (!b) {
                    ++offlineCount;
                }
            }
            boolean online = msg.online || offlineCount<MAX_HISTORY_COUNT;
            if (info.online != online) {
                info.online = online;
                if (online) {
                    logger.info("Service ONLINE : {}@{}", info.name, info.addr);
                } else {
                    logger.warn("Service OFFLINE : {}@{}", info.name, info.addr);
                }
            }
        }
    }
    //------------------------------------------------------------------------------------
    class SubscriberInfo {
        public final String name;
        public boolean active;
        public SubscriberInfo(String name) {
            this.name = name;
            this.active = true;
        }
    }

    private String makeSerialId() {
        //todo: 计算实例集合的HashCode或者MD5
        return UUID.randomUUID().toString();
    }
}
