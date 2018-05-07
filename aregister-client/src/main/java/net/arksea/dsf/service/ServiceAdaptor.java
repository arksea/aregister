package net.arksea.dsf.service;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Address;
import akka.actor.Props;
import akka.japi.Creator;
import com.google.protobuf.*;
import net.arksea.dsf.DSF;
import net.arksea.dsf.codes.ICodes;
import net.arksea.dsf.register.RegisterClient;
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
    private final RegisterClient register;
    private final String serviceName;
    private final String serviceAddr;
    private final String servicePath;
    private ServiceAdaptor(String serviceName, String host, int port, ActorRef service, ICodes codes, RegisterClient register) {
        this.serviceName = serviceName;
        this.service = service;
        this.register = register;
        this.codes = codes;
        Address address = Address.apply("akka.tcp",context().system().name(),host, port);
        serviceAddr = host + ":" + port;
        servicePath = self().path().toStringWithAddress(address);
        logger.debug("addr: {}, path: {}", serviceAddr, servicePath);
    }


    public static Props props(String serviceName, String host, int port, ActorRef service, ICodes codes, RegisterClient register) {
        return Props.create(new Creator<ServiceAdaptor>() {
            @Override
            public ServiceAdaptor create() throws Exception {
                return new ServiceAdaptor(serviceName, host, port, service, codes, register);
            }
        });
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(DSF.ServiceRequest.class,    this::handleServiceRequest)
            .match(ServiceResponse.class,   this::handleServiceResponse)
            .build();
    }

    @Override
    public void preStart() throws Exception {
//        InetAddress addr = InetAddress.getLocalHost();
//        final String host = addr.getHostAddress();
        register.register(serviceName, serviceAddr, servicePath);
    }

    @Override
    public void postStop() {
        register.unregister(serviceName, serviceAddr);
    }
    //------------------------------------------------------------------------------------
    private void handleServiceRequest(DSF.ServiceRequest msg) {
        logger.trace("handleServiceRequest({},{})", msg.getTypeName(), msg.getRequestId());
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
            DSF.ServiceResponse r = codes.encodeResponse((Message)msg.result, msg.request.reqid);
            msg.request.sender.tell(r, self());
        } else {
            DSF.ServiceResponse r = codes.encodeResponse(msg.result, msg.request.reqid);
            msg.request.sender.tell(r, self());
        }
    }

}
