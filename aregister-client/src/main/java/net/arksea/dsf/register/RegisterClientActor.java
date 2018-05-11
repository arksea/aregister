package net.arksea.dsf.register;

import akka.actor.*;
import akka.dispatch.OnComplete;
import akka.japi.Creator;
import akka.pattern.Patterns;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.arksea.dsf.DSF;
import net.arksea.dsf.store.Instance;
import net.arksea.dsf.store.LocalStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scala.concurrent.duration.Duration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static akka.japi.Util.classTag;

/**
 *
 * Created by xiaohaixing on 2018/4/23.
 */
public class RegisterClientActor extends AbstractActor {
    public static final String ACTOR_NAME = "registerClient";
    private static final Logger log = LogManager.getLogger(RegisterClientActor.class);
    private ActorSelection register;
    private Map<String, ServiceInfo> serviceInfoMap = new HashMap<>();
    private long timeout = 10000;
    private final static int MIN_RETRY_DELAY = 2000;
    private final static int MAX_RETRY_DELAY = 300000;
    private long backoff = MIN_RETRY_DELAY;
    private final String clientName;
    private Cancellable updateTimer;
    private static final int UPDATE_DELAY_SECONDS = 60;


    public RegisterClientActor(String clientName, String serverAddr) {
        this.clientName = clientName;
        String path = "akka.tcp://DsfCluster@"+serverAddr+"/user/dsfRegister";
        register = context().actorSelection(path);
    }

    public static Props props(String clientName, String serverAddr) {
        return Props.create(new Creator<RegisterClientActor>() {
            @Override
            public RegisterClientActor create() throws Exception {
                return new RegisterClientActor(clientName, serverAddr);
            }
        });
    }

    @Override
    public void preStart() {
        log.info("RegisterClientActor preStart");
        updateTimer = context().system().scheduler().schedule(
            Duration.create(UPDATE_DELAY_SECONDS, TimeUnit.SECONDS),
            Duration.create(UPDATE_DELAY_SECONDS,TimeUnit.SECONDS),
            self(),new UpdateSubscribe(),context().dispatcher(),self());
    }

