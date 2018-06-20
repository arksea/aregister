package net.arksea.dsf.service;

/**
 *
 * Created by xiaohaixing on 2018/04/24.
 */
public class ServiceResponse {
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

}
