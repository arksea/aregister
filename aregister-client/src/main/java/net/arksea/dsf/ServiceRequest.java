package net.arksea.dsf;

/**
 *
 * Created by xiaohaixing on 2018/04/24.
 */
public interface ServiceRequest {
    String getRequestId();
    boolean isOnewayRequest();
}
