package net.arksea.dsf.demo;

import net.arksea.dsf.ServiceResponse;

import java.io.Serializable;

/**
 *
 * Created by xiaohaixing on 2018/4/24.
 */
public class DemoResponse1 implements ServiceResponse,Serializable {
    public final int status;
    public final String msg;
    public final String requestId;
    public DemoResponse1(int status, String msg, String requestId) {
        this.requestId = requestId;
        this.msg = msg;
        this.status = status;
    }

    @Override
    public String getRequestId() {
        return requestId;
    }

    @Override
    public boolean isSucceed() {
        return status == 0;
    }
}
