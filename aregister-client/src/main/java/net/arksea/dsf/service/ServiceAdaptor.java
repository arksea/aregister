package net.arksea.dsf.service;

import akka.actor.*;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import com.google.common.util.concurrent.RateLimiter;
import com.google.protobuf.ByteString;
import net.arksea.dsf.DSF;
import net.arksea.dsf.client.InstanceQuality;
import net.arksea.dsf.codes.ICodes;
import net.arksea.dsf.register.RegisterClient;
import net.arksea.zipkin.akka.TracingUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import zipkin2.Span;
import zipkin2.codec.SpanBytesEncoder;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 *
 * Created by xiaohaixing on 2018/5/4.
 */
public class ServiceAdaptor extends AbstractActor {
    protected SpanBytesEncoder spanEncoder = SpanBytesEncoder.PROTO3;
    private static final Logger logger = LogManager.getLogger(ServiceAdaptor.class);
    private final ActorRef service;
    private final ICodes codes;
    private InstanceQuality quality;
    private Cancellable saveStatDataTimer; //保存历史统计数据定时器
    private final RegisterClient register;
    private final String serviceName;
    private final String serviceAddr;
    private final String servicePath;
    private final IRateLimitStrategy rateLimitStrategy; //限流策略
    private RateLimiter rateLimiter;
    private long lastUpdateCheckTime;  //最后一次检测是否需要限流的时间
    private long rateLimitQPS;         //限流QPS，<=0表示不限流

    protected ServiceAdaptor(String serviceName, String host, int port, ActorRef service, ICodes codes, RegisterClient register, IRateLimitStrategy rateLimitStrategy) {
        this.service = service;
        this.codes = codes;
        this.quality = new InstanceQuality("");
        this.serviceName = serviceName;
        this.register = register;
        Address address = Address.apply("akka.tcp",context().system().name(),host, port);
        serviceAddr = host + ":" + port;
        servicePath = self().path().toStringWithAddress(address);
        this.rateLimitStrategy = rateLimitStrategy;
        logger.info("Create Service Adaptor: addr={}, path={}", serviceAddr, servicePath);
    }

    public static Props props(String serviceName, String host, int port, ActorRef service, ICodes codes, RegisterClient register, IRateLimitStrategy rateLimitStrategy) {
        return Props.create(new Creator<ServiceAdaptor>() {
            @Override
            public ServiceAdaptor create() throws Exception {
                return new ServiceAdaptor(serviceName, host, port, service, codes, register, rateLimitStrategy);
            }
        });
    }

    @Override
    public void preStart() {
        logger.info("ServiceAdaptor preStart: {}", serviceName);
        context().system().scheduler().scheduleOnce(Duration.create(10, TimeUnit.SECONDS),
            self(),new DelayRegister(),context().dispatcher(),self());
        saveStatDataTimer = context().system().scheduler().schedule(
            Duration.create(60, TimeUnit.SECONDS),
            Duration.create(60,TimeUnit.SECONDS),
            self(),new SaveStatData(),context().dispatcher(),self());
    }

    @Override
    public void postStop() {
        logger.info("ServiceAdaptor postStop: {}", serviceName);
        if (saveStatDataTimer != null) {
            saveStatDataTimer.cancel();
            saveStatDataTimer = null;
        }
        try {
            Future<Boolean> f =register.unregisterAtRepertory(serviceName, serviceAddr, 10000);
            Await.result(f, Duration.create(10, TimeUnit.SECONDS));
            logger.info("Service unregisted: {}@{}", serviceName, serviceAddr);
        } catch (Exception ex) {
            logger.warn("Unregister service timeout: {}@{}", serviceName, serviceAddr, ex);
        }
    }

    @Override
    public Receive createReceive() {
        return createReceiveBuilder().build();
    }

