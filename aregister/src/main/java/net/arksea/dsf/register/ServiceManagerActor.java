package net.arksea.dsf.register;

import akka.actor.*;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent;
import akka.cluster.Member;
import akka.japi.Creator;
import net.arksea.dsf.DSF;
import net.arksea.dsf.store.IRegisterStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scala.collection.Iterator;
import scala.collection.SortedSet;

import java.util.HashMap;
import java.util.Map;

/**
 * 负责创建ServiceActor，并将群发消息分发给集群中的其他节点
 * Created by xiaohaixing on 2018/4/18.
 */
public class ServiceManagerActor extends AbstractActor {
    public final static String ACTOR_NAME = "dsfServiceManager";
    private final Logger log = LogManager.getLogger(ServiceActor.class);
    private final Map<String, ActorRef> serviceMap = new HashMap<>();
    private final Cluster cluster = Cluster.get(getContext().getSystem());
    private final IRegisterStore register;

    public static Props props(IRegisterStore store) {
        return Props.create(ServiceManagerActor.class, new Creator<ServiceManagerActor>() {
            @Override
            public ServiceManagerActor create() throws Exception {
                return new ServiceManagerActor(store);
            }
        });
    }

    public ServiceManagerActor(IRegisterStore register) {
        this.register = register;
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
        ActorRef actor = serviceMap.computeIfAbsent(name, k -> {
            Props props = ServiceActor.props(name, register);
            String actorName = ServiceActor.ACTOR_NAME_PRE + name;
            return context().actorOf(props, actorName);
        });
        actor.forward(msg, context());
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

}