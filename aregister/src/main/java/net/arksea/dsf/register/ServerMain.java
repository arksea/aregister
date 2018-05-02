package net.arksea.dsf.register;

import akka.actor.ActorSystem;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import net.arksea.dsf.register.redis.RedisRegister;
import net.arksea.dsf.store.IRegisterStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
            logger.info("启动注册服务");
            String host = "172.17"+".149.54";
            IRegisterStore store = new RedisRegister(host,6379, 5000, null);
            Config cfg = ConfigFactory.load();
            ActorSystem system = ActorSystem.create("DsfCluster", cfg);
            system.actorOf(ServiceManagerActor.props(store), ServiceManagerActor.ACTOR_NAME);
            system.actorOf(RegisterActor.props(store), RegisterActor.ACTOR_NAME);

        } catch (Exception ex) {
            LogManager.getLogger(ServerMain.class).error("启动注册服务失败", ex);
        }
    }
}
