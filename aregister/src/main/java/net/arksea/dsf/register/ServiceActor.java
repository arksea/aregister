package net.arksea.dsf.register;

import akka.actor.*;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent;
import akka.dispatch.OnComplete;
import akka.japi.Creator;
import akka.pattern.Patterns;
import net.arksea.dsf.DSF;
import net.arksea.dsf.store.IRegisterStore;
import net.arksea.dsf.store.Instance;
import net.arksea.dsf.store.LocalStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scala.concurrent.duration.Duration;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static akka.japi.Util.classTag;

/**
 *
 * Created by xiaohaixing on 2018/4/18.
 */
public class ServiceActor extends AbstractActor {
    public final static String ACTOR_NAME_PRE = "dsfService-";
    private final Logger logger = LogManager.getLogger(ServiceActor.class);
    private final static long UNREG_TIMEOUT = 24L * 3600_000L;
    private final static long OFFLINE_TIMEOUT = 3L * 24L * 3600_000L;
    private final String serviceName;
    private String serialId; //识别实例集合是否变化的ID，用于减少同步消息的分发
    private final Map<String,String> attributes = new HashMap<>();
    private Map<String, InstanceInfo> instances = new HashMap<>();
    private final Map<ActorRef, SubscriberInfo> subscriberMap = new HashMap<>();
    private final IRegisterStore store;
    private final ServiceStateLogger stateLogger;
    private Cancellable loadServiceInfoTimer;  //从Store更新服务实例定时器
    private Cancellable checkAliveTimer;
    private static final int LOAD_SVC_DELAY_SECONDS = 300; //从注册服务器更新实例列表的周期(s)
    private static final int CHECK_ALIVE_SECONDS = 60; //测试服务是否存活
    private final DSF.Ping ping = DSF.Ping.getDefaultInstance();
    private String lastStoreVersionID = "";
    private final Cluster cluster = Cluster.get(getContext().getSystem());

    public static Props props(String serviceId, IRegisterStore store, ServiceStateLogger stateLogger) {
        return Props.create(ServiceActor.class, new Creator<ServiceActor>() {
            @Override
            public ServiceActor create() throws Exception {
                return new ServiceActor(serviceId, store, stateLogger);
            }
        });
    }

