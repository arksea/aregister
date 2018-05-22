package net.arksea.dsf.register;

import akka.actor.*;
import akka.cluster.Cluster;
import akka.cluster.Member;
import akka.japi.Creator;
import net.arksea.dsf.DSF;
import net.arksea.dsf.store.IRegisterStore;
import net.arksea.dsf.store.Instance;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scala.collection.Iterator;
import scala.collection.SortedSet;

import java.util.UUID;

/**
 * 负责响应客户端请求
 * Created by xiaohaixing on 2018/4/20.
 */

public class RegisterActor extends AbstractActor {
    public final static String ACTOR_NAME = "dsfRegister";
    private final Logger log = LogManager.getLogger(ServiceActor.class);
    private IRegisterStore store;
    private ActorSelection serviceManagerActor;
    private final Cluster cluster = Cluster.get(getContext().getSystem());
    public RegisterActor(IRegisterStore store) {
        this.store = store;
        serviceManagerActor = context().actorSelection("/user/"+ServiceManagerActor.ACTOR_NAME);
    }

    public static Props props(IRegisterStore store) {
        return Props.create(new Creator<Actor>() {
            @Override
            public Actor create() throws Exception {
                return new RegisterActor(store);
            }
        });
    }

    @Override
    public void preStart() {
        log.info("RegisterActor preStart");
    }

    @Override
    public void postStop() {
        log.info("RegisterActor postStop");
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(DSF.SyncSvcInstances.class,    this::handleSyncSvcInstances)
            .match(DSF.GetSvcInstances.class,     this::handleGetSvcInstances)
            .match(DSF.RegService.class,          this::handleRegService)
            .match(DSF.UnregService.class,        this::handleUnregService)
            .match(DSF.SubService.class,          this::handleSubService)
            .match(DSF.UnsubService.class,        this::handleUnsubService)
            .match(DSF.Ping.class,                this::handlePing)
            .match(DSF.GetRegisterInstances.class,this::handleGetRegisterInstances)
            .build();
    }

    private void handleRegService(DSF.RegService msg) {
        log.trace("RegisterActor.handleRegService({},{},{})", msg.getName(), msg.getAddr(), msg.getPath());
        try {
            store.addServiceInstance(msg.getName(), new Instance(msg.getAddr(), msg.getPath()));
            serviceManagerActor.tell(new MSG.SendToAll(msg), self());
            sender().tell(true, self());
        } catch (Exception ex) {
            log.warn("register a service instance failed:{}",msg.toString(), ex);
            sender().tell(false, self());
        }
    }

    private void handleUnregService(DSF.UnregService msg) {
        log.trace("RegisterActor.handleUnregService({},{})", msg.getName(), msg.getAddr());
        try {
            store.delServiceInstance(msg.getName(), msg.getAddr());
            serviceManagerActor.tell(new MSG.SendToAll(msg), self());
            sender().tell(true, self());
        } catch (Exception ex) {
            log.warn("Unregister a service instance failed:{}",msg.toString(), ex);
            sender().tell(false, self());
        }
    }

    private void handleGetSvcInstances(DSF.GetSvcInstances msg) {
        log.trace("RegisterActor.handleGetSvcInstances({})", msg.getName());
        serviceManagerActor.forward(msg, context());
    }

    private void handleSyncSvcInstances(DSF.SyncSvcInstances msg) {
        log.trace("RegisterActor.handleSyncSvcInstances({},{})", msg.getName(), msg.getSerialId());
        serviceManagerActor.forward(msg, context());
    }

    private void handleSubService(DSF.SubService msg) {
        log.trace("RegisterActor.handleSubService({},{})", msg.getService(),msg.getSubscriber());
        serviceManagerActor.forward(msg, context());
    }

    private void handleUnsubService(DSF.UnsubService msg) {
        log.trace("RegisterActor.handleUnsubService({})", msg.getService());
        serviceManagerActor.forward(msg, context());
    }

    private void handlePing(DSF.Ping msg) {
        sender().tell(DSF.Pong.getDefaultInstance(), self());
    }

    private void handleGetRegisterInstances(DSF.GetRegisterInstances msg) {
        DSF.SvcInstances.Builder builder = DSF.SvcInstances.newBuilder()
            .setName("dsfRegister")
            .setSerialId(UUID.randomUUID().toString());

        SortedSet<Member> members = cluster.state().members();
        Iterator<Member> it = members.iterator();
        while (it.hasNext()) {
            Member m = it.next();
            Address a = m.address();
            String addr = a.host().get()+":"+a.port().get();
            String path = "akka.tcp://DsfCluster@"+addr+"/user/dsfRegister";
            builder.addInstances(
                DSF.Instance.newBuilder()
                    .setAddr(addr)
                    .setPath(path)
                    .setOnline(true)
                    .build());
        }
        sender().tell(builder.build(), self());
    }
}
