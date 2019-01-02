package net.arksea.dsf.service;

import net.arksea.zipkin.akka.ITraceableMessage;
import net.arksea.zipkin.akka.TracingUtils;
import zipkin2.Span;

import java.util.Optional;

/**
 *
 * Created by xiaohaixing on 2018/04/24.
 */
public class ServiceResponse implements ITraceableMessage {
    public final Object result;
    public final ServiceRequest request;
    public final boolean succeed;
    public ServiceResponse(Object result, ServiceRequest request) {
        this.result = result;
        this.request = request;
        this.succeed = true;
    }
    public ServiceResponse(Object result, ServiceRequest request, boolean succeed) {
        this.result = result;
        this.request = request;
        this.succeed = succeed;
    }

    @Override
    public Span getTracingSpan() {
        Optional<Span> op = TracingUtils.getTracingSpan(result);
        if (op == null || !op.isPresent()) {
            return null;
        } else {
            return op.get();
        }
    }

    @Override
    public void setTracingSpan(Span tracingSpan) {
        TracingUtils.fillTracingSpan(result, tracingSpan);
    }

    @Override
    public String getTracingName() {
        return result.getClass().getSimpleName();
    }

}
