package net.arksea.dsf.register;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Address;
import akka.actor.Props;
import akka.japi.Creator;
import com.google.protobuf.*;
import net.arksea.dsf.DSF;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.io.*;

/**
 *
 * Created by xiaohaixing on 2018/5/4.
 */
public class ServiceAdaptor extends AbstractActor {
    private static final Logger logger = LogManager.getLogger(ServiceAdaptor.class);
    private final ActorRef service;
    private final Descriptors.GenericDescriptor descriptor;
    private final RegisterClient register;
    private final String serviceName;
    private final String serviceAddr;
    private final String servicePath;
    private ServiceAdaptor(String serviceName, String host, int port, ActorRef service, RegisterClient register, Descriptors.FileDescriptor descriptor) {
        this.serviceName = serviceName;
        this.service = service;
        this.register = register;
        this.descriptor = descriptor;
        Address address = Address.apply("akka.tcp",context().system().name(),host, port);
        serviceAddr = host + ":" + port;
        servicePath = self().path().toStringWithAddress(address);
        logger.debug("addr: {}, path: {}", serviceAddr, servicePath);
    }

    private ServiceAdaptor(String serviceName, String host, int port, ActorRef service, RegisterClient register) {
        this.serviceName = serviceName;
        this.service = service;
        this.register = register;
        this.descriptor = unsupportPB();
        Address address = Address.apply("akka.tcp",context().system().name(),host, port);
        serviceAddr = host + ":" + port;
        servicePath = self().path().toStringWithAddress(address);
        logger.debug("addr: {}, path: {}", serviceAddr, servicePath);
    }

    private Descriptors.GenericDescriptor unsupportPB() {
        return new Descriptors.GenericDescriptor() {
            private static final String err = "Unsupport Protocol Buffer, please pass Descriptors.FileDescriptor at new Client";
            public Message toProto() { throw new RuntimeException(err); }
            public String getName() { throw new RuntimeException(err); }
            public String getFullName() { throw new RuntimeException(err); }
            public Descriptors.FileDescriptor getFile() { throw new RuntimeException(err); }
        };
    }

    public static Props props(String serviceName, String host, int port, ActorRef service, RegisterClient register, Descriptors.FileDescriptor descriptor) {
        return Props.create(new Creator<ServiceAdaptor>() {
            @Override
            public ServiceAdaptor create() throws Exception {
                return new ServiceAdaptor(serviceName, host, port, service, register, descriptor);
            }
        });
    }

    public static Props props(String serviceName, String host, int port, ActorRef service, RegisterClient register) {
        return Props.create(new Creator<ServiceAdaptor>() {
            @Override
            public ServiceAdaptor create() throws Exception {
                return new ServiceAdaptor(serviceName, host, port, service, register);
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
        switch (msg.getSerialize()) {
            case BYTES:
                request = new ServiceRequest(msg.getPayload().toByteArray(), msg.getRequestId(), sender);
                break;
            case JAVA:
                Object obj = decodeJavaMessage(msg);
                request = new ServiceRequest(obj, msg.getRequestId(), sender);
                break;
            case PROTO:
                Message m = decodeProtoBuffMessage(msg);
                request = new ServiceRequest(m, msg.getRequestId(), sender);
                break;
            default:
                throw new RuntimeException("Unsupport protocol: "+msg.getSerialize());
        }
        service.tell(request, self());
    }

    private Message decodeProtoBuffMessage(DSF.ServiceRequest msg) {
        try {
            Descriptors.Descriptor d = descriptor.getFile().findMessageTypeByName(msg.getTypeName());
            DynamicMessage.Builder b = DynamicMessage.newBuilder(d);
            return b.mergeFrom(msg.getPayload()).build();
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException("protocol error", e);
        }
    }

    private Object decodeJavaMessage(DSF.ServiceRequest msg) {
        try {
            ByteArrayInputStream buff = new ByteArrayInputStream(msg.getPayload().toByteArray());
            ObjectInputStream in = new ObjectInputStream(buff);
            return in.readObject();
        } catch (Exception ex) {
            throw new RuntimeException("protocol error");
        }
    }
    //------------------------------------------------------------------------------------
    private void handleServiceResponse(ServiceResponse msg) {
        logger.trace("handleServiceResponse({})", msg.request.reqid);
        if (msg.result instanceof Message) {
            handleProtocoBuffResponse((Message)msg.result, msg.request.reqid, msg.request.sender);
        } else {
            handleJavaMessage(msg.result, msg.request.reqid, msg.request.sender);
        }
    }
    private void handleProtocoBuffResponse(Message msg, String reqid, ActorRef sender) {
        ByteString payload = msg.toByteString();
        DSF.ServiceResponse response = DSF.ServiceResponse.newBuilder()
            .setRequestId(reqid)
            .setPayload(payload)
            .setSerialize(DSF.EnumSerialize.PROTO)
            .setTypeName(msg.getDescriptorForType().getName())
            .build();
        sender.tell(response, self());
    }
    //------------------------------------------------------------------------------------
    private void handleJavaMessage(Object msg, String reqid, ActorRef sender) {
        try {
            ByteArrayOutputStream buff = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(buff);
            out.writeObject(msg);
            byte[] bytes = buff.toByteArray();
            ByteString payload = ByteString.copyFrom(bytes);
            DSF.ServiceResponse response = DSF.ServiceResponse.newBuilder()
                .setRequestId(reqid)
                .setPayload(payload)
                .setSerialize(DSF.EnumSerialize.JAVA)
                .setTypeName(msg.getClass().getName())
                .build();
            sender.tell(response, self());
        } catch (IOException ex) {
            throw new RuntimeException("protocol error", ex);
        }
    }
}
