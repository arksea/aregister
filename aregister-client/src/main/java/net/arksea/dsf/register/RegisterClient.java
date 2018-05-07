package net.arksea.dsf.register;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Terminated;
import akka.dispatch.OnComplete;
import akka.pattern.Patterns;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import net.arksea.dsf.DSF;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scala.concurrent.Future;

import static akka.japi.Util.classTag;

/**
 *
 * Created by xiaohaixing on 2018/4/20.
 */
public class RegisterClient {
    public static final String SYSTEM_NAME = "DsfClientSystem";
    private static final Logger logger = LogManager.getLogger(RegisterClient.class);
    public final ActorSystem system;
    private final ActorRef registerClient;
    private final String clientName;

    public RegisterClient(String clientName, String serverAddr) {
        this.clientName = clientName;
        Config config = ConfigFactory.parseResources("default-dsf-client.conf");
        this.system = ActorSystem.create(SYSTEM_NAME,config.getConfig(SYSTEM_NAME).withFallback(config));
        registerClient = system.actorOf(RegisterClientActor.props(clientName, serverAddr), RegisterClientActor.ACTOR_NAME);
    }

    public void register(String name, String addr, String path) {
        registerClient.tell(new RegLocalService(name,addr,path), ActorRef.noSender());
    }


    public void unregister(String name, String addr) {
        registerClient.tell(new UnregLocalService(name,addr), ActorRef.noSender());
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

    public void syncServiceList(String serviceName, String serialId, ActorRef subscriber) {
        DSF.SyncSvcInstances get = DSF.SyncSvcInstances.newBuilder()
            .setName(serviceName)
            .setSerialId(serialId)
            .setSubscriber(clientName)
            .build();
        registerClient.tell(get, subscriber);
    }

    public void stop() {
        this.system.terminate().onComplete(new OnComplete<Terminated>() {
            @Override
            public void onComplete(Throwable failure, Terminated success) throws Throwable {
                if (failure == null) {
                    logger.info("DSF Register Client stoped");
                } else {
                    logger.info("stop DSF Register Client failed", failure);
                }
            }
        }, system.dispatcher());
    }
}
