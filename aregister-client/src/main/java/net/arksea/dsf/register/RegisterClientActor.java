package net.arksea.dsf.register;

import akka.actor.*;
import akka.dispatch.OnComplete;
import akka.japi.Creator;
import akka.pattern.Patterns;
import net.arksea.dsf.DSF;
import net.arksea.dsf.client.DefaultSwitchCondition;
import net.arksea.dsf.client.IInstanceSource;
import net.arksea.dsf.client.Instance;
import net.arksea.dsf.client.RequestRouter;
import net.arksea.dsf.client.route.HotStandby;
import net.arksea.dsf.store.LocalStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scala.concurrent.duration.Duration;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static akka.japi.Util.classTag;

/**
 *
 * Created by xiaohaixing on 2018/4/23.
 */
public class RegisterClientActor extends RequestRouter {
    public static final String ACTOR_NAME = "registerClient";
    private static final Logger log = LogManager.getLogger(RegisterClientActor.class);
    private Map<String, ServiceInfo> serviceInfoMap = new HashMap<>();
    private long timeout = 10000;
    private final static int MIN_RETRY_DELAY = 2;
    private final static int MAX_RETRY_DELAY = 300;
    private long backoff = MIN_RETRY_DELAY;
    private final String clientName;
    private Cancellable updateTimer;
    private static final int UPDATE_DELAY_SECONDS = 60;

    public RegisterClientActor(String clientName, IInstanceSource instanceSource) {
        super("dsfRegister",
            instanceSource,
            new HotStandby(),
            new DefaultSwitchCondition(),
            true);
        this.clientName = clientName;
    }

    public static Props props(String clientName, IInstanceSource instanceSource) {
        return Props.create(new Creator<RegisterClientActor>() {
            @Override
            public RegisterClientActor create() throws Exception {
                return new RegisterClientActor(clientName, instanceSource);
            }
        });
    }

    @Override
    public void preStart() {
        log.info("RegisterClientActor preStart");
        super.preStart();
        updateTimer = context().system().scheduler().schedule(
            Duration.create(UPDATE_DELAY_SECONDS, TimeUnit.SECONDS),
            Duration.create(UPDATE_DELAY_SECONDS,TimeUnit.SECONDS),
            self(),new UpdateSubscribe(),context().dispatcher(),self());
    }

    @Override
    public void postStop() {
        super.postStop();
        if (updateTimer != null) {
            updateTimer.cancel();
            updateTimer = null;
        }
        log.info("RegisterClientActor postStop");
    }

