package net.arksea.dsf.demo.client;

import akka.actor.ActorSystem;
import akka.dispatch.OnComplete;
import net.arksea.dsf.codes.ICodes;
import net.arksea.dsf.codes.JavaSerializeCodes;
import net.arksea.dsf.client.Client;
import net.arksea.dsf.client.route.RouteStrategy;
import net.arksea.dsf.demo.DemoRequest1;
import net.arksea.dsf.register.RegisterClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
            RegisterClient register = new RegisterClient("TestClient","127.0.0.1:6501");
            ICodes codes = new JavaSerializeCodes();
            Client client = new Client(serviceName, RouteStrategy.ROUNDROBIN, codes, system, register);
            for (int i=0; i<80000; ++i) {
                DemoRequest1 msg = new DemoRequest1("hello"+i,i);
                client.request(msg, 10000).onComplete(
                    new OnComplete<Object>() {
                        @Override
                        public void onComplete(Throwable failure, Object ret) throws Throwable {
                            if (failure != null) {
                                logger.warn("failed", failure);
                            }
                        }
                    }, system.dispatcher()
                );
                Thread.sleep(1000);
            }
            Thread.sleep(10000);
            system.terminate().value();
        } catch (Exception ex) {
            logger.error("Start DEMO Client failed", ex);
        }
    }
}
