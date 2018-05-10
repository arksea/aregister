package net.arksea.dsf.service;

/**
 *
 * Created by xiaohaixing on 2018/04/24.
 */
public class ServiceResponse {
    public final Object result;
    public final ServiceRequest request;
    public ServiceResponse(Object result, ServiceRequest request) {
        this.result = result;
        this.request = request;
    }
}
