package net.arksea.dsf.client;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.dispatch.Mapper;
import akka.pattern.Patterns;
import com.google.protobuf.*;
import net.arksea.dsf.DSF;
import net.arksea.dsf.client.route.IRouteStrategy;
import net.arksea.dsf.client.route.RouteStrategy;
import net.arksea.dsf.client.route.RouteStrategyFactory;
import net.arksea.dsf.register.RegisterClient;
import scala.concurrent.Future;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.UUID;
import static akka.japi.Util.classTag;

/**
 *
 * Created by xiaohaixing on 2018/5/4.
 */
public class Client {
    public final ActorRef router;
    private final ActorSystem system;
    private final Descriptors.GenericDescriptor descriptor;

    /**
     * @param serviceName
     * @param strategy
     * @param descriptor ProtocolBuffer协议元数据，用于服务请求与结果消息的序列化与反序列化
     * @param system
     */
    public Client(String serviceName, RouteStrategy strategy, Descriptors.FileDescriptor descriptor, ActorSystem system) {
        this.system = system;
        this.descriptor = descriptor;
        IRouteStrategy routeStrategy = RouteStrategyFactory.create(strategy);
        router = system.actorOf(RequestRouter.props(serviceName, routeStrategy));
    }

    /**
     * 序列化：Protocol Buffer
     * @param serviceName
     * @param strategy
     * @param register   注册服务客户端，没有指定此参数则读取本地配置文件
     * @param descriptor ProtocolBuffer协议元数据，用于服务请求与结果消息的序列化与反序列化
     * @param system
     */
    public Client(String serviceName, RouteStrategy strategy, RegisterClient register, Descriptors.FileDescriptor descriptor, ActorSystem system) {
        this.system = system;
        this.descriptor = descriptor;
        IRouteStrategy routeStrategy = RouteStrategyFactory.create(strategy);
        router = system.actorOf(RequestRouter.props(serviceName, register,routeStrategy));
    }

    /**
     * 序列化：Java
     * @param serviceName
     * @param strategy
     * @param system
     */
    public Client(String serviceName, RouteStrategy strategy, ActorSystem system) {
        this.system = system;
        this.descriptor = unsupportPB();
        IRouteStrategy routeStrategy = RouteStrategyFactory.create(strategy);
        router = system.actorOf(RequestRouter.props(serviceName, routeStrategy));
    }

    /**
     * 序列化：Java
     * @param serviceName
     * @param strategy
     * @param register
     * @param system
     */
    public Client(String serviceName, RouteStrategy strategy, RegisterClient register, ActorSystem system) {
        this.system = system;
        this.descriptor = unsupportPB();
        IRouteStrategy routeStrategy = RouteStrategyFactory.create(strategy);
        router = system.actorOf(RequestRouter.props(serviceName, register,routeStrategy));
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
    public void tell(Message msg, boolean oneway, ActorRef sender) {
        ByteString payload = msg.toByteString();
        DSF.ServiceRequest req = DSF.ServiceRequest.newBuilder()
            .setOneway(oneway)
            .setRequestId(makeRequestId())
            .setPayload(payload)
            .setSerialize(DSF.EnumSerialize.PROTO)
            .setTypeName(msg.getDescriptorForType().getName())
            .build();
        router.tell(req, sender);
    }

    public Future<Message> request(Message msg, long timeout) {
        ByteString payload = msg.toByteString();
        DSF.ServiceRequest req = DSF.ServiceRequest.newBuilder()
            .setOneway(false)
            .setRequestId(makeRequestId())
            .setPayload(payload)
            .setSerialize(DSF.EnumSerialize.PROTO)
            .setTypeName(msg.getDescriptorForType().getName())
            .build();
        return Patterns.ask(router, req, timeout).mapTo(classTag(DSF.ServiceResponse.class)).map(
            new Mapper<DSF.ServiceResponse, Message>() {
                public Message apply(DSF.ServiceResponse m) {
                    return dynamicMessage(m.getTypeName(), m.getPayload());
                }
            },system.dispatcher());
    }

    private Message dynamicMessage(String typeName, ByteString bytes) {
        try {
            Descriptors.Descriptor d = descriptor.getFile().findMessageTypeByName(typeName);
            DynamicMessage.Builder b = DynamicMessage.newBuilder(d);
            return b.mergeFrom(bytes).build();
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException("Invalid protocol", e);
        }
    }

    public Future<Object> request(Object msg, long timeout) {
        try {
            ByteArrayOutputStream buff = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(buff);
            out.writeObject(msg);
            byte[] bytes = buff.toByteArray();
            ByteString payload = ByteString.copyFrom(bytes);
            DSF.ServiceRequest req = DSF.ServiceRequest.newBuilder()
                .setOneway(false)
                .setRequestId(makeRequestId())
                .setPayload(payload)
                .setSerialize(DSF.EnumSerialize.JAVA)
                .setTypeName(msg.getClass().getName())
                .build();
            return Patterns.ask(router, req, timeout);
        } catch (IOException ex) {
            throw new RuntimeException("Invalid protocol", ex);
        }
    }

    public Future<byte[]> request(byte[] msg, long timeout) {
        ByteString payload = ByteString.copyFrom(msg);
        DSF.ServiceRequest req = DSF.ServiceRequest.newBuilder()
            .setOneway(false)
            .setRequestId(makeRequestId())
            .setPayload(payload)
            .setSerialize(DSF.EnumSerialize.BYTES)
            .build();
        return Patterns.ask(router, req, timeout).mapTo(classTag(DSF.ServiceResponse.class)).map(
            new Mapper<DSF.ServiceResponse, byte[]>() {
                public byte[] apply(DSF.ServiceResponse m) {
                    return m.getPayload().toByteArray();
                }
            },system.dispatcher());
    }
    private String makeRequestId() {
        return UUID.randomUUID().toString();
    }
}
