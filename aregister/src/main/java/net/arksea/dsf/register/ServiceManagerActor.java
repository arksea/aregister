package net.arksea.dsf.register;

import akka.actor.*;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent;
import akka.cluster.Member;
import akka.japi.Creator;
import net.arksea.dsf.DSF;
import net.arksea.dsf.store.IRegisterStore;
import net.arksea.dsf.store.LocalStore;
import net.arksea.httpclient.asker.FuturedHttpClient;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scala.collection.Iterator;
import scala.collection.SortedSet;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 负责创建ServiceActor，并将群发消息分发给集群中的其他节点
 * Created by xiaohaixing on 2018/4/18.
 */
public class ServiceManagerActor extends AbstractActor {
    public final static String ACTOR_NAME = "dsfServiceManager";
    private final Logger log = LogManager.getLogger(ServiceManagerActor.class);
    private final Map<String, ActorRef> serviceMap = new HashMap<>();
    private final Cluster cluster = Cluster.get(getContext().getSystem());
    private final IRegisterStore store;
    private final ServiceStateLogger stateLogger;

    public static Props props(IRegisterStore store, String stateLogUrl) {
        return Props.create(ServiceManagerActor.class, new Creator<ServiceManagerActor>() {
            @Override
            public ServiceManagerActor create() throws Exception {
                return new ServiceManagerActor(store, stateLogUrl);
            }
        });
    }

    public ServiceManagerActor(IRegisterStore store, String stateLogUrl) {
        this.store = store;
        if (StringUtils.isNotEmpty(stateLogUrl)) {
            FuturedHttpClient client = new FuturedHttpClient(context().system());
            this.stateLogger = new ServiceStateLogger(client, stateLogUrl, 5000);
        } else {
            this.stateLogger = null;
        }
    }

    @Override
    public void preStart() {
        log.info("ServiceManagerActor preStart");
        cluster.subscribe(getSelf(), ClusterEvent.initialStateAsEvents(),
            ClusterEvent.MemberEvent.class, ClusterEvent.UnreachableMember.class);
    }

    @Override
    public void postStop() {
        cluster.unsubscribe(getSelf());
        log.info("ServiceManagerActor postStop");
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(MSG.SendToAll.class,                  this::handleSendToAll)
            .match(DSF.RegService.class,                 this::handleRegService)
            .match(DSF.UnregService.class,               this::handleUnregService)
            .match(DSF.SyncSvcInstances.class,           this::handleSyncSvcInstances)
            .match(DSF.GetSvcInstances.class,            this::handleGetSvcInstances)
            .match(DSF.SubService.class,                 this::handleSubService)
            .match(DSF.UnsubService.class,               this::handleUnsubService)
            .match(ClusterEvent.MemberUp.class,          this::handleMemberUp)
            .match(ClusterEvent.UnreachableMember.class, this::handleUnreachableMember)
            .match(ClusterEvent.MemberRemoved.class,     this::handleMemberRemoved)
            .match(ClusterEvent.ClusterDomainEvent.class,this::handleEvent)
            .match(DSF.GetServiceList.class,             this::handleGetServiceList)
            .match(DSF.GetService.class,                 this::handleGetService)
            .match(MSG.StopServiceActor.class,           this::handleStopServiceActor)
            .build();
    }

    private void handleMemberUp(ClusterEvent.MemberUp msg) {
        log.info("Cluster Member is Up: {}", msg.member());
    }

    private void handleUnreachableMember(ClusterEvent.UnreachableMember msg) {
        log.info("Cluster Member detected as unreachable: {}", msg.member());
    }

    private void handleMemberRemoved(ClusterEvent.MemberRemoved msg) {
        log.info("Cluster Member is Removed: {}", msg.member());
    }

    private void handleEvent(ClusterEvent.ClusterDomainEvent msg) {
        log.debug("Cluster Event: {}", msg);
    }

    private void forwardToService(String name, Object msg) {
        //条件判断的目的是为了尽量防止客户端无意或有意的使用大量不存在的服务名发起请求，
        //造成注册服务器记录并启动大量无效的服务管理Actor
        if (serviceMap.containsKey(name)
                || msg instanceof DSF.RegService
                || serviceExists(name)) {
            ActorRef actor = serviceMap.computeIfAbsent(name, k -> {
                Props props = ServiceActor.props(name, store, stateLogger);
                String actorName = ServiceActor.ACTOR_NAME_PRE + name;
                return context().actorOf(props, actorName);
            });
            actor.forward(msg, context());
        }
    }

    private boolean serviceExists(String name) {
        if (store == null) {
            try {
                return LocalStore.serviceExists(name);
            } catch (IOException ex) {
                log.warn("local store error", ex);
                return false;
            }
        } else {
            return store.serviceExists(name);
        }
    }

    private void handleRegService(DSF.RegService msg) {
        log.trace("ServiceManagerActor.handleRegService({},{},{})", msg.getName(), msg.getAddr(), msg.getPath());
        forwardToService(msg.getName(), msg);
    }

    private void handleUnregService(DSF.UnregService msg) {
        log.trace("ServiceManagerActor.handleUnregService({},{})", msg.getName(), msg.getAddr());
        forwardToService(msg.getName(), msg);
    }

    private void handleGetSvcInstances(DSF.GetSvcInstances msg) {
        log.trace("ServiceManagerActor.handleGetSvcInstances({})", msg.getName());
        forwardToService(msg.getName(), msg);
    }

    private void handleGetService(DSF.GetService msg) {
        log.trace("ServiceManagerActor.handleGetService({})", msg.getName());
        forwardToService(msg.getName(), msg);
    }

    private void handleSyncSvcInstances(DSF.SyncSvcInstances msg) {
        log.trace("ServiceManagerActor.handleSyncSvcInstances({})", msg.getName());
        forwardToService(msg.getName(), msg);
    }

    private void handleSubService(DSF.SubService msg) {
        log.trace("ServiceManagerActor.handleSubService({},{})", msg.getService(), msg.getSubscriber());
        forwardToService(msg.getService(), msg);
    }

    private void handleUnsubService(DSF.UnsubService msg) {
        log.trace("ServiceManagerActor.handleUnsubService({})", msg.getService());
        forwardToService(msg.getService(), msg);
    }

    private void handleSendToAll(MSG.SendToAll sendToAll) {
        log.trace("ServiceManagerActor.handleSendToAll({})", sendToAll.msg);
        SortedSet<Member> members = cluster.state().members();
        Iterator<Member> it = members.iterator();
        while (it.hasNext()) {
            Member m = it.next();
            String path = self().path().toStringWithAddress(m.address());
            context().actorSelection(path).forward(sendToAll.msg, context());
        }
    }

    private void handleGetServiceList(DSF.GetServiceList msg) {
        log.trace("ServiceManagerActor.handleGetServiceList()");
        DSF.ServiceList list = DSF.ServiceList.newBuilder().addAllItems(serviceMap.keySet()).build();
        sender().tell(list, self());
    }

    private void handleStopServiceActor(MSG.StopServiceActor msg) {
        log.info("ServiceManagerActor.handleStopServiceActor({})", msg.serviceName);
        serviceMap.remove(msg.serviceName);
        context().stop(msg.actorRef);
    }
}