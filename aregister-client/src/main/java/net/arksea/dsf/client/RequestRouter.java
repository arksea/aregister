package net.arksea.dsf.client;

import akka.actor.*;
import akka.dispatch.OnComplete;
import akka.dispatch.OnSuccess;
import akka.pattern.Patterns;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.arksea.dsf.DSF;
import net.arksea.dsf.client.route.IRouteStrategy;
import net.arksea.dsf.register.RegisterClient;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 *
 * Created by xiaohaixing on 2018/4/20.
 */
public class RequestRouter extends AbstractActor {
    private final Logger log = LogManager.getLogger(RequestRouter.class);
    private final String serviceName;
    //private final ActorSelection register;
    private final IRouteStrategy routeStrategy;
    private final Map<String,InstanceQuality> qualityMap = new HashMap<>();
    private final List<Instance> instances = new LinkedList<>();
    private final Map<String, RequestState> requests = new HashMap<>();
    private Cancellable saveStatDataTimer; //保存历史统计数据定时器
    private Cancellable checkOfflineTimer;
    private static final int SAVE_DELAY_SECONDS = 10; //保存统计历史数据的周期(s)
    private static final int CHECK_OFFLINE_SECONDS = 10; //测试OFFLINE服务是否存活
    private static final int REQUEST_TIMEOUT = 10000; //请求超时时间(ms)
    private final DSF.Ping ping;
    private RegisterClient registerClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private RequestRouter(String serviceName, RegisterClient registerClient, IRouteStrategy routeStrategy) {
        this.serviceName = serviceName;
        this.registerClient = registerClient;
        this.routeStrategy = routeStrategy;
        this.ping = DSF.Ping.getDefaultInstance();
    }

    @Override
    public void preStart() {
        log.debug("RequestRouter preStart: {}", serviceName);
        if (registerClient == null) {
            loadFromLocalCache();
        } else {
            try {
                DSF.GetSvcInstances get = DSF.GetSvcInstances.newBuilder()
                    .setName(serviceName)
                    .build();
                Future<DSF.SvcInstances> future = registerClient.getServiceList(serviceName, 5000);
                DSF.SvcInstances result = Await.result(future, Duration.create(5000, "ms"));
                handleSvcInstances(result);
                log.info("Load service list form register succeed", serviceName);
            } catch (Exception e) {
                log.warn("Load service list form register failed: {}", serviceName, e);
                loadFromLocalCache();
            }
            registerClient.subscribe(serviceName, self());
        }
        saveStatDataTimer = context().system().scheduler().schedule(
            Duration.create(SAVE_DELAY_SECONDS, TimeUnit.SECONDS),
            Duration.create(SAVE_DELAY_SECONDS,TimeUnit.SECONDS),
            self(),new SaveStatData(),context().dispatcher(),self());
        checkOfflineTimer = context().system().scheduler().schedule(
            Duration.create(CHECK_OFFLINE_SECONDS, TimeUnit.SECONDS),
            Duration.create(CHECK_OFFLINE_SECONDS,TimeUnit.SECONDS),
            self(),new CheckOfflineService(),context().dispatcher(),self());
    }

    private void loadFromLocalCache() {
        try {
            String fileName = "./config/" + serviceName + ".svc";
            File file = new File(fileName);
            DSF.SvcInstances.Builder builder = DSF.SvcInstances.newBuilder()
                .setName(serviceName)
                .setSerialId("");
            List<String> lines = Files.readAllLines(file.toPath());
            for (String line: lines){
                if (StringUtils.isNotBlank(line)) {
                    net.arksea.dsf.store.Instance i = objectMapper.readValue(line, net.arksea.dsf.store.Instance.class);
                    builder.addInstances(
                        DSF.Instance.newBuilder()
                            .setAddr(i.getAddr())
                            .setPath(i.getPath())
                            .setOnline(true)
                            .build());
                }
            }
            handleSvcInstances(builder.build());
            log.info("Load service list form local cache file succeed",serviceName);
        } catch (Exception ex) {
            log.warn("Load service list form local cache file failed: {}",serviceName,ex);
        }
    }
    @Override
    public void postStop() {
        log.debug("RequestRouter postStop: {}", serviceName);
        if (registerClient != null) {
            registerClient.unsubscribe(serviceName, self());
        }
        if (saveStatDataTimer != null) {
            saveStatDataTimer.cancel();
            saveStatDataTimer = null;
        }
        if (checkOfflineTimer != null) {
            checkOfflineTimer.cancel();
            checkOfflineTimer = null;
        }
    }