    protected ReceiveBuilder createReceiveBuilder() {
        return receiveBuilder()
            .match(DSF.ServiceRequest.class,this::handleServiceRequest)
            .match(ServiceResponse.class,   this::handleServiceResponse)
            .match(DSF.Ping.class,          this::handlePing)
            .match(SaveStatData.class,      this::handleSaveStatData)
            .match(ServiceAdaptor.DelayRegister.class, this::handleDelayRegister)
            .match(DSF.GetRequestCountHistory.class,   this::handleGetRequestCountHistory);
    }
    //------------------------------------------------------------------------------------
    private void handleServiceRequest(DSF.ServiceRequest msg) {
        logger.trace("handleServiceRequest(type={},reqid={})", msg.getTypeName(), msg.getRequestId());
        if (rateLimitStrategy != null) {
            updateRateLimiter();
            if (rateLimitQPS > 0 && !rateLimiter.tryAcquire()) {
                Object result = rateLimitStrategy.getRateLimitResponse();
                // quality.failed(0); 此处为异常时的快速失败应对，不能再加入请求时间统计，否则会导致系统"自激振荡"
                DSF.ServiceResponse.Builder builder = codes.encodeResponse(result, msg.getRequestId(), true);
                Optional<Span> op = TracingUtils.getTracingSpan(msg);
                if (op != null && op.isPresent()) {
                    byte[] sb = spanEncoder.encode(op.get());
                    builder.setTracingSpan(ByteString.copyFrom(sb));
                }
                DSF.ServiceResponse r = builder.build();
                sender().forward(r, context());
                return;
            }
        }
        ServiceRequest request;
        ActorRef sender = sender();
        Object obj = codes.decodeRequest(msg);
        request = new ServiceRequest(obj, msg.getRequestId(), sender);
        service.tell(request, self());
    }

    //long ___lastDebugLog;

    private void updateRateLimiter() {
        long now = System.currentTimeMillis();
        if (now - lastUpdateCheckTime > rateLimitStrategy.getMinUpdatePeriod()) {
            this.lastUpdateCheckTime = now;
            long meanQPS = quality.getRequestCount(1) / 60;
            long meanTTS = quality.getMeanRespondTime(1);
            //if (now - ___lastDebugLog > 10_000) {
            //    logger.debug("Current rateLimitQPS={}, meanQPS={}, meanTTS={}", rateLimitQPS, meanQPS, meanTTS);
            //    ___lastDebugLog = now;
            //}
            long qps = rateLimitStrategy.getLimitQPS(meanTTS, meanQPS, rateLimitQPS);
            if (rateLimitQPS != qps) {
                rateLimitQPS = qps;
                logger.warn("Update rateLimitQPS={}, meanQPS={}, meanTTS={}", rateLimitQPS, meanQPS, meanTTS);
                if (rateLimitQPS > 0) {
                    this.rateLimiter = RateLimiter.create(rateLimitQPS);
                }
            }
        }
    }
    //------------------------------------------------------------------------------------
    private void handleServiceResponse(ServiceResponse msg) {
        logger.trace("handleServiceResponse({})", msg.request.reqid);
        if (msg.succeed) {
            quality.succeed(System.currentTimeMillis() - msg.request.requestTime);
        } else {
            quality.failed(System.currentTimeMillis() - msg.request.requestTime);
        }
        DSF.ServiceResponse.Builder builder = codes.encodeResponse(msg.result, msg.request.reqid, msg.succeed);
        Optional<Span> op = TracingUtils.getTracingSpan(msg);
        if (op != null && op.isPresent()) {
            byte[] sb = spanEncoder.encode(op.get());
            builder.setTracingSpan(ByteString.copyFrom(sb));
        }
        DSF.ServiceResponse r = builder.build();
        msg.request.sender.forward(r, context());
    }
    //------------------------------------------------------------------------------------
    private void handlePing(DSF.Ping msg) {
        sender().tell(DSF.Pong.getDefaultInstance(), self());
    }
    //------------------------------------------------------------------------------------
    private static class SaveStatData {}
    private void handleSaveStatData(SaveStatData msg) {
        quality.saveHistory();
    }
    //------------------------------------------------------------------------------------
    class DelayRegister {}
    private void handleDelayRegister(DelayRegister msg) {
        register.registerAtRepertory(serviceName, serviceAddr, servicePath);
    }
    //------------------------------------------------------------------------------------
    private void handleGetRequestCountHistory(DSF.GetRequestCountHistory msg) {
        List<InstanceQuality.Count> counts = quality.getCountHistory();
        DSF.RequestCountHistory.Builder hisBuilder = DSF.RequestCountHistory.newBuilder();
        for (InstanceQuality.Count c: counts) {
            DSF.RequestCount.Builder cb = DSF.RequestCount.newBuilder();
            cb.setRequestCount(c.requestCount);
            cb.setSucceedCount(c.succeedCount);
            cb.setRespondTime(c.respondTime);
            hisBuilder.addItems(cb.build());
        }
        sender().tell(hisBuilder.build(), self());
    }
}