    @Override
    public Receive createReceive() {
        return createReceiveBuilder()
            .match(DSF.GetSvcInstances.class, this::handleGetSvcInstances)
            .match(DSF.SvcInstances.class,    this::handleSvcInstances)
            .match(DSF.SubService.class,      this::handleSubService)
            .match(DSF.UnsubService.class,    this::handleUnsubService)
            .match(RegLocalService.class,     this::handleRegLocalService)
            .match(UnregLocalService.class,   this::handleUnregLocalService)
            .match(DSF.RegService.class,      this::handleRegService)
            .match(DSF.UnregService.class,    this::handleUnregService)
            .match(UpdateSubscribe.class,     this::handleUpdateSubscribe)
            .match(RegisterRequestSucceed.class,  this::handleRegisterRequestSucceed)
            .match(RegisterRequestFailed.class,   this::handleRegisterRequestFailed)
            .build();
    }
    //-------------------------------------------------------------------------------------------------
    private void tellRegister(Object message, ActorRef sender) {
        Optional<Instance> op = getInstance();
        if (op.isPresent()) {
            Instance i = op.get();
            ActorSelection register = context().actorSelection(i.path);
            register.tell(message, sender);
        }
    }
    private class UpdateSubscribe {}
    private void handleUpdateSubscribe(UpdateSubscribe msg) {
        log.trace("RegisterClientActor.handleUpdateSubscribe, serviceInfoMap.size={}", serviceInfoMap.size());
        serviceInfoMap.forEach((name, info) -> {
            DSF.SyncSvcInstances sync = DSF.SyncSvcInstances.newBuilder()
                .setName(name)
                .setSerialId(info.serialId)
                .setSubscriber(clientName)
                .build();
            tellRegister(sync, self());
        });
    }
    //-------------------------------------------------------------------------------------------------
    private void handleGetSvcInstances(DSF.GetSvcInstances msg) {
        log.trace("getSvcInstances: {}",msg.getName());
        tellRegister(msg, sender());
    }
    //------------------------------------------------------------------------------------
    private void handleSvcInstances(DSF.SvcInstances msg) {
        log.trace("RegisterClientActor.handleSvcInstances: {}",msg.getName());
        ServiceInfo info = serviceInfoMap.computeIfAbsent(msg.getName(), k -> new ServiceInfo());
        if (!info.serialId.equals(msg.getSerialId())) {
            info.serialId = msg.getSerialId();
            List<DSF.Instance> instances = msg.getInstancesList();
            info.instances.clear();
            instances.forEach(i -> info.instances.put(i.getAddr(),
                    new net.arksea.dsf.store.Instance(i.getAddr(), i.getPath()))
            );
            info.clientSet.forEach(c -> c.tell(msg, self()));
            try {
                LocalStore.save(msg.getName(), info.instances.values());
            } catch (IOException ex) {
                log.error("Write service instancs to local cache file failed: {}", msg.getName(), ex);
            }
        }
    }
    //-------------------------------------------------------------------------------------------------
    private void handleSubService(DSF.SubService msg) {
        log.info("Subscribe Service : {}", msg.getService());
        ServiceInfo info = serviceInfoMap.computeIfAbsent(msg.getService(), k -> new ServiceInfo());
        info.clientSet.add(sender());
        tellRegister(msg, self());
    }
    //-------------------------------------------------------------------------------------------------
    private void handleUnsubService(DSF.UnsubService msg) {
        log.info("Unsubscribe Service : {}", msg.getService());
        ServiceInfo info = serviceInfoMap.computeIfAbsent(msg.getService(), k -> new ServiceInfo());
        info.clientSet.remove(sender());
        if (info.clientSet.isEmpty()) {
            tellRegister(msg, self());
        }
    }
    //-------------------------------------------------------------------------------------------------
    //向注册服务器发起注册请求，并重试直到成功
    private void handleRegLocalService(RegLocalService msg) {
        log.trace("RegisterClientActor.handleRegLocalService({})", msg.addr);
        DSF.RegService dsfmsg = DSF.RegService.newBuilder()
            .setName(msg.name)
            .setAddr(msg.addr)
            .setPath(msg.path)
            .build();
        Optional<Instance> op = getInstance();
        if (op.isPresent()) {
            Instance i = op.get();
            ActorSelection register = context().actorSelection(i.path);
            Patterns.ask(register, dsfmsg, timeout).mapTo(classTag(Boolean.class)).onComplete(
                new TryUntilSucceed(self(), msg, backoff,
                    () -> "Register service success: "+msg.name+"@"+msg.addr,
                    () -> "Register service failed: "+msg.name+"@"+msg.addr)
                , context().dispatcher());
        } else {
            log.trace("no usable register");
            context().system().scheduler().scheduleOnce(
                Duration.create(backoff, TimeUnit.SECONDS),self(),dsfmsg,context().dispatcher(),self()
            );
        }
    }
    //将集群中广播的注册事件分发给所有本地订阅者
    private void handleRegService(DSF.RegService msg) {
        log.trace("RegisterClientActor.handleRegService({})", msg.getAddr());
        ServiceInfo info = serviceInfoMap.computeIfAbsent(msg.getName(), k -> new ServiceInfo());
        info.instances.computeIfAbsent(msg.getAddr(), addr ->
            new net.arksea.dsf.store.Instance(addr,msg.getPath()));
        info.clientSet.forEach(c -> c.tell(msg, self()));
    }
    //-------------------------------------------------------------------------------------------------
    private void handleUnregLocalService(UnregLocalService msg) {
        log.trace("RegisterClientActor.handleUnregLocalService({})", msg.addr);
        DSF.UnregService dsfmsg = DSF.UnregService.newBuilder()
            .setName(msg.name)
            .setAddr(msg.addr)
            .build();
        Optional<Instance> op = getInstance();
        if (op.isPresent()) {
            Instance i = op.get();
            ActorSelection register = context().actorSelection(i.path);
            Patterns.ask(register, dsfmsg, timeout).mapTo(classTag(Boolean.class)).onComplete(
                new TryUntilSucceed(self(), msg, backoff,
                    () -> "Unregister service success: " + msg.name + "@" + msg.addr,
                    () -> "Unregister service failed: " + msg.name + "@" + msg.addr)
                , context().dispatcher());
        } else {
            context().system().scheduler().scheduleOnce(
                Duration.create(backoff, TimeUnit.SECONDS),self(),dsfmsg,context().dispatcher(),self()
            );
        }
    }
    //将集群中广播的注销事件分发给所有订阅者
    private void handleUnregService(DSF.UnregService msg) {
        log.trace("RegisterClientActor.handleUnregService({})", msg.getAddr());
        ServiceInfo info = serviceInfoMap.computeIfAbsent(msg.getName(), k -> new ServiceInfo());
        info.instances.remove(msg.getAddr());
        info.clientSet.forEach(c -> c.tell(msg, self()));
    }
    //-------------------------------------------------------------------------------------------------
    class ServiceInfo {
        String serialId = ""; //注册服务下发的实例集合的序列号，注册服务用此序列号判断订阅者是否需要更新实例列表
        final Set<ActorRef> clientSet = new HashSet<>();
        final Map<String, net.arksea.dsf.store.Instance> instances = new HashMap<>();
    }

