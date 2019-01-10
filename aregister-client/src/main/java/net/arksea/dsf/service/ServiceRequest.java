package net.arksea.dsf.service;

import akka.actor.ActorRef;
import akka.routing.ConsistentHashingRouter;
import net.arksea.zipkin.akka.ITraceableMessage;
import net.arksea.zipkin.akka.TracingUtils;
import zipkin2.Span;

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
        } else {
            return reqid;
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