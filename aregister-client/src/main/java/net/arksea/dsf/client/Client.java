package net.arksea.dsf.client;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.dispatch.Mapper;
import akka.japi.pf.FI;
import akka.pattern.Patterns;
import com.google.protobuf.ByteString;
import net.arksea.dsf.DSF;
import net.arksea.dsf.codes.ICodes;
import net.arksea.dsf.client.route.IRouteStrategy;
import net.arksea.dsf.client.route.RouteStrategy;
import net.arksea.dsf.client.route.RouteStrategyFactory;
import net.arksea.zipkin.akka.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.codec.SpanBytesEncoder;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 *
 * Created by xiaohaixing on 2018/5/4.
 */
public class Client {
    private static final Logger log = LogManager.getLogger(Client.class);
    private final IActorTracing tracing;
    protected SpanBytesEncoder spanEncoder = SpanBytesEncoder.PROTO3;
    public final ActorSystem system;
    public final ActorRef router;
    public final ICodes codes;
    public final String clientName;
    public final RequestIdStrategy requestIdStrategy;

    /**
     * @param serviceName
     * @param strategy
     * @param codes
     * @param system
     */
    public Client(String serviceName, RouteStrategy strategy, RequestIdStrategy requestIdStrategy, ICodes codes,
                  ISwitchCondition condition, ActorSystem system, IInstanceSource instanceSource,
                  String clientName, ITracingConfig tracingConfig) {
        this.system = system;
        this.codes = codes;
        this.requestIdStrategy = requestIdStrategy;
        this.clientName = clientName;
        this.tracing = tracingConfig == null ? null : ActorTracingFactory.create(tracingConfig, clientName, "", 0);
        IRouteStrategy routeStrategy = RouteStrategyFactory.create(strategy);
        router = system.actorOf(ServiceRequestRouter.props(serviceName, instanceSource, routeStrategy, condition));
        Future f = Patterns.ask(router, new ServiceRequestRouter.Ready(), 25000); //等待RequestRouter初始化完毕
        try {
            Await.result(f, Duration.create(30, TimeUnit.SECONDS));
        } catch (Exception e) {
            log.warn("Client wait RequestRouter init timeout", e);
        }
    }

    public void tell(Object msg, boolean oneway, ActorRef sender) {
        DSF.ServiceRequest req = encodeRequest(msg, oneway);
        if (tracing == null) {
            router.tell(req, sender);
        } else {
            tracing.tell(router, req, sender);
        }
    }

    private DSF.ServiceRequest encodeRequest(Object msg, boolean oneway) {
        return encodeRequest(codes.makeRequestId(), msg, oneway);
    }
    private DSF.ServiceRequest encodeRequest(String reqid, Object msg, boolean oneway) {
        DSF.ServiceRequest.Builder builder = codes.encodeRequest(reqid, msg, oneway);
        if (tracing != null) {
            Optional<Span> op = TracingUtils.getTracingSpan(msg);
            if (op != null && op.isPresent()) {
                byte[] sb = spanEncoder.encode(op.get());
                builder.setTracingSpan(ByteString.copyFrom(sb));
            } else if (op != null) {
                Endpoint endpoint = tracing.makeEndpoint(this.clientName);
                Span span = Span.newBuilder()
                    .traceId(reqid.replace("-", ""))
                    .id(tracing.makeSpanId())
                    .name(getMsgSpanName(msg))
                    .kind(Span.Kind.PRODUCER)
                    .timestamp(tracing.tracingTimestamp())
                    .remoteEndpoint(endpoint)
                    .build();
                ByteString spanStr = ByteString.copyFrom(spanEncoder.encode(span));
                builder.setTracingSpan(spanStr);
            }
        }
        return builder.build();
    }

    private String getMsgSpanName(Object msg) {
        String name;
        if (msg instanceof ITraceableMessage) {
            name = ((ITraceableMessage)msg).getTracingName();
        } else {
            name = msg.getClass().getSimpleName();
        }
        return name;
    }
    public Future<Object> request(Object msg, long timeout) {
        DSF.ServiceRequest req = encodeRequest(msg, false);
        return request(req, timeout);
    }

    public Future<Object> request(String originalReqid, Object msg, long timeout) {
        String reqid;
        switch (requestIdStrategy) {
            case ORIGINAL:
                reqid = originalReqid;
                break;
            case POSTFIX:
                reqid = originalReqid+"_"+codes.makeRequestId();
                break;
            case POSTFIX_LESS_32:
                reqid = originalReqid.length() < 32 ? originalReqid+"_"+codes.makeRequestId() : originalReqid;
                break;
            case REGENERATE_LESS_32:
                reqid = originalReqid.length() < 32 ? codes.makeRequestId() : originalReqid;
                break;
            default:
            case REGENERATE:
                reqid = codes.makeRequestId();
                break;
        }
        DSF.ServiceRequest req = encodeRequest(reqid, msg, false);
        return request(req, timeout);
    }

    private Future<Object> request(DSF.ServiceRequest req, long timeout) {
        if (tracing == null) {
            return Patterns.ask(router, req, timeout).map(
                new Mapper<Object, Object>() {
                    public Object apply(Object obj) {
                        if (obj instanceof RuntimeException) {
                            throw (RuntimeException) obj;
                        } else {
                            DSF.ServiceResponse m = (DSF.ServiceResponse) obj;
                            return codes.decodeResponse(m);
                        }
                    }
                },system.dispatcher());
        } else {
            return tracing.ask(router, req, clientName, timeout).map(
                new Mapper<Object, Object>() {
                    public Object apply(Object obj) {
                        if (obj instanceof RuntimeException) {
                            throw (RuntimeException) obj;
                        } else {
                            DSF.ServiceResponse m = (DSF.ServiceResponse) obj;
                            return codes.decodeResponse(m);
                        }
                    }
                },system.dispatcher());
        }
    }

    public <T> void trace(T var, FI.UnitApply<T> apply) throws Exception {
        if (tracing == null) {
            apply.apply(var);
        } else {
            tracing.trace(var, apply);
        }
    }
}
