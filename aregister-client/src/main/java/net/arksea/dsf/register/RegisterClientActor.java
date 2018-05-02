package net.arksea.dsf.register;

import akka.actor.*;
import akka.dispatch.OnComplete;
import akka.japi.Creator;
import akka.pattern.Patterns;
import net.arksea.dsf.DSF;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scala.concurrent.duration.Duration;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static akka.japi.Util.classTag;

/**
 *
 * Created by xiaohaixing on 2018/4/23.
 */
public class RegisterClientActor extends AbstractActor {
    public static final String ACTOR_NAME = "registerClient";
    private final Logger log = LogManager.getLogger(RegisterClientActor.class);
    private ActorSelection register;
    private Map<String, Set<ActorRef>> subscriberMap = new HashMap<>();
    private long timeout = 10000;
    private final static int MIN_RETRY_DELAY = 10000;
    private final static int MAX_RETRY_DELAY = 300000;
    private long backoff = MIN_RETRY_DELAY;
    private final String clientName;
    private Cancellable updateSubTimer;
    private static final int SUBSCRIBE_DELAY_SECONDS = 300;

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
        log.info("ServiceRegisterActor preStart");
        updateSubTimer = context().system().scheduler().schedule(
            Duration.create(SUBSCRIBE_DELAY_SECONDS, TimeUnit.SECONDS),
            Duration.create(SUBSCRIBE_DELAY_SECONDS,TimeUnit.SECONDS),
            self(),new UpdateSubscribe(),context().dispatcher(),self());
    }

    @Override
    public void postStop() {
        if (updateSubTimer != null) {
            updateSubTimer.cancel();
            updateSubTimer = null;
        }
        log.info("ServiceRegisterActor postStop");
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(DSF.GetSvcInstances.class, this::handleGetSvcInstances)
            .match(DSF.SyncSvcInstances.class,this::handleSyncSvcInstances)
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
        subscriberMap.keySet().forEach(it -> {
            DSF.SubService sub = DSF.SubService.newBuilder()
                .setService(it)
                .setSubscriber(clientName)
                .build();
            register.tell(sub, self());
        });
    }
    //-------------------------------------------------------------------------------------------------
    private void handleGetSvcInstances(DSF.GetSvcInstances msg) {
        log.info("getSvcInstances: {}",msg.getName());
        register.forward(msg, context());
    }
    //-------------------------------------------------------------------------------------------------
    private void handleSyncSvcInstances(DSF.SyncSvcInstances msg) {
        log.debug("syncSvcInstances: {}", msg.getName());
        register.forward(msg, context());
    }
    //-------------------------------------------------------------------------------------------------
    private void handleSubService(DSF.SubService msg) {
        log.info("Subscribe Service : {}", msg.getService());
        Set<ActorRef> subscribers = subscriberMap.computeIfAbsent(msg.getService(), k -> new HashSet<>());
        subscribers.add(sender());
        register.tell(msg, self());
    }
    //-------------------------------------------------------------------------------------------------
    private void handleUnsubService(DSF.UnsubService msg) {
        log.info("Unsubscribe Service : {}", msg.getService());
        Set<ActorRef> subscribers = subscriberMap.computeIfAbsent(msg.getService(), k -> new HashSet<>());
        subscribers.remove(sender());
        if (subscribers.isEmpty()) {
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
                            log.info("register Service success: {}@{}", msg.name, msg.addr);
                            resetBackoffDelay();
                            return;
                        } else {
                            if (backoff >= MAX_RETRY_DELAY) {
                                log.error("register Service failed: {}@{}", msg.name, msg.addr);
                            } else {
                                log.warn("register Service failed: {}@{}", msg.name, msg.addr);
                            }
                        }
                    } else {
                        if (backoff >= MAX_RETRY_DELAY) {
                            log.error("register Service failed: {}@{}", msg.name, msg.addr, failure);
                        } else {
                            log.warn("register Service failed: {}@{}", msg.name, msg.addr, failure);
                        }
                    }
                    context().system().scheduler().scheduleOnce(
                        Duration.create(getBackoffDelay(), TimeUnit.SECONDS),self(),dsfmsg,context().dispatcher(),self()
                    );
                }
            }, context().dispatcher());
    }
    //将集群中广播的注册事件分发给所有订阅者
    private void handleRegService(DSF.RegService msg) {
        log.trace("RegisterClientActor.handleRegService({})", msg.getAddr());
        Set<ActorRef> subscribers = subscriberMap.get(msg.getName());
        if (subscribers != null) {
            subscribers.forEach(s -> s.tell(msg, self()));
        }
    }
    //-------------------------------------------------------------------------------------------------
    private void handleUnregLocalService(UnregLocalService msg) {
        log.trace("RegisterClientActor.handleUnregLocalService({})", msg.addr);
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
                            log.info("unregister Service success: {}@{}", msg.name, msg.addr);
                            resetBackoffDelay();
                            return;
                        } else {
                            if (backoff >= MAX_RETRY_DELAY) {
                                log.error("unregister Service failed: {}@{}", msg.name, msg.addr);
                            } else {
                                log.warn("unregister Service failed: {}@{}", msg.name, msg.addr);
                            }
                        }
                    } else {
                        if (backoff >= MAX_RETRY_DELAY) {
                            log.error("unregister Service failed: {}@{}", msg.name, msg.addr, failure);
                        } else {
                            log.warn("unregister Service failed: {}@{}", msg.name, msg.addr, failure);
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
        Set<ActorRef> subscribers = subscriberMap.get(msg.getName());
        if (subscribers != null) {
            subscribers.forEach(s -> s.tell(msg, self()));
        }
    }
    //-------------------------------------------------------------------------------------------------
    private long getBackoffDelay() {
        this.backoff = Math.min(MAX_RETRY_DELAY, backoff*2);
        return backoff;
    }

    private void resetBackoffDelay() {
        this.backoff = MIN_RETRY_DELAY;
    }
}
