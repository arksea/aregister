package net.arksea.dsf.register;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Terminated;
import akka.pattern.Patterns;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import net.arksea.dsf.DSF;
import net.arksea.dsf.service.ServiceAdaptor;
import scala.concurrent.Future;

import static akka.japi.Util.classTag;

/**
 *
 * Created by xiaohaixing on 2018/4/20.
 */
public class RegisterClient {
    public static final String SYSTEM_NAME = "DsfClientSystem";
    private final ActorSystem system;
    private final ActorRef registerClient;
    private final String clientName;

    public RegisterClient(String clientName, String serverAddr) {
        this.clientName = clientName;
        Config config = ConfigFactory.parseResources("default-dsf-client.conf");
        this.system = ActorSystem.create(SYSTEM_NAME,config.getConfig(SYSTEM_NAME).withFallback(config));
        registerClient = system.actorOf(RegisterClientActor.props(clientName, serverAddr), RegisterClientActor.ACTOR_NAME);
    }

    public void register(String serviceName, String bindHost, int bindPort, ActorRef service, ActorSystem serviceSystem) {
        serviceSystem.actorOf(ServiceAdaptor.props(serviceName, bindHost, bindPort, service, this), serviceName+"-Adaptor");
    }

    public void register(String serivceName, String addr, String path) {
        registerClient.tell(new RegLocalService(serivceName,addr,path), ActorRef.noSender());
    }

    public void unregister(String serviceName, String addr) {
        registerClient.tell(new UnregLocalService(serviceName,addr), ActorRef.noSender());
    }

    public Future<DSF.SvcInstances> getServiceList(String serviceName, long timeout) {
        DSF.GetSvcInstances get = DSF.GetSvcInstances.newBuilder()
            .setName(serviceName)
            .build();
        return Patterns.ask(registerClient, get, timeout).mapTo(classTag(DSF.SvcInstances.class));
    }

    public void subscribe(String serviceName, ActorRef subscriber) {
        registerClient.tell(DSF.SubService.newBuilder()
                                    .setService(serviceName)
                                    .setSubscriber(clientName)
                                    .build(), subscriber);
    }

    public void unsubscribe(String serviceName, ActorRef subscriber) {
        registerClient.tell(DSF.UnsubService.newBuilder()
            .setService(serviceName)
            .build(), subscriber);
    }

    public Future<Terminated> stop() {
        return this.system.terminate();
    }
}
