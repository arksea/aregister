package net.arksea.dsf.client;

import akka.actor.*;
import akka.dispatch.OnComplete;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;
import net.arksea.dsf.DSF;
import net.arksea.dsf.client.route.IRouteStrategy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scala.concurrent.duration.Duration;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 *
 * Created by xiaohaixing on 2018/4/20.
 */
public class RequestRouter extends AbstractActor {
    private static final Logger log = LogManager.getLogger(RequestRouter.class);
    protected final String serviceName;
    private final ISwitchCondition switchCondition;
    private final Map<String,InstanceQuality> qualityMap = new HashMap<>();
    private final List<Instance> instances = new LinkedList<>();
    private Cancellable saveStatDataTimer; //保存历史统计数据定时器
    private Cancellable checkOfflineTimer;
    private static final int CHECK_OFFLINE_SECONDS = 5; //测试OFFLINE服务是否存活
    private final DSF.Ping ping;
    private IInstanceSource instanceSource;
    private final IRouteStrategy routeStrategy;

    protected RequestRouter(String serviceName, IInstanceSource instanceSource, IRouteStrategy routeStrategy, ISwitchCondition condition) {
        this.serviceName = serviceName;
        this.instanceSource = instanceSource;
        this.ping = DSF.Ping.getDefaultInstance();
        this.routeStrategy = routeStrategy;
        this.switchCondition = condition;
    }

    public static Props props(String serviceName, IInstanceSource instanceSource, IRouteStrategy routeStrategy, ISwitchCondition condition) {
        return Props.create(RequestRouter.class, () -> new RequestRouter(serviceName,instanceSource, routeStrategy, condition));
    }

    @Override
    public void preStart() {
        log.debug("RequestRouter preStart: {}", serviceName);
        saveStatDataTimer = context().system().scheduler().schedule(
            Duration.create(switchCondition.statPeriod(),TimeUnit.SECONDS),
            Duration.create(switchCondition.statPeriod(),TimeUnit.SECONDS),
            self(),new SaveStatData(),context().dispatcher(),self());
        checkOfflineTimer = context().system().scheduler().schedule(
            Duration.create(CHECK_OFFLINE_SECONDS, TimeUnit.SECONDS),
            Duration.create(CHECK_OFFLINE_SECONDS,TimeUnit.SECONDS),
            self(),new CheckOfflineService(),context().dispatcher(),self());
        try {
            initInstances(instanceSource.getSvcInstances());
        } catch (Exception ex) {
            log.warn("Load service list failed: {}",serviceName,ex);
        }
    }

    @Override
    public void postStop() {
        log.debug("RequestRouter postStop: {}", serviceName);
        if (saveStatDataTimer != null) {
            saveStatDataTimer.cancel();
            saveStatDataTimer = null;
        }
        if (checkOfflineTimer != null) {
            checkOfflineTimer.cancel();
            checkOfflineTimer = null;
        }
    }

    protected void initInstances(DSF.SvcInstances msg) {
        log.trace("initInstances(), instanceCount={}",msg.getInstancesCount());
        Map<String,Instance> oldMap = new HashMap<>();
        this.instances.forEach(it -> oldMap.put(it.addr, it));
        this.instances.clear();
        msg.getInstancesList().forEach(it -> {
            qualityMap.computeIfAbsent(it.getAddr(), k -> new InstanceQuality(it.getAddr()));
            Instance old = oldMap.remove(it.getAddr());
            if (old == null) {
                InstanceStatus status = it.getOnline() ? InstanceStatus.ONLINE : InstanceStatus.OFFLINE;
                Instance instance =  new Instance(serviceName, it.getAddr(), it.getPath(), status);
                this.instances.add(instance);
                checkServiceAlive(instance);
                log.info("Service ADD : {}@{}, status={}", serviceName, it.getAddr(), status);
            } else {
                //因为服务的本地Online状态只采信质量统计或者心跳拨测的结果，
                //所以此处不使用Register获取到的值进行更新
                this.instances.add(old);
            }
        });
        if (oldMap.size() > 0) {
            for (String addr : oldMap.keySet()) {
                qualityMap.remove(addr);
                log.info("Service DEL : {}@{}", serviceName, addr);
            }
            oldMap.clear();
        }
    }

    @Override
    public Receive createReceive() {
        return createReceiveBuilder().build();
    }

    protected ReceiveBuilder createReceiveBuilder() {
        return receiveBuilder()

            .match(SaveStatData.class,       this::handleSaveStatData)
            .match(CheckOfflineService.class,this::handleCheckOfflineService)
            .match(ServiceAlive.class,       this::handleServiceAlive)
            .match(Ready.class,              this::handleReady);
    }

