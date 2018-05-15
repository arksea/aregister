package net.arksea.dsf.register;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.actor.Terminated;
import akka.pattern.Patterns;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import net.arksea.dsf.DSF;
import net.arksea.dsf.client.Client;
import net.arksea.dsf.client.DefaultSwitchCondition;
import net.arksea.dsf.client.ISwitchCondition;
import net.arksea.dsf.client.ServiceInstanceSource;
import net.arksea.dsf.client.route.RouteStrategy;
import net.arksea.dsf.codes.ICodes;
import net.arksea.dsf.codes.JavaSerializeCodes;
import net.arksea.dsf.service.RegisteredServiceAdaptor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

import static akka.japi.Util.classTag;

/**
 *
 * Created by xiaohaixing on 2018/4/20.
 */
public class RegisterClient {
    private static final Logger logger = LogManager.getLogger(RegisterClient.class);
    public static final String REG_CLIENT_SYSTEM_NAME = "DsfRegisterClientSystem";
    public static final String SVC_CLIENT_SYSTEM_NAME = "DsfServiceClientSystem";
    public final ActorRef registerClient;
    public final String clientName;
    private final ActorSystem system;
    /**
     *
     * @param clientName 用于注册服务分辨客户端
     * @param serverAddr
     */
    public RegisterClient(String clientName, String serverAddr) {
        this.clientName = clientName;
        Config config = ConfigFactory.parseResources("default-register-client.conf");
        this.system = ActorSystem.create(REG_CLIENT_SYSTEM_NAME,config.getConfig(REG_CLIENT_SYSTEM_NAME).withFallback(config));
        registerClient = system.actorOf(RegisterClientActor.props(clientName, serverAddr), RegisterClientActor.ACTOR_NAME);
    }

    public Client subscribe(String serviceName) {
        ICodes codes = new JavaSerializeCodes();
        ISwitchCondition condition = new DefaultSwitchCondition();
        Config config = ConfigFactory.parseResources("default-service-client.conf");
        ActorSystem clientSystem = ActorSystem.create(SVC_CLIENT_SYSTEM_NAME,config.getConfig(SVC_CLIENT_SYSTEM_NAME).withFallback(config));
        return subscribe(serviceName, RouteStrategy.ROUNDROBIN, codes, condition, clientSystem);
    }

    public Client subscribe(String serviceName, ICodes codes, ActorSystem clientSystem) {
        ISwitchCondition condition = new DefaultSwitchCondition();
        return subscribe(serviceName, RouteStrategy.ROUNDROBIN, codes, condition, clientSystem);
    }

    public Client subscribe(String serviceName, RouteStrategy routeStrategy, ICodes codes, ISwitchCondition condition, ActorSystem clientSystem) {
        return new Client(serviceName, routeStrategy, codes, condition, clientSystem, new ServiceInstanceSource(serviceName, this));
    }

    public void register(String serviceName, String bindHost, int bindPort, ActorRef service, ActorSystem serviceSystem) {
        serviceSystem.actorOf(RegisteredServiceAdaptor.props(serviceName, bindHost, bindPort, service, this), serviceName+"-Adaptor");
    }

    public void register(String serviceName, int bindPort, ActorRef service, ActorSystem serviceSystem) throws UnknownHostException {
        InetAddress addr = InetAddress.getLocalHost();
        final String bindHost = addr.getHostAddress();
        serviceSystem.actorOf(RegisteredServiceAdaptor.props(serviceName, bindHost, bindPort, service, this), serviceName+"-Adaptor");
    }

    public Future<Boolean> unregister(String serviceName, ActorSystem serviceSystem, long timeoutMillis) {
        ActorSelection sel = serviceSystem.actorSelection(serviceName+"-Adaptor");
        return Patterns.ask(sel, new RegisteredServiceAdaptor.Unregister(), timeoutMillis)
            .mapTo(classTag(Boolean.class));
    }

    public void registerInfo(String serivceName, String addr, String path) {
        registerClient.tell(new RegLocalService(serivceName,addr,path), ActorRef.noSender());
    }

    public void unregisterInfo(String serviceName, String addr, ActorRef requester) {
        registerClient.tell(new UnregLocalService(serviceName,addr), requester);
    }

    public Future<Boolean> unregisterInfo(String serviceName, String addr, long timeout) {
        return Patterns.ask(registerClient, new UnregLocalService(serviceName,addr), timeout).mapTo(classTag(Boolean.class));
    }

    public Future<DSF.SvcInstances> getServiceList(String serviceName, long timeout) {
        DSF.GetSvcInstances get = DSF.GetSvcInstances.newBuilder()
            .setName(serviceName)
            .build();
        return Patterns.ask(registerClient, get, timeout).mapTo(classTag(DSF.SvcInstances.class));
    }

    public void subscribeInfo(String serviceName, ActorRef subscriber) {
        registerClient.tell(DSF.SubService.newBuilder()
                                    .setService(serviceName)
                                    .setSubscriber(clientName)
                                    .build(), subscriber);
    }

    public void unsubscribeInfo(String serviceName, ActorRef subscriber) {
        registerClient.tell(DSF.UnsubService.newBuilder()
            .setService(serviceName)
            .build(), subscriber);
    }

    public Future<Terminated> stop() {
        return this.system.terminate();
    }

    public void stopAndWait(long waitSeconds) {
        try {
            logger.info("Stopping register client system");
            Future f = this.system.terminate();
            Await.result(f, Duration.apply(waitSeconds, TimeUnit.SECONDS));
            logger.info("Register client system stopped");
        } catch (Exception e) {
            logger.warn("Stop register client system timeout", e);
        }
    }
}
