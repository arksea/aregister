package net.arksea.dsf.service;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import com.google.protobuf.Message;
import net.arksea.dsf.DSF;
import net.arksea.dsf.codes.ICodes;
import net.arksea.dsf.codes.JavaSerializeCodes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * Created by xiaohaixing on 2018/5/4.
 */
public class ServiceAdaptor extends AbstractActor {
    private static final Logger logger = LogManager.getLogger(ServiceAdaptor.class);
    private final ActorRef service;
    private final ICodes codes;
    protected ServiceAdaptor(ActorRef service, ICodes codes) {
        this.service = service;
        this.codes = codes;
    }

    public static Props props(ActorRef service, ICodes codes) {
        return Props.create(new Creator<ServiceAdaptor>() {
            @Override
            public ServiceAdaptor create() throws Exception {
                return new ServiceAdaptor(service, codes);
            }
        });
    }

    public static Props props(ActorRef service) {
        return Props.create(new Creator<ServiceAdaptor>() {
            @Override
            public ServiceAdaptor create() throws Exception {
                return new ServiceAdaptor(service, new JavaSerializeCodes());
            }
        });
    }

    @Override
    public Receive createReceive() {
        return createReceiveBuilder().build();
    }

    protected ReceiveBuilder createReceiveBuilder() {
        return receiveBuilder()
            .match(DSF.ServiceRequest.class,this::handleServiceRequest)
            .match(ServiceResponse.class,   this::handleServiceResponse)
            .match(DSF.Ping.class,          this::handlePing);
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
        if (msg.result instanceof Message) {
            DSF.ServiceResponse r = codes.encodeResponse(msg.result, msg.request.reqid);
            msg.request.sender.forward(r, context());
        } else {
            DSF.ServiceResponse r = codes.encodeResponse(msg.result, msg.request.reqid);
            msg.request.sender.forward(r, context());
        }
    }

    //------------------------------------------------------------------------------------
    private void handlePing(DSF.Ping msg) {
        sender().tell(DSF.Pong.getDefaultInstance(), self());
    }
}
