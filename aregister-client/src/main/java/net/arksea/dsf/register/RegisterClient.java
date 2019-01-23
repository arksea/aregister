package net.arksea.dsf.register;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Terminated;
import akka.pattern.Patterns;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import net.arksea.dsf.DSF;
import net.arksea.dsf.client.*;
import net.arksea.dsf.client.route.RouteStrategy;
import net.arksea.dsf.codes.ICodes;
import net.arksea.dsf.codes.JavaSerializeCodes;
import net.arksea.dsf.service.IRateLimitStrategy;
import net.arksea.dsf.service.ServiceAdaptor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static akka.japi.Util.classTag;

/**
 *
 * Created by xiaohaixing on 2018/4/20.
 */
public class RegisterClient {
    private static final Logger logger = LogManager.getLogger(RegisterClient.class);
    public static final String REG_CLIENT_SYSTEM_NAME = "DsfRegisterClientSystem";
    public static final String SVC_CLIENT_SYSTEM_NAME = "DsfServiceClientSystem";
    public final ActorRef actorRef;
    public final String clientName;
    private final ActorSystem system;
    /**
     *
     * @param clientName 用于注册服务分辨请求是由哪个客户端发出的
     * @param seedServerAddrs 注册服务集群的种子服务器地址，通常提供2~3个即可
     */
    public RegisterClient(String clientName, List<String> seedServerAddrs) {
        this.clientName = clientName;
        Config config = ConfigFactory.parseResources("default-register-client.conf");
        this.system = ActorSystem.create(REG_CLIENT_SYSTEM_NAME,config.getConfig(REG_CLIENT_SYSTEM_NAME).withFallback(config));
        IInstanceSource instanceSource = new RegisterInstanceSource(seedServerAddrs, this.system);
        actorRef = system.actorOf(RegisterClientActor.props(clientName, instanceSource), RegisterClientActor.ACTOR_NAME);
    }

    /**
     * 订阅服务
     * @param serviceName 服务发布的注册名
     * @param routeStrategy 发起请求的路由策略
     * @param codes 服务通讯消息的编解码方法
     * @param condition 用于判定服务上下线状态
     * @param clientSystem 创建客户端的ActorSystem，因为指定了codes，通常就需要定制ActorSystem的config，因此需要传入此参数
     * @return
     */
    public Client subscribe(String serviceName, RouteStrategy routeStrategy, ICodes codes, ISwitchCondition condition, ActorSystem clientSystem) {
        return new Client(serviceName, routeStrategy, codes, condition, clientSystem, new ServiceInstanceSource(serviceName, this), clientName);
    }

    public Client subscribe(String serviceName) {
        ICodes codes = new JavaSerializeCodes();
        ISwitchCondition condition = new DefaultSwitchCondition();
        Config config = ConfigFactory.parseResources("default-service-client.conf");
        ActorSystem clientSystem = ActorSystem.create(SVC_CLIENT_SYSTEM_NAME,config.getConfig(SVC_CLIENT_SYSTEM_NAME).withFallback(config));
        return subscribe(serviceName, RouteStrategy.ROUNDROBIN, codes, condition, clientSystem);
    }

    public Client subscribe(String serviceName, RouteStrategy routeStrategy) {
        ICodes codes = new JavaSerializeCodes();
        ISwitchCondition condition = new DefaultSwitchCondition();
        Config config = ConfigFactory.parseResources("default-service-client.conf");
        ActorSystem clientSystem = ActorSystem.create(SVC_CLIENT_SYSTEM_NAME,config.getConfig(SVC_CLIENT_SYSTEM_NAME).withFallback(config));
        return subscribe(serviceName, routeStrategy, codes, condition, clientSystem);
    }

    public Client subscribe(String serviceName, RouteStrategy routeStrategy, ISwitchCondition condition) {
        ICodes codes = new JavaSerializeCodes();
        Config config = ConfigFactory.parseResources("default-service-client.conf");
        ActorSystem clientSystem = ActorSystem.create(SVC_CLIENT_SYSTEM_NAME,config.getConfig(SVC_CLIENT_SYSTEM_NAME).withFallback(config));
        return subscribe(serviceName, routeStrategy, codes, condition, clientSystem);
    }