    class RegisterRequestSucceed {}
    private void handleRegisterRequestSucceed(RegisterRequestSucceed msg) {
        this.backoff = MIN_RETRY_DELAY;
    }

    class RegisterRequestFailed {}
    private void handleRegisterRequestFailed(RegisterRequestFailed msg) {
        this.backoff = Math.min(MAX_RETRY_DELAY, backoff*2);
    }
    //-------------------------------------------------------------------------------------------------
    public class TryUntilSucceed extends OnComplete<Boolean> {
        private ActorRef registerClient;
        private Object message;
        private Callable<String> succeedLogInfo;
        private Callable<String> failedLogInfo;
        private final long failedDelay;
        public TryUntilSucceed(ActorRef registerClient,
                               Object msg, long failedDelay,
                               Callable<String> succeedLogInfo, Callable<String> failedLogInfo) {
            this.registerClient = registerClient;
            this.message = msg;
            this.succeedLogInfo = succeedLogInfo;
            this.failedLogInfo = failedLogInfo;
            this.failedDelay = failedDelay;
        }

        @Override
        public void onComplete(Throwable failure, Boolean success) throws Throwable {
            if (failure == null) {
                if (success) {
                    log.info(succeedLogInfo.call());
                    registerClient.tell(new RegisterRequestSucceed(), ActorRef.noSender());
                    return;
                } else {
                    if (failedDelay >= MAX_RETRY_DELAY) {
                        log.error(failedLogInfo.call());
                    } else {
                        log.warn(failedLogInfo.call());
                    }
                }
            } else {
                if (failedDelay >= MAX_RETRY_DELAY) {
                    log.error(failedLogInfo.call());
                } else {
                    log.warn(failedLogInfo.call());
                }
            }
            registerClient.tell(new RegisterRequestFailed(), ActorRef.noSender());
            context().system().scheduler().scheduleOnce(
                Duration.create(failedDelay, TimeUnit.SECONDS),self(),message,context().dispatcher(),self()
            );
        }
    }
}
