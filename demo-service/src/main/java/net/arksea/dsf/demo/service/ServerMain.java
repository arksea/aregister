package net.arksea.dsf.demo.service;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.routing.ConsistentHashingPool;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import net.arksea.dsf.codes.JavaSerializeCodes;
import net.arksea.dsf.demo.DemoResponse1;
import net.arksea.dsf.register.RegisterClient;
import net.arksea.dsf.service.DefaultRateLimitStrategy;
import net.arksea.dsf.service.IRateLimitConfig;
import net.arksea.dsf.service.IRateLimitStrategy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.net.InetAddress;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

/**
 *
 * Created by xiaohaixing on 2018/04/17.
 */
public final class ServerMain {
    private static final Logger logger = LogManager.getLogger(ServerMain.class);
    private ServerMain() {};

    /**
     * @param args command line args
     */
    public static void main(final String[] args) {
        try {
            logger.info("启动DEMO服务");
            Config cfg = ConfigFactory.load();
            ActorSystem system = ActorSystem.create("DemoSystem",cfg);
            LinkedList<String> addrs = new LinkedList<>();
            addrs.add("127.0.0.1:6501");
            RegisterClient registerClient = new RegisterClient("TestClient",addrs);
            String serviceName = "net.arksea.dsf.DemoService-v1.5";
            int port = cfg.getInt("akka.remote.netty.tcp.port");
            IRateLimitConfig limitConfig = new IRateLimitConfig() {
                private final DemoResponse1 response = new DemoResponse1(1, "rate limit");
                @Override
                public long getLowThreshold() {
                    return 4;
                }
                @Override
                public long getHightThreshold() {
                    return 8;
                }
                @Override
                public Object getRateLimitResponse() {
                    return response;
                }
            };
            IRateLimitStrategy rs = new DefaultRateLimitStrategy(limitConfig);
            ConsistentHashingPool pool = new ConsistentHashingPool(5);
            ActorRef service = system.actorOf(pool.props(DemoActor.props(port)), "DemoServicePool");
            String hostAddrss = InetAddress.getLocalHost().getHostAddress();
            logger.info("hostAddress={}", hostAddrss);
            registerClient.register(serviceName, hostAddrss, port, service, system, new JavaSerializeCodes(), rs);
            Thread.sleep(3000);
//            if (port == 8772) {
//                Thread.sleep(400000);
//                wait(system.terminate());
//                registerClient.stopAndWait(10);
//            }
        } catch (Exception ex) {
            LogManager.getLogger(ServerMain.class).error("启动DEMO服务失败", ex);
        }
    }

    private static void wait(Future f) throws Exception {
        try {
            Await.result(f, Duration.create(10, TimeUnit.SECONDS));
        } catch (Exception ex) {
            logger.warn("Await timeout", ex);
        }
    }
}