    @Override
    public void postStop() {
        if (updateTimer != null) {
            updateTimer.cancel();
            updateTimer = null;
        }
        log.info("RegisterClientActor postStop");
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(DSF.GetSvcInstances.class, this::handleGetSvcInstances)
            .match(DSF.SvcInstances.class,    this::handleSvcInstances)
            .match(DSF.SubService.class,      this::handleSubService)
            .match(DSF.UnsubService.class,    this::handleUnsubService)
            .match(RegLocalService.class,     this::handleRegLocalService)
            .match(UnregLocalService.class,   this::handleUnregLocalService)
            .match(DSF.RegService.class,      this::handleRegService)
            .match(DSF.UnregService.class,    this::handleUnregService)
            .match(UpdateSubscribe.class,     this::handleUpdateSubscribe)
            .build();
    }
    //-------------------------------------------------------------------------------------------------
    private class UpdateSubscribe {}
    private void handleUpdateSubscribe(UpdateSubscribe msg) {
        serviceInfoMap.forEach((name, info) -> {
            DSF.SyncSvcInstances sync = DSF.SyncSvcInstances.newBuilder()
                .setName(name)
                .setSerialId(info.serialId)
                .setSubscriber(clientName)
                .build();
            register.tell(sync, self());
        });
    }
    //-------------------------------------------------------------------------------------------------
    private void handleGetSvcInstances(DSF.GetSvcInstances msg) {
        log.info("getSvcInstances: {}",msg.getName());
        register.forward(msg, context());
    }
    //------------------------------------------------------------------------------------
    private void handleSvcInstances(DSF.SvcInstances msg) {
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
        register.tell(msg, self());
    }
    //-------------------------------------------------------------------------------------------------
    private void handleUnsubService(DSF.UnsubService msg) {
        log.info("Unsubscribe Service : {}", msg.getService());
        ServiceInfo info = serviceInfoMap.computeIfAbsent(msg.getService(), k -> new ServiceInfo());
        info.clientSet.remove(sender());
        if (info.clientSet.isEmpty()) {
            register.tell(msg, self());
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
        Patterns.ask(register, dsfmsg, timeout).mapTo(classTag(Boolean.class)).onComplete(
            new OnComplete<Boolean>() {
                @Override
                public void onComplete(Throwable failure, Boolean success) throws Throwable {
                    if (failure == null) {
                        if (success) {
                            log.info("Register service success: {}@{}", msg.name, msg.addr);
                            resetBackoffDelay();
                            return;
                        } else {
                            if (backoff >= MAX_RETRY_DELAY) {
                                log.error("Register service failed: {}@{}", msg.name, msg.addr);
                            } else {
                                log.warn("Register service failed: {}@{}", msg.name, msg.addr);
                            }
                        }
                    } else {
                        if (backoff >= MAX_RETRY_DELAY) {
                            log.error("Register service failed: {}@{}", msg.name, msg.addr, failure);
                        } else {
                            log.warn("Register service failed: {}@{}", msg.name, msg.addr, failure);
                        }
                    }
                    context().system().scheduler().scheduleOnce(
                        Duration.create(getBackoffDelay(), TimeUnit.SECONDS),self(),dsfmsg,context().dispatcher(),self()
                    );
                }
            }, context().dispatcher());
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
        final ActorRef sender = sender();
        DSF.UnregService dsfmsg = DSF.UnregService.newBuilder()
            .setName(msg.name)
            .setAddr(msg.addr)
            .build();
        Patterns.ask(register, dsfmsg, timeout).mapTo(classTag(Boolean.class)).onComplete(
            new OnComplete<Boolean>() {
                @Override
                public void onComplete(Throwable failure, Boolean success) throws Throwable {
                    if (failure == null) {
                        if (success) {
                            sender.tell(true, ActorRef.noSender());
                            log.info("Unregister service success: {}@{}", msg.name, msg.addr);
                            resetBackoffDelay();
                            return;
                        } else {
                            if (backoff >= MAX_RETRY_DELAY) {
                                log.error("Unregister service failed: {}@{}", msg.name, msg.addr);
                            } else {
                                log.warn("Unregister service failed: {}@{}", msg.name, msg.addr);
                            }
                        }
                    } else {
                        if (backoff >= MAX_RETRY_DELAY) {
                            log.error("Unregister service failed: {}@{}", msg.name, msg.addr, failure);
                        } else {
                            log.warn("Unregister service failed: {}@{}", msg.name, msg.addr, failure);
                        }
                    }
                    context().system().scheduler().scheduleOnce(
                        Duration.create(getBackoffDelay(), TimeUnit.SECONDS),self(),dsfmsg,context().dispatcher(),self()
                    );
                }
            }, context().dispatcher());
    }
    //将集群中广播的注销事件分发给所有订阅者
    private void handleUnregService(DSF.UnregService msg) {
        log.trace("RegisterClientActor.handleUnregService({})", msg.getAddr());
        ServiceInfo info = serviceInfoMap.computeIfAbsent(msg.getName(), k -> new ServiceInfo());
        info.instances.remove(msg.getAddr());
        info.clientSet.forEach(c -> c.tell(msg, self()));
    }
    //-------------------------------------------------------------------------------------------------
    private long getBackoffDelay() {
        this.backoff = Math.min(MAX_RETRY_DELAY, backoff*2);
        return backoff;
    }

    private void resetBackoffDelay() {
        this.backoff = MIN_RETRY_DELAY;
    }

    class ServiceInfo {
        String serialId = ""; //注册服务下发的实例集合的序列号，注册服务用此序列号判断订阅者是否需要更新实例列表
        final Set<ActorRef> clientSet = new HashSet<>();
        final Map<String, net.arksea.dsf.store.Instance> instances = new HashMap<>();
    }
}
