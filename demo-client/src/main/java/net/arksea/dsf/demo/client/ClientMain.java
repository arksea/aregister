package net.arksea.dsf.demo.client;

import akka.actor.ActorSystem;
import akka.dispatch.OnComplete;
import akka.japi.pf.FI;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import net.arksea.dsf.client.Client;
import net.arksea.dsf.client.DefaultSwitchCondition;
import net.arksea.dsf.client.route.RouteStrategy;
import net.arksea.dsf.codes.ICodes;
import net.arksea.dsf.codes.ProtocolBufferCodes;
import net.arksea.dsf.demo.DEMO;
import net.arksea.dsf.register.RegisterClient;
import net.arksea.zipkin.akka.demo.TracingConfigImpl;
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
            String serviceName = "net.arksea.dsf.DemoService-v2";
            LinkedList<String> addrs = new LinkedList<>();
            addrs.add("127.0.0.1:6501");
            RegisterClient register = new RegisterClient("TestClient",addrs,new TracingConfigImpl());
            ICodes codes = new ProtocolBufferCodes(DEMO.getDescriptor());
            client = register.subscribe(serviceName, codes);
            for (int i=0; i<500000; ++i) {
                DEMO.DemoRequest1 msg = DEMO.DemoRequest1.newBuilder().setMsg("hello"+i).setIndex(i).build();
                Future<DEMO.DemoResponse1> f = client.request(msg, 10000).mapTo(classTag(DEMO.DemoResponse1.class));
                f.onComplete(
                    new OnComplete<DEMO.DemoResponse1>() {
                        @Override
                        public void onComplete(Throwable failure, DEMO.DemoResponse1 ret) throws Throwable {
                            if (failure == null) {
                                client.trace(ret, ClientMain::complete);
                            } else {
                                logger.warn("failed", failure);
                            }
                        }
                    }, client.system.dispatcher()
                );
                Thread.sleep(10);
            }
            Thread.sleep(10000);
            Await.result(client.system.terminate(), Duration.apply(10, TimeUnit.SECONDS));
        } catch (Exception ex) {
            logger.error("DEMO Client failed", ex);
        }
    }

    static long __lastLogTime;
    private static void complete(DEMO.DemoResponse1 ret) {
        if (System.currentTimeMillis() - __lastLogTime > 10_000) {
            __lastLogTime = System.currentTimeMillis();
            logger.info("result message: {}", ret.getMsg());
        }
    }
}
