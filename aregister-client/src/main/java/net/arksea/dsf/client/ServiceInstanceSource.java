package net.arksea.dsf.client;

import akka.actor.ActorRef;
import net.arksea.dsf.DSF;
import net.arksea.dsf.register.RegisterClient;
import net.arksea.dsf.register.RegisterManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

/**
 * 服务实例源，从注册服务获取实例列表，并缓存于本地
 * Created by xiaohaixing on 2018/5/14.
 */
public class ServiceInstanceSource extends LocalInstanceSource {
    private static final Logger log = LogManager.getLogger(ServiceInstanceSource.class);
    private final RegisterManager register;

    public ServiceInstanceSource(String serviceName, RegisterClient registerClient) {
        super(serviceName);
        this.register = new RegisterManager(registerClient);
    }

    public void subscribe(ActorRef subscriber) {
        register.subscribeAtRepertory(serviceName, subscriber);
    }

    public void unsubscribe(ActorRef subscriber) {
        register.unsubscribeAtRepertory(serviceName, subscriber);
    }

    public DSF.SvcInstances getSvcInstances() throws Exception {
        try {
            Future<DSF.SvcInstances> future = register.getServiceInstances(serviceName, 5000);
            DSF.SvcInstances result = Await.result(future, Duration.create(5000, "ms"));
            log.info("Load service list form register succeed", serviceName);
            return result;
        } catch (Exception e) {
            log.warn("Load service list form register failed: {}", serviceName, e);
            return loadFromLocalCache();
        }
    }
}
