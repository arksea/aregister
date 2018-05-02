package net.arksea.dsf.demo;

import net.arksea.dsf.ServiceRequest;

import java.io.Serializable;
import java.util.UUID;

/**
 *
 * Created by xiaohaixing on 2018/4/24.
 */
public class DemoRequest1 implements ServiceRequest,Serializable {
    public final String msg;
    public final int index;
    public final String requestId;
    public DemoRequest1(String msg, int index) {
        this.index = index;
        this.requestId = UUID.randomUUID().toString();
        this.msg = msg;
    }

    @Override
    public String getRequestId() {
        return requestId;
    }

    @Override
    public boolean isOnewayRequest() {
        return false;
    }
}