    //优先从注册服务器获取服务列表
    public static Props props(String serviceName, RegisterClient registerClient, IRouteStrategy routeStrategy) {
        return Props.create(RequestRouter.class, () -> new RequestRouter(serviceName,registerClient,routeStrategy));
    }

    //只从本地配置文件获取服务列表
    public static Props props(String serviceName, IRouteStrategy routeStrategy) {
        return Props.create(RequestRouter.class, () -> new RequestRouter(serviceName,null,routeStrategy));
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(DSF.ServiceRequest.class,    this::handleServiceRequest)
            .match(DSF.ServiceResponse.class,   this::handleServiceResponse)
            .match(DSF.RegService.class,    this::handleRegService)
            .match(DSF.UnregService.class,  this::handleUnregService)
            .match(SaveStatData.class,      this::handleSaveStatData)
            .match(DSF.SvcInstances.class,  this::handleSvcInstances)
            .match(CheckOfflineService.class, this::handleCheckOfflineService)
            .match(ServiceAlive.class,      this::handleServiceAlive)
            .build();
    }

    //------------------------------------------------------------------------------------
    private void handleServiceRequest(DSF.ServiceRequest msg) {
        log.trace("handleServiceRequest({},{})", msg.getTypeName(), msg.getRequestId());
        long startTime = System.currentTimeMillis();
        Optional<Instance> op = routeStrategy.getInstance(instances);
        final ActorRef requester = sender();
        if (op.isPresent()) {
            Instance instance = op.get();
            InstanceQuality q = qualityMap.get(instance.addr);
            q.request(msg.getOneway());
            log.trace("service instance: {}", instance.path);
            ActorSelection service = context().actorSelection(instance.path);
            service.tell(msg,self());
            if (!msg.getOneway()) {
                RequestState state = new RequestState(requester, startTime, msg, instance);
                requests.put(msg.getRequestId(), state);
            }
        } else {
            requester.tell(new NoUseableService(), self());
        }
    }
    //------------------------------------------------------------------------------------
    private void handleServiceResponse(DSF.ServiceResponse msg) {
        log.trace("handleServiceResponse({},{})", msg.getTypeName(), msg.getRequestId());
        RequestState state = requests.remove(msg.getRequestId());
        if (state == null) {
            log.warn("not fond the request state : {}", msg.getRequestId());
        } else {
            state.requester.forward(msg, context());
            InstanceQuality q = qualityMap.get(state.instance.addr);
            long time = System.currentTimeMillis() - state.startTime;
            if (time > REQUEST_TIMEOUT) {
                q.timeout(time);
            } else {
                q.respond(time);
            }
        }
    }
    //-------------------------------------------------------------------------------
    private void handleRegService(DSF.RegService msg) {
        log.info("Service REG : {}@{}",msg.getName(),msg.getAddr());
        //当收到服务实例的注册事件时，如果他在本地被标记为OFFLINE状态，则将其切换到UP状态
        //这样他将会有机会使用部分流量进行测试，直到被切换到ONLINE或者OFFLINE状态
        InstanceQuality q = qualityMap.get(msg.getAddr());
        if (q == null) {
            Instance instance =  new Instance(serviceName, msg.getAddr(), msg.getPath());
            this.instances.add(instance);
            q = new InstanceQuality(msg.getAddr());
            qualityMap.put(msg.getAddr(), q);
            instance.status = InstanceStatus.UP;
            log.info("Service UP : {}@{}", instance.name, instance.addr);
        } else {
            for (Instance i : instances) {
                if (i.addr.equals(msg.getAddr())) {
                    if (i.status == InstanceStatus.OFFLINE) {
                        i.status = InstanceStatus.UP;
                        log.info("Service UP : {}@{}", i.name, i.addr);
                    }
                    break;
                }
            }
        }
    }
    //-------------------------------------------------------------------------------
    private void handleUnregService(DSF.UnregService msg) {
        log.info("Service UNREG : {}@{}",msg.getName(),msg.getAddr());
        if (qualityMap.remove(msg.getAddr()) != null ) {
            Instance instance =  new Instance(serviceName, msg.getAddr(), null);
            this.instances.remove(instance);
        }
    }
    //------------------------------------------------------------------------------------
    private void handleSvcInstances(DSF.SvcInstances msg) {
        log.trace("handleSvcInstances(), instanceCount={}",msg.getInstancesCount());
        Map<String,Instance> oldMap = new HashMap<>();
        this.instances.forEach(it -> oldMap.put(it.addr, it));
        this.instances.clear();
        msg.getInstancesList().forEach(it -> {
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
            qualityMap.computeIfAbsent(it.getAddr(), k -> new InstanceQuality(it.getAddr()));
        });
        if (oldMap.size() > 0) {
            for (String addr : oldMap.keySet()) {
                qualityMap.remove(addr);
                log.info("Service DEL : {}@{}", serviceName, addr);
            }
            oldMap.clear();
        }
    }
    //------------------------------------------------------------------------------------
    private static class SaveStatData {}
    private void handleSaveStatData(SaveStatData msg) {
        long now = System.currentTimeMillis();
        List<String> timeoutRequests = new LinkedList<>();
        for (Map.Entry<String, RequestState> e: requests.entrySet()) {
            if (now - e.getValue().startTime > REQUEST_TIMEOUT) {
                timeoutRequests.add(e.getKey());
                Instance i = e.getValue().instance;
                InstanceQuality q = qualityMap.get(i.addr);
                q.timeout(REQUEST_TIMEOUT);
            }
        }
        timeoutRequests.forEach(requests::remove);
        this.instances.forEach(it -> {
            InstanceQuality q = qualityMap.get(it.addr);
            q.saveHistory();
        });
        updateInstanceStatus();
    }
    //------------------------------------------------------------------------------------
    private void updateInstanceStatus() {
        this.instances.forEach(this::updateInstanceStatus);
    }

