package net.arksea.dsf.client;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.dispatch.Mapper;
import akka.pattern.Patterns;
import net.arksea.dsf.DSF;
import net.arksea.dsf.codes.ICodes;
import net.arksea.dsf.client.route.IRouteStrategy;
import net.arksea.dsf.client.route.RouteStrategy;
import net.arksea.dsf.client.route.RouteStrategyFactory;
import net.arksea.dsf.register.RegisterClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.util.concurrent.TimeUnit;

import static akka.japi.Util.classTag;

/**
 *
 * Created by xiaohaixing on 2018/5/4.
 */
public class Client {
    public final ActorSystem system;
    public final ActorRef router;
    public final ICodes codes;
    private final Logger log = LogManager.getLogger(Client.class);

    /**
     * 序列化：Protocol Buffer
     * @param serviceName
     * @param strategy
     * @param register 注册服务客户端，没有指定此参数则读取本地配置文件
     * @param codes
     * @param system
     */
    public Client(String serviceName, RouteStrategy strategy, ICodes codes, ISwitchCondition condition, ActorSystem system, RegisterClient register) {
        this.system = system;
        this.codes = codes;
        IRouteStrategy routeStrategy = RouteStrategyFactory.create(strategy);
        router = system.actorOf(RequestRouter.props(serviceName, register,routeStrategy, condition));
        Future f = Patterns.ask(router, new RequestRouter.Ready(), 25000); //等待RequestRouter初始化完毕
        try {
            Await.result(f, Duration.create(30, TimeUnit.SECONDS));
        } catch (Exception e) {
            log.warn("Client wait RequestRouter init timeout", e);
        }
    }

    public void tell(Object msg, boolean oneway, ActorRef sender) {
        DSF.ServiceRequest req = codes.encodeRequest(msg, oneway);
        router.tell(req, sender);
    }

    public Future<Object> request(Object msg, long timeout) {
        DSF.ServiceRequest req = codes.encodeRequest(msg, false);
        return Patterns.ask(router, req, timeout).mapTo(classTag(DSF.ServiceResponse.class)).map(
            new Mapper<DSF.ServiceResponse, Object>() {
                public Object apply(DSF.ServiceResponse m) {
                    return codes.decodeResponse(m);
                }
            },system.dispatcher());
    }
}
