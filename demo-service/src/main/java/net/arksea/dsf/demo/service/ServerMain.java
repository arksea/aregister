package net.arksea.dsf.demo.service;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import net.arksea.dsf.register.RegisterClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

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
            addrs.add("127.0.0.1:6502");
            RegisterClient registerClient = new RegisterClient("TestClient",addrs);
            String serviceName = "net.arksea.dsf.DemoService-1.0";
            int port = cfg.getInt("akka.remote.netty.tcp.port");
            ActorRef service = system.actorOf(DemoActor.props(port), "DemoService");
            registerClient.register(serviceName, port, service, system);
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
