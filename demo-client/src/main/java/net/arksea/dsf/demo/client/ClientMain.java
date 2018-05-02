package net.arksea.dsf.demo.client;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.dispatch.OnComplete;
import akka.pattern.Patterns;
import net.arksea.dsf.ServiceResponse;
import net.arksea.dsf.client.ActorClient;
import net.arksea.dsf.client.route.IRouteStrategy;
import net.arksea.dsf.client.route.RouteStrategy;
import net.arksea.dsf.client.route.RouteStrategyFactory;
import net.arksea.dsf.demo.DemoRequest1;
import net.arksea.dsf.register.RegisterClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static akka.japi.Util.classTag;

/**
 *
 * Created by xiaohaixing on 2018/04/17.
 */
public final class ClientMain {
    private static final Logger logger = LogManager.getLogger(ClientMain.class);
    private ClientMain() {};

    /**
     * @param args command line args
     */
    public static void main(final String[] args) {
        try {
            logger.info("Start DEMO Client");
            ActorSystem system = ActorSystem.create("system");
            String serviceName = "net.arksea.dsf.DemoService-1.0";
            RegisterClient registerClient = new RegisterClient("TestClient","127.0.0.1:6501");
            IRouteStrategy routeStrategy = RouteStrategyFactory.create(RouteStrategy.ROUNDROBIN);
            ActorRef client = system.actorOf(ActorClient.props(serviceName, registerClient,routeStrategy));
            for (int i=0; i<80000; ++i) {
                DemoRequest1 msg = new DemoRequest1("hello"+i,i);
                call(client, msg, system);
                Thread.sleep(1000);
            }
            Thread.sleep(10000);
            system.terminate().value();
        } catch (Exception ex) {
            logger.error("Start DEMO Client failed", ex);
        }
    }

    private static void call(ActorRef client, DemoRequest1 msg, ActorSystem system) {
        Patterns.ask(client, msg, 10000).mapTo(classTag(ServiceResponse.class)).onComplete(
            new OnComplete<ServiceResponse>() {
                @Override
                public void onComplete(Throwable failure, ServiceResponse ret) throws Throwable {
                    if (failure == null) {
                        if (ret.isSucceed()) {
                            //logger.info(ret.msg);
                        } else {
                            logger.info(ret.getClass().getName());
                        }
                    } else {
                        logger.warn("failed", failure);
                    }
                }
            }, system.dispatcher()
        );
    }
}