    //-------------------------------------------------------------------------------
    private static class SaveStatData {}
    private void handleSaveStatData(SaveStatData msg) {
        checkTimeoutRequest();
        this.instances.forEach(it -> {
            InstanceQuality q = qualityMap.get(it.addr);
            q.saveHistory();
        });
        updateInstanceStatus();
    }

    protected void checkTimeoutRequest() {
    }

    protected void onRequest(Instance i) {
        qualityMap.get(i.addr).request();
    }
    protected void onRequestSucceed(Instance i, long timeMills) {
        qualityMap.get(i.addr).succeed(timeMills);
    }
    protected void onRequestTimeout(Instance i, long timeMills) {
        qualityMap.get(i.addr).timeout(timeMills);
    }
    protected void onAddInstance(Instance i) {
        InstanceQuality q = qualityMap.get(i.addr);
        if (q == null) {
            this.instances.add(i);
            q = new InstanceQuality(i.addr);
            qualityMap.put(i.addr, q);
            i.status = InstanceStatus.OFFLINE;
        }
    }
    protected long getReuqestTimeout() {
        return switchCondition.requestTimeout();
    }
    protected void onDelInstance(Instance i) {
        this.qualityMap.remove(i.addr);
        this.instances.remove(i);
    }
    protected Optional<Instance> getInstance() {
        return this.routeStrategy.getInstance(instances);
    }
    //------------------------------------------------------------------------------------
    private void updateInstanceStatus() {
        this.instances.forEach(this::updateInstanceStatus);
    }

    private void updateInstanceStatus(Instance it) {
        InstanceQuality q = qualityMap.get(it.addr);
        if (it.status == InstanceStatus.ONLINE){
            if (switchCondition.onlineToOffline(q)) {
                it.status = InstanceStatus.OFFLINE;
                log.error("Service OFFLINE : {}@{}", it.name, it.addr);
            }
        } else if (it.status == InstanceStatus.UP) {
            if (switchCondition.upToOffline(q)) {
                it.status = InstanceStatus.OFFLINE;
                log.error("Service OFFLINE : {}@{}", it.name, it.addr);
            } else if (switchCondition.upToOnline(q)) {
                it.status = InstanceStatus.ONLINE;
                log.info("Service ONLINE : {}@{}", it.name, it.addr);
            }
        }
    }
    //------------------------------------------------------------------------------------
    private class CheckOfflineService {}
    private void handleCheckOfflineService(CheckOfflineService msg) {
        this.instances.forEach(it -> {
            if (it.status == InstanceStatus.OFFLINE) {
                checkServiceAlive(it);
            }
        });
    }
    protected void checkServiceAlive(Instance instance) {
        log.trace("Check offline servcie: {}@{} ",instance.name, instance.addr);
        ActorSelection service = context().actorSelection(instance.path);
        ActorRef self = self();
        InstanceQuality q = qualityMap.get(instance.addr);
        long start = System.currentTimeMillis();
        q.request();
        long PING_TIMEOUT = 3000;
        Patterns.ask(service, ping, PING_TIMEOUT).onComplete(new OnComplete<Object>() {
            @Override
            public void onComplete(Throwable failure, Object success) throws Throwable {
                long time = System.currentTimeMillis() - start;
                if (failure == null && success instanceof DSF.Pong) {
                    q.succeed(time);
                    self.tell(new ServiceAlive(instance.addr, InstanceStatus.UP), ActorRef.noSender());
                } else if (time > PING_TIMEOUT){
                    q.timeout(PING_TIMEOUT);
                }
            }
        }, context().dispatcher());
    }
    //------------------------------------------------------------------------------------
    class ServiceAlive {
        final String addr;
        final InstanceStatus status;
        ServiceAlive(String addr, InstanceStatus status) {
            this.addr = addr;
            this.status = status;
        }
    }
    private void handleServiceAlive(ServiceAlive msg) {
        for (Instance i : instances) {
            if (i.addr.equals(msg.addr)) {
                if (i.status == InstanceStatus.OFFLINE) {
                    InstanceQuality q = qualityMap.get(msg.addr);
                    if (switchCondition.offlineToUp(q)) {
                        i.status = msg.status;
                        log.info("Service {} : {}@{}", msg.status, i.name, i.addr);
                    }
                }
                break;
            }
        }
    }
    //
    public static class Ready {}
    private void handleReady(Ready msg) {
        sender().tell(true, self());
    }
}
