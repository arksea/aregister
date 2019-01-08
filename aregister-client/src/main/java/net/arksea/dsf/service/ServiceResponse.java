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

    /**
     *
     * @param result
     * @param request
     * @param succeed 当设置为false时，用于标识服务端运行时异常，不可用于业务错误。
     *                此异常将被纳入统计系统健康状态的统计，当被判断为健康状态不佳时可能会被做限流或下线处理，
     *                所以只有当服务遭遇不可恢复的运行时异常，希望系统做限流或下线处理时才可设置此值为false，不可用于业务错误
     */
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
