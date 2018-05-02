package net.arksea.dsf.client;

import net.arksea.dsf.ServiceResponse;

/**
 *
 * @author xiaohaixing
 */
public class NoUseableService implements ServiceResponse {
    private final String reqid;
    public NoUseableService(String reqid) {
        this.reqid = reqid;
    }
    @Override
    public String getRequestId() {
        return reqid;
    }

    @Override
    public boolean isSucceed() {
        return false;
    }
}
