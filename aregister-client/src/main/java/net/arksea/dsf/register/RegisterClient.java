package net.arksea.dsf.register;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.actor.Terminated;
import akka.pattern.Patterns;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import net.arksea.dsf.DSF;
import net.arksea.dsf.client.*;
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
import java.util.List;
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
    public final ActorRef actorRef;
    public final String clientName;
    private final ActorSystem system;
    /**
     *
     * @param clientName 用于注册服务分辨客户端
     * @param serverAddrs
     */
    public RegisterClient(String clientName, List<String> serverAddrs) {
        this.clientName = clientName;
        Config config = ConfigFactory.parseResources("default-register-client.conf");
        this.system = ActorSystem.create(REG_CLIENT_SYSTEM_NAME,config.getConfig(REG_CLIENT_SYSTEM_NAME).withFallback(config));
        IInstanceSource instanceSource = new RegisterInstanceSource(serverAddrs, this.system);
        actorRef = system.actorOf(RegisterClientActor.props(clientName, instanceSource), RegisterClientActor.ACTOR_NAME);
    }

    public Client subscribe(String serviceName) {
        ICodes codes = new JavaSerializeCodes();
        ISwitchCondition condition = new DefaultSwitchCondition();
        Config config = ConfigFactory.parseResources("default-service-client.conf");
        ActorSystem clientSystem = ActorSystem.create(SVC_CLIENT_SYSTEM_NAME,config.getConfig(SVC_CLIENT_SYSTEM_NAME).withFallback(config));
        return subscribe(serviceName, RouteStrategy.ROUNDROBIN, codes, condition, clientSystem);
    }

    public Client subscribe(String serviceName, RouteStrategy routeStrategy) {
        ICodes codes = new JavaSerializeCodes();
        ISwitchCondition condition = new DefaultSwitchCondition();
        Config config = ConfigFactory.parseResources("default-service-client.conf");
        ActorSystem clientSystem = ActorSystem.create(SVC_CLIENT_SYSTEM_NAME,config.getConfig(SVC_CLIENT_SYSTEM_NAME).withFallback(config));
        return subscribe(serviceName, routeStrategy, codes, condition, clientSystem);
    }

    public Client subscribe(String serviceName, RouteStrategy routeStrategy, ISwitchCondition condition) {
        ICodes codes = new JavaSerializeCodes();
        Config config = ConfigFactory.parseResources("default-service-client.conf");
        ActorSystem clientSystem = ActorSystem.create(SVC_CLIENT_SYSTEM_NAME,config.getConfig(SVC_CLIENT_SYSTEM_NAME).withFallback(config));
        return subscribe(serviceName, routeStrategy, codes, condition, clientSystem);
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

    public Future<DSF.SvcInstances> getServiceList(String serviceName, long timeout) {
        DSF.GetSvcInstances get = DSF.GetSvcInstances.newBuilder()
            .setName(serviceName)
            .build();
        return Patterns.ask(actorRef, get, timeout).mapTo(classTag(DSF.SvcInstances.class));
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