    public Client subscribe(String serviceName, ICodes codes, ActorSystem clientSystem) {
        ISwitchCondition condition = new DefaultSwitchCondition();
        return subscribe(serviceName, RouteStrategy.ROUNDROBIN, codes, condition, clientSystem);
    }

    /**
     * 直接在注册服务器上注销一个服务
     * @param serviceName  服务注册名
     * @param serviceAddr  host:port 格式的服务地址
     */
    public Future<Boolean> unregisterAtRepertory(String serviceName, String serviceAddr, long timeout) {
        return Patterns.ask(actorRef, new UnregLocalService(serviceName,serviceAddr), timeout)
            .mapTo(classTag(Boolean.class));
    }
    public void unregisterAtRepertory(String serviceName, String serviceAddr) {
        actorRef.tell(new UnregLocalService(serviceName, serviceAddr), ActorRef.noSender());
    }

    /**
     * 直接在注册服务器上注册一个服务（提供服务path）
     * @param serviceName
     * @param serviceAddr
     */
    public Future<Boolean> registerAtRepertory(String serviceName, String serviceAddr, String servicePath, long timeout) {
        return Patterns.ask(actorRef, new RegLocalService(serviceName, serviceAddr, servicePath), timeout)
            .mapTo(classTag(Boolean.class));
    }
    public void registerAtRepertory(String serviceName, String serviceAddr, String servicePath) {
        actorRef.tell(new RegLocalService(serviceName, serviceAddr, servicePath), ActorRef.noSender());
    }

    /**
     * 注册一个服务实例（提供服务实例），系统会尝试向注册服务器注册指定服务，失败会重试，直到成功
     * @param serviceName 全局唯一服务注册名，建议通过这几个部分组成： 包名.服务名-版本号-Profile，例如 net.arksea.DemoService-v1-QA
     * @param bindHost 服务绑定的主机名
     * @param bindPort 服务绑定的端口号
     * @param service  服务ActorRef
     * @param serviceSystem 创建服务的ActorSystem
     */
    public void register(String serviceName, String bindHost, int bindPort, ActorRef service, ActorSystem serviceSystem, ICodes codes, IRateLimitStrategy strategy) {
        serviceSystem.actorOf(ServiceAdaptor.props(serviceName, bindHost, bindPort, service, codes, this, strategy), serviceName+"-Adaptor");
    }

    /**
     * 获取指定服务的实例列表
     * @param serviceName 服务名
     * @param timeout 超时时间（毫秒）
     * @return 实例列表
     */
    public Future<DSF.SvcInstances> getServiceInstances(String serviceName, long timeout) {
        DSF.GetSvcInstances get = DSF.GetSvcInstances.newBuilder()
            .setName(serviceName)
            .build();
        return Patterns.ask(actorRef, get, timeout).mapTo(classTag(DSF.SvcInstances.class));
    }

    public Future<DSF.ServiceList> getServiceList(long timeout) {
        return Patterns.ask(actorRef, DSF.GetServiceList.getDefaultInstance(), timeout)
            .mapTo(classTag(DSF.ServiceList.class));
    }

    public Future<DSF.Service> getService(String serviceName, long timeout) {
        DSF.GetService get = DSF.GetService.newBuilder().setName(serviceName).build();
        return Patterns.ask(actorRef, get, timeout).mapTo(classTag(DSF.Service.class));
    }

    /**
     * 停止注册服务客户端ActorySystem，通常无需调用，因为系统创建时默认指定daemonic=on，会随进程退出自动退出；
     * 除非你需要在进程生命周期内停止注册服务客户端系统
     * @return
     */
    public Future<Terminated> stop() {
        return this.system.terminate();
    }

    public void stopAndWait(long waitSeconds) {
        try {
            logger.info("Stopping register client system");
            Future f = this.system.terminate();
            Await.result(f, Duration.apply(waitSeconds, TimeUnit.SECONDS));
            logger.info("Register client system stopped");
        } catch (Exception e) {
            logger.warn("Stop register client system timeout", e);
        }
    }
}
