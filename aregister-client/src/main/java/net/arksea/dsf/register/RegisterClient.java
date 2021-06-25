package net.arksea.dsf.register;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Terminated;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import net.arksea.dsf.client.*;
import net.arksea.dsf.client.route.RouteStrategy;
import net.arksea.dsf.codes.ICodes;
import net.arksea.dsf.codes.JavaSerializeCodes;
import net.arksea.dsf.service.IRateLimitStrategy;
import net.arksea.dsf.service.ServiceAdaptor;
import net.arksea.zipkin.akka.ITracingConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.util.List;
import java.util.concurrent.TimeUnit;


/**
 *
 * Created by xiaohaixing on 2018/4/20.
 */
public class RegisterClient {
    private static final Logger logger = LogManager.getLogger(RegisterClient.class);
    public static final String REG_CLIENT_SYSTEM_NAME = "DsfRegisterClientSystem";
    public static final String SVC_CLIENT_SYSTEM_NAME = "DsfServiceClientSystem";
    public final String clientName;
    final ActorRef actorRef;
    private final ActorSystem system;
    private final ITracingConfig tracingConfig;
    /**
     *
     * @param clientName 用于注册服务分辨请求是由哪个客户端发出的
     * @param seedServerAddrs 注册服务集群的种子服务器地址，通常提供2~3个即可
     */
    public RegisterClient(String clientName, List<String> seedServerAddrs) {
        this(clientName, seedServerAddrs, null);
    }
    public RegisterClient(String clientName, List<String> seedServerAddrs, ITracingConfig tracingConfig) {
        this.clientName = clientName;
        Config config = ConfigFactory.parseResources("default-register-client.conf");
        this.system = ActorSystem.create(REG_CLIENT_SYSTEM_NAME,config.getConfig(REG_CLIENT_SYSTEM_NAME).withFallback(config));
        IInstanceSource instanceSource = new RegisterInstanceSource(seedServerAddrs, this.system);
        actorRef = system.actorOf(RegisterClientActor.props(clientName, instanceSource), RegisterClientActor.ACTOR_NAME);
        this.tracingConfig = tracingConfig;
    }

    /**
     * 订阅服务
     * @param serviceName 服务发布的注册名
     * @param routeStrategy 发起请求的路由策略
     * @param condition 用于判定服务上下线状态
     * @param codes 服务通讯消息的编解码方法
     * @param clientSystem 创建客户端的ActorSystem，因为指定了codes，通常就需要定制ActorSystem的config，因此需要传入此参数
     * @return
     */
    public Client subscribe(String serviceName, RouteStrategy routeStrategy, RequestIdStrategy requestIdStrategy, ISwitchCondition condition, ICodes codes, ActorSystem clientSystem) {
        return new Client(serviceName, routeStrategy, requestIdStrategy, codes, condition, clientSystem, new ServiceInstanceSource(serviceName, this), clientName, tracingConfig);
    }

    //兼容旧的方法签名
    public Client subscribe(String serviceName, RouteStrategy routeStrategy, ISwitchCondition condition, ICodes codes, ActorSystem clientSystem) {
        return new Client(serviceName, routeStrategy, RequestIdStrategy.REGENERATE, codes, condition, clientSystem, new ServiceInstanceSource(serviceName, this), clientName, tracingConfig);
    }

    public Client subscribe(String serviceName, RouteStrategy routeStrategy, RequestIdStrategy requestIdStrategy, ISwitchCondition condition) {
        Config config = ConfigFactory.parseResources("default-service-client.conf");
        ActorSystem clientSystem = ActorSystem.create(SVC_CLIENT_SYSTEM_NAME,config.getConfig(SVC_CLIENT_SYSTEM_NAME).withFallback(config));
        ICodes codes = new JavaSerializeCodes();
        return subscribe(serviceName, routeStrategy, requestIdStrategy, condition, codes, clientSystem);
    }

    public Client subscribe(String serviceName, RouteStrategy routeStrategy, RequestIdStrategy requestIdStrategy) {
        ISwitchCondition condition = new DefaultSwitchCondition();
        return subscribe(serviceName, routeStrategy, requestIdStrategy, condition);
    }

    public Client subscribe(String serviceName, RouteStrategy routeStrategy) {
        ISwitchCondition condition = new DefaultSwitchCondition();
        return subscribe(serviceName, routeStrategy, RequestIdStrategy.REGENERATE, condition);
    }

    public Client subscribe(String serviceName) {
        return subscribe(serviceName, RouteStrategy.ROUNDROBIN, RequestIdStrategy.REGENERATE);
    }


    /**
     * 注册一个服务实例（提供服务实例），系统会尝试向注册服务器注册指定服务，失败会重试，直到成功
     * @param serviceName 全局唯一服务注册名，建议通过这几个部分组成： 包名.服务名-版本号-Profile，例如 net.arksea.DemoService-v1-QA
     * @param bindHost 服务绑定的IP
     * @param bindPort 服务绑定的端口
     * @param service  待注册的服务Actor
     * @param serviceSystem 用于创建服务代理Actor的ActorSystem
     * @param codes 编解码，有两个实现： JavaSerializeCodes和ProtocolBufferCodes,
     *              建议使用ProtocolBufferCodes，访问量小、无性能要求的服务可使用JavaSerializeCodes
     * @param strategy 限流策略，可以为null
     */
    public void register(String serviceName, String bindHost, int bindPort, ActorRef service, ActorSystem serviceSystem, ICodes codes, IRateLimitStrategy strategy) {
        serviceSystem.actorOf(ServiceAdaptor.props(serviceName, bindHost, bindPort, service, codes, this, strategy), serviceName+"-Adaptor");
    }

    public void register(String serviceName, String bindHost, int bindPort, ActorRef service, ActorSystem serviceSystem, ICodes codes) {
        serviceSystem.actorOf(ServiceAdaptor.props(serviceName, bindHost, bindPort, service, codes, this), serviceName+"-Adaptor");
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
