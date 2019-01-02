package net.arksea.dsf.demo.client;

import akka.dispatch.OnComplete;
import akka.japi.pf.FI;
import net.arksea.dsf.client.Client;
import net.arksea.dsf.demo.DemoRequest1;
import net.arksea.dsf.demo.DemoResponse1;
import net.arksea.dsf.register.RegisterClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

import static akka.japi.Util.classTag;

/**
 *
 * Created by xiaohaixing on 2018/04/17.
 */
public final class ClientMain {
    private static final Logger logger = LogManager.getLogger(ClientMain.class);
    private ClientMain() {};
    private static Client client;
    /**
     * @param args command line args
     */
    public static void main(final String[] args) {
        try {
            logger.info("Start DEMO Client");
            String serviceName = "net.arksea.dsf.DemoService-v1.5";
            LinkedList<String> addrs = new LinkedList<>();
            addrs.add("127.0.0.1:6501");
            RegisterClient register = new RegisterClient("TestClient",addrs);
            client = register.subscribe(serviceName);
            for (int i=0; i<2; ++i) {
                DemoRequest1 msg = new DemoRequest1("hello"+i,i);
                Future<DemoResponse1> f = client.request(msg, 10000).mapTo(classTag(DemoResponse1.class));
                f.onComplete(
                    new OnComplete<DemoResponse1>() {
                        @Override
                        public void onComplete(Throwable failure, DemoResponse1 ret) throws Throwable {
                            if (failure == null) {
                                handleComplete(ret, ClientMain::complete);
                            } else {
                                logger.warn("failed", failure);
                            }
                        }
                    }, client.system.dispatcher()
                );
                Thread.sleep(1000);
            }
            Thread.sleep(10000);
            Await.result(client.system.terminate(), Duration.apply(10, TimeUnit.SECONDS));
        } catch (Exception ex) {
            logger.error("Start DEMO Client failed", ex);
        }
    }

    private static <T> void handleComplete(T ret, FI.UnitApply<T> apply) throws Exception {
        client.tracing.trace(ret, apply);
    }

    private static void complete(DemoResponse1 ret) {
        client.tracing.addAnnotation("111111");
    }
}