    private void updateInstanceStatus(Instance it) {
        InstanceQuality q = qualityMap.get(it.addr);
        float timeoutRate1M = q.getTimeoutRate(1);
        if (it.status == InstanceStatus.ONLINE){
            if (timeoutRate1M > 0.5f) {
                it.status = InstanceStatus.OFFLINE;
                log.error("Service OFFLINE : {}@{}", it.name, it.addr);
            }
        } else if (it.status == InstanceStatus.UP) {
            if (timeoutRate1M > 0.5f) {
                it.status = InstanceStatus.OFFLINE;
                log.error("Service OFFLINE : {}@{}", it.name, it.addr);
            } else if (q.getRequestCount(1)>0 && timeoutRate1M < 0.2f) {
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
    private void checkServiceAlive(Instance instance) {
        log.trace("Check offline servcie: {}@{} ",instance.name, instance.addr);
        ActorSelection service = context().actorSelection(instance.path);
        ActorRef self = self();
        Patterns.ask(service, ping, 5000).onComplete(new OnComplete<Object>() {
            @Override
            public void onComplete(Throwable failure, Object success) throws Throwable {
                if (failure == null && success instanceof DSF.Pong) {
                    self.tell(new ServiceAlive(instance.addr, InstanceStatus.UP), ActorRef.noSender());
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
                    i.status = msg.status;
                    log.info("Service {} : {}@{}", msg.status, i.name, i.addr);
                }
                break;
            }
        }
    }
}