    public ServiceActor(String serviceName, IRegisterStore store, ServiceStateLogger stateLogger) {
        this.serviceName = serviceName;
        this.store = store;
        this.serialId = "";
        this.stateLogger = stateLogger;
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
            .match(DSF.SyncSvcInstances.class,  this::handleSyncSvcInstances)
            .match(DSF.GetSvcInstances.class,   this::handleGetSvcInstances)
            .match(DSF.RegService.class,        this::handleRegService)
            .match(DSF.UnregService.class,      this::handleUnregService)
            .match(DSF.SubService.class,        this::handleSubService)
            .match(DSF.UnsubService.class,      this::handleUnsubService)
            .match(LoadServiceInfo.class,       this::handleLoadServiceInfo)
            .match(CheckServiceAlive.class,     this::handleCheckServiceAlive)
            .match(ServiceAlive.class,          this::handleServiceAlive)
            .match(DSF.GetService.class,        this::handleGetService)
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
        InstanceInfo info = instances.get(msg.getAddr());
        info.unregister();
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
            builder.addInstances(buildInstance(it));
        });
        sender().tell(builder.build(), self());
    }
    //-------------------------------------------------------------------------------
    private void handleGetService(DSF.GetService msg) {
        logger.trace("Service.handleGetService({}),instances.size={}", msg.getName(),instances.size());
        DSF.Service.Builder builder = DSF.Service.newBuilder()
            .setName(serviceName);
        this.instances.forEach((addr,it) ->
            builder.addInstances(buildInstance(it))
        );
        fillSubscriber(builder);
        sender().tell(builder.build(), self());
    }
    private DSF.Instance buildInstance(InstanceInfo it) {
        return DSF.Instance.newBuilder()
            .setAddr(it.addr)
            .setPath(it.path)
            .setOnline(it.isOnline())
            .setUnregistered(it.isUnregistered())
            .setRegisterTime(it.registerTime)
            .setUnregisterTime(it.getUnregisterTime())
            .setLastOfflineTime(it.getLastOfflineTime())
            .setLastOnlineTime(it.getLastOnlineTime())
            .build();
    }
    private void fillSubscriber(DSF.Service.Builder svc) {
        Map<String, Integer> counter = new HashMap<>();
        subscriberMap.forEach((ref, info) -> {
            Integer c = counter.get(info.name);
            if (c == null) {
                c = 1;
            } else {
                c = c+1;
            }
            counter.put(info.name, c);
        });
        counter.forEach((name, count) ->
            svc.addSubscribers(DSF.Subscriber.newBuilder().setName(name).setCount(count).build())
        );
    }
    //-------------------------------------------------------------------------------
    private void handleSubService(DSF.SubService msg) {
        subscribeService(msg.getSubscriber(), sender());
    }
    private void subscribeService(String subscriberName, ActorRef subscriber) {
        if (!subscriberMap.containsKey(subscriber)) {
            Address address = subscriber.path().address();
            String addr = address.host().get() + ":" + address.port().get();
            logger.info("{}@{} subscribe {}",subscriberName, addr, serviceName);
            context().unwatch(subscriber);
            context().watchWith(subscriber, new SubscriberTerminated(subscriberName, subscriber));
            subscriberMap.put(subscriber, new SubscriberInfo(subscriberName));
        }
    }
    //-------------------------------------------------------------------------------
    private void handleUnsubService(DSF.UnsubService msg) {
        SubscriberInfo info = subscriberMap.remove(sender());
        logger.info("{}@{} unsubscribe {}", info.name, sender().path().address(), serviceName);
    }
    //-------------------------------------------------------------------------------
    class SubscriberTerminated {
        public final ActorRef subRef;
        public final String subName;
        SubscriberTerminated(String subName, ActorRef ref) {
            this.subName = subName;
            this.subRef = ref;
        }
    }
    private void handleSubscriberTerminated(SubscriberTerminated msg) {
        Address address = msg.subRef.path().address();
        String addr = address.host().get() + ":" + address.port().get();
        logger.info("{}@{} unsubscribe {} because terminated", msg.subName, addr, serviceName);
        subscriberMap.remove(msg.subRef);
        context().unwatch(msg.subRef);
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
        List<Instance> list;
        if (store == null) {
            list = loadFromLocalFile();
        } else {
            try {
                String verId = store.getVersionID(serviceName);
                if (lastStoreVersionID.equals(verId)) {
                    list = null;
                } else {
                    list = store.getServiceInstances(serviceName);
                    lastStoreVersionID = verId;
                    logger.trace("Load service list from register store succeed: {}", serviceName);
                }
            } catch (Exception ex) {
                list = loadFromLocalFile();
            }
        }

        if (list != null) {
            boolean changed = false;
            logger.trace("Load service info, instance size = {}", list.size());
            Map<String, InstanceInfo> newInstances = new HashMap<>();
            for (Instance it : list) {
                InstanceInfo old = instances.remove(it.getAddr());
                if (old == null) {
                    changed = true;
                    newInstances.put(it.getAddr(), new InstanceInfo(serviceName, it.getAddr(), it.getPath(), false));
                    logger.info("Service ADD : {}@{}, online={}", serviceName, it.getAddr(), false);
                } else {
                    newInstances.put(it.getAddr(), old);
                }
            }
            if (!instances.isEmpty()) {
                changed = true;
                long now = System.currentTimeMillis();
                for (Map.Entry<String, InstanceInfo> e: instances.entrySet()) {
                    InstanceInfo i = e.getValue();
                    if (i.isUnregistered() && now - i.getUnregisterTime() < UNREG_TIMEOUT) {
                        newInstances.put(i.addr, i);
                    } else {
                        logger.info("Service DEL : {}@{}", serviceName, i.addr);
                    }
                }
                this.instances.clear();
            }
            if (changed) {
                this.serialId = makeSerialId();
                saveToLocalFile(list);
            }
            this.instances = newInstances;
        }
    }

    private List<Instance> loadFromLocalFile() {
        try {
            List<Instance> list = LocalStore.load(serviceName);
            logger.info("Load service list form local cache file succeed: {}",serviceName);
            return list;
        } catch (Exception ex1) {
            logger.warn("Load service list form local cache file failed: {}",serviceName,ex1);
            return null;
        }
    }

    private void saveToLocalFile(List<Instance> list) {
        try {
            LocalStore.save(serviceName, list);
        } catch (Exception ex) {
            logger.error("Write service instancs to local cache file failed: {}", serviceName, ex);
        }
    }
    //-------------------------------------------------------------------------------
    private class CheckServiceAlive {}
    private void handleCheckServiceAlive(CheckServiceAlive msg) {
        removeUnregedSvc();
        instances.forEach((addr,it) -> {
            checkServiceAlive(it);
        });
        if (this.stateLogger != null && isLeader()) {
            logServiceState();
        }
    }
    private void removeUnregedSvc() {
        List<String> unregedList = new LinkedList<>();
        instances.forEach((addr,it) -> {
            if(it.isUnregistered()) {
                //移除已注销1天以上的服务
                long unregTime = System.currentTimeMillis() - it.getUnregisterTime();
                if (unregTime > UNREG_TIMEOUT) {
                    unregedList.add(addr);
                    storeDelServiceInstance(addr);
                }
            } else if (!it.isOnline()){
                //超过3天拨测失败则认为服务已注销
                long offlineTime = it.getOfflineTime();
                if (offlineTime > OFFLINE_TIMEOUT) {
                    unregedList.add(addr);
                    storeDelServiceInstance(addr);
                }
            }
        });
        unregedList.forEach(instances::remove);
    }

    private void storeDelServiceInstance(String addr) {
        try {
            logger.warn("Service UNREG : {}@{}", serviceName, addr);
            store.delServiceInstance(serviceName, addr);
        } catch (Exception ex) {
            logger.warn("Unregister a service instance failed: {}@{}", serviceName, addr, ex);
        }
    }

    private void checkServiceAlive(InstanceInfo instance) {
        logger.trace("Check servcie alive: {}@{} ",instance.name, instance.addr);
        ActorSelection service = context().actorSelection(instance.path);
        ActorRef self = self();
        Patterns.ask(service, ping, 5000).onComplete(new OnComplete<Object>() {
            @Override
            public void onComplete(Throwable failure, Object success) throws Throwable {
                boolean online = failure == null && success instanceof DSF.Pong;
                logger.trace("Check servcie alive complete: {}@{} {},{},{}",instance.name, instance.addr, online, failure, success);
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
            info.setOnline(msg.online);
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
        return UUID.randomUUID().toString();
    }

    //------------------------------------------------------------------------------------
    private void logServiceState() {
        instances.forEach((addr,info) -> {
            requestState(info);
        });
    }

    private void requestState(InstanceInfo info) {
        DSF.GetRequestCountHistory msg = DSF.GetRequestCountHistory.getDefaultInstance();
        ActorSelection service = context().actorSelection(info.path);
        Patterns.ask(service, msg, 3000)
            .mapTo(classTag(DSF.RequestCountHistory.class))
            .onComplete(new OnComplete<DSF.RequestCountHistory>() {
                public void onComplete(Throwable ex, DSF.RequestCountHistory his) {
                    if (ex == null && his != null) {
                        DSF.RequestCount c1 = his.getItems(0);
                        DSF.RequestCount c2 = his.getItems(1);
                        long request = c1.getRequestCount() - c2.getRequestCount();
                        long succeed = c1.getSucceedCount() - c2.getSucceedCount();
                        long responedTime = c1.getRespondTime() - c2.getRespondTime();
                        float tts = request == 0 ? 0 : responedTime*1.0f / request;
                        float failedRate = request == 0 ? 0 : (request - succeed)*1.0f / request;
                        DecimalFormat df = new DecimalFormat();
                        df.applyPattern("0.###");
                        StringBuilder sb = new StringBuilder();
                        sb.append("service,name=").append(info.name)
                            .append(",addr=").append(info.addr)
                            .append(" online=").append(info.isOnline()?1:0)
                            .append(",unreg=").append(info.isUnregistered()?1:0)
                            .append(",request=").append(request)
                            .append(",tts=").append(df.format(tts))
                            .append(",failed=").append(df.format(failedRate));
                        String line = sb.toString();
                        logger.debug("log service state: {}", line);
                        stateLogger.write(line);
                    } else {
                        StringBuilder sb = new StringBuilder();
                        sb.append("service,name=").append(info.name)
                            .append(",addr=").append(info.addr)
                            .append(" online=").append(info.isOnline()?1:0)
                            .append(",unreg=").append(info.isUnregistered()?1:0)
                            .append(",request=0")
                            .append(",tts=0")
                            .append(",failed=0");
                        String line = sb.toString();
                        logger.debug("log service state: {}", line);
                        stateLogger.write(line);
                    }
                }
            }, context().dispatcher());
    }

    private boolean isLeader() {
        Address selfAddr = cluster.selfAddress();
        ClusterEvent.CurrentClusterState state = cluster.state();
        return selfAddr.equals(state.getLeader());
    }
}
