package net.arksea.dsf.client;

import akka.actor.ActorRef;
import net.arksea.dsf.DSF;
import net.arksea.dsf.register.RegisterClient;
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
    private final RegisterClient registerClient;

    public ServiceInstanceSource(String serviceName, RegisterClient registerClient) {
        super(serviceName);
        this.registerClient = registerClient;
    }

    public void subscribe(ActorRef subscriber) {
        registerClient.actorRef.tell(DSF.SubService.newBuilder()
                            .setService(serviceName)
                            .setSubscriber(registerClient.clientName)
                            .build(), subscriber);
    }

    public void unsubscribe(ActorRef subscriber) {
        registerClient.actorRef.tell(DSF.UnsubService.newBuilder()
            .setService(serviceName)
            .build(), subscriber);
    }

    public DSF.SvcInstances getSvcInstances() throws Exception {
        try {
            Future<DSF.SvcInstances> future = registerClient.getServiceList(serviceName, 5000);
            DSF.SvcInstances result = Await.result(future, Duration.create(5000, "ms"));
            log.info("Load service list form register succeed", serviceName);
            return result;
        } catch (Exception e) {
            log.warn("Load service list form register failed: {}", serviceName, e);
            return loadFromLocalCache();
        }
    }
}
