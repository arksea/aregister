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
            Config cfg = ConfigFactory.load();
            String redisHost = cfg.getString("register.store.redis-host");
            int redisPort = cfg.getInt("register.store.redis-port");
            String pwdPath = "register.store.redis-password";
            String redisPwd = cfg.hasPath(pwdPath) ? cfg.getString(pwdPath) : null;
            logger.info("redisHost={}, redisPort={}", redisHost, redisPort);
            IRegisterStore store = new RedisRegister(redisHost,redisPort, 5000, redisPwd);
            ActorSystem system = ActorSystem.create("DsfCluster", cfg);
            system.actorOf(ServiceManagerActor.props(store), ServiceManagerActor.ACTOR_NAME);
            system.actorOf(RegisterActor.props(store), RegisterActor.ACTOR_NAME);

        } catch (Exception ex) {
            LogManager.getLogger(ServerMain.class).error("启动注册服务失败", ex);
        }
    }
}
