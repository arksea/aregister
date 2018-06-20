package net.arksea.dsf.service;

import akka.actor.*;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;
import net.arksea.dsf.DSF;
import net.arksea.dsf.client.InstanceQuality;
import net.arksea.dsf.codes.ICodes;
import net.arksea.dsf.codes.JavaSerializeCodes;
import net.arksea.dsf.register.RegLocalService;
import net.arksea.dsf.register.RegisterClient;
import net.arksea.dsf.register.UnregLocalService;
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
 * Created by xiaohaixing on 2018/5/4.
 */
public class ServiceAdaptor extends AbstractActor {
    private static final Logger logger = LogManager.getLogger(ServiceAdaptor.class);
    private final ActorRef service;
    private final ICodes codes;
    private InstanceQuality quality;
    private Cancellable saveStatDataTimer; //保存历史统计数据定时器
    private final RegisterClient register;
    private final String serviceName;
    private final String serviceAddr;
    private final String servicePath;

    protected ServiceAdaptor(String serviceName, String host, int port, ActorRef service, ICodes codes, RegisterClient register) {
        this.service = service;
        this.codes = codes;
        this.quality = new InstanceQuality("");
        this.serviceName = serviceName;
        this.register = register;
        Address address = Address.apply("akka.tcp",context().system().name(),host, port);
        serviceAddr = host + ":" + port;
        servicePath = self().path().toStringWithAddress(address);
        logger.info("Create Service Adaptor: addr={}, path={}", serviceAddr, servicePath);
    }

    public static Props props(String serviceName, String host, int port, ActorRef service, ICodes codes, RegisterClient register) {
        return Props.create(new Creator<ServiceAdaptor>() {
            @Override
            public ServiceAdaptor create() throws Exception {
                return new ServiceAdaptor(serviceName, host, port, service, codes, register);
            }
        });
    }

    public static Props props(String serviceName, String host, int port, ActorRef service, RegisterClient register) {
        return Props.create(new Creator<ServiceAdaptor>() {
            @Override
            public ServiceAdaptor create() throws Exception {
                return new ServiceAdaptor(serviceName, host, port, service, new JavaSerializeCodes(), register);
            }
        });
    }

    @Override
    public void preStart() {
        logger.debug("ServiceAdaptor preStart: {}", serviceName);
        context().system().scheduler().scheduleOnce(Duration.create(10, TimeUnit.SECONDS),
            self(),new DelayRegister(),context().dispatcher(),self());
        saveStatDataTimer = context().system().scheduler().schedule(
            Duration.create(60, TimeUnit.SECONDS),
            Duration.create(60,TimeUnit.SECONDS),
            self(),new SaveStatData(),context().dispatcher(),self());
    }

    @Override
    public void postStop() {
        logger.debug("ServiceAdaptor postStop: {}", serviceName);
        if (saveStatDataTimer != null) {
            saveStatDataTimer.cancel();
            saveStatDataTimer = null;
        }
        try {
            Future f = Patterns.ask(register.actorRef, new UnregLocalService(serviceName,serviceAddr), 10000)
                .mapTo(classTag(Boolean.class));
            Await.result(f, Duration.create(10, TimeUnit.SECONDS));
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
        ServiceRequest request;
        ActorRef sender = sender();
        Object obj = codes.decodeRequest(msg);
        request = new ServiceRequest(obj, msg.getRequestId(), sender);
        service.tell(request, self());
    }
    //------------------------------------------------------------------------------------
    private void handleServiceResponse(ServiceResponse msg) {
        logger.trace("handleServiceResponse({})", msg.request.reqid);
        if (msg.succeed) {
            quality.succeed(System.currentTimeMillis() - msg.request.requestTime);
        } else {
            quality.failed(System.currentTimeMillis() - msg.request.requestTime);
        }
        DSF.ServiceResponse r = codes.encodeResponse(msg.result, msg.request.reqid);
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
        register.actorRef.tell(new RegLocalService(serviceName,serviceAddr,servicePath), ActorRef.noSender());
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
