package net.arksea.dsf.service;

import akka.actor.ActorRef;
import akka.routing.ConsistentHashingRouter;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import net.arksea.zipkin.akka.ITraceableMessage;
import net.arksea.zipkin.akka.TracingUtils;
import zipkin2.Span;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 *
 * Created by xiaohaixing on 2018/04/24.
 */
public class ServiceRequest implements ConsistentHashingRouter.ConsistentHashable, ITraceableMessage {
    public final String reqid;
    public final Object message;
    public final long requestTime;
    final ActorRef sender;
    public ServiceRequest(Object message, ActorRef sender) {
        this.message = message;
        this. sender = sender;
        this.reqid = UUID.randomUUID().toString();
        this.requestTime = System.currentTimeMillis();
    }
    public ServiceRequest(Object message, String reqid, ActorRef sender) {
        this.message = message;
        this.sender = sender;
        this.requestTime = System.currentTimeMillis();
        if (reqid == null) {
            this.reqid = UUID.randomUUID().toString();
        } else {
            this.reqid = reqid;
        }
    }

    @Override
    public Object consistentHashKey() {
        if (message instanceof ConsistentHashingRouter.ConsistentHashable) {
            ConsistentHashingRouter.ConsistentHashable m = (ConsistentHashingRouter.ConsistentHashable) message;
            return m.consistentHashKey();
        } else if (message instanceof Message) {
            Message m = (Message)message;
            //--------------------------------------------------------------------------------------------------
            //每个类型首次调用getAllFields需要调用com.google.protobuf.GeneratedMessageV3.internalGetFieldAccessorTable()
            //初始化元数据，会花费数十ms时间，但之后会使用已初始化的元数据不会额外花费时间，所以对性能基本没有影响
            Map<Descriptors.FieldDescriptor, Object> fm = m.getAllFields();
            for (Map.Entry<Descriptors.FieldDescriptor, Object> e: fm.entrySet()) {
                String name = e.getKey().getName();
                if ("consistentHashKey".equals(name) || "key".equals(name)) {
                    return e.getValue();
                }
            }
            //--------------------------------------------------------------------------------------------------
            return message;
        } else {
            return message;
        }
    }

    @Override
    public Span getTracingSpan() {
        Optional<Span> op = TracingUtils.getTracingSpan(message);
        if (op == null || !op.isPresent()) {
            return null;
        } else {
            return op.get();
        }
    }

    @Override
    public void setTracingSpan(Span tracingSpan) {
        TracingUtils.fillTracingSpan(message, tracingSpan);
    }


    @Override
    public String getTracingName() {
        return message.getClass().getSimpleName();
    }
}