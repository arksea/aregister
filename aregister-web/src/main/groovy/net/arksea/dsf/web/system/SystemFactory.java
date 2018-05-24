package net.arksea.dsf.web.system;

import akka.actor.ActorSystem;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import net.arksea.dsf.register.RegisterClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.LinkedList;
import java.util.List;


/**
 *
 * Created by xiaohaixing on 2017/07/22.
 */
@Component
public class SystemFactory {
    private Logger logger = LogManager.getLogger(SystemFactory.class);
    @Value("${aregister.addr1}")
    String aregisterAddr1;
    @Value("${aregister.addr2}")
    String aregisterAddr2;

    RegisterClient registerClient;

    @PostConstruct
    public void init() {
        List<String> addrs = new LinkedList<>();
        addrs.add(aregisterAddr1);
        addrs.add(aregisterAddr2);
        logger.debug("register addr1: {}, addr2: {}", aregisterAddr1, aregisterAddr2);
        registerClient = new RegisterClient("aregister-web", addrs);
    }

    @Bean(name = "registerClient")
    public RegisterClient createRegisterClient() {
        return registerClient;
    }

    @Bean(name = "restapiSystem")
    public ActorSystem createSystem() {
        Config cfg = ConfigFactory.load();
        return ActorSystem.create("restapiSystem", cfg);
    }

}
