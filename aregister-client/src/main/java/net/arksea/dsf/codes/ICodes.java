package net.arksea.dsf.codes;

import net.arksea.dsf.DSF;

/**
 *
 * Created by xiaohaixing on 2018/5/7.
 */
public interface ICodes {
    DSF.ServiceRequest.Builder encodeRequest(Object msg, boolean oneway);
    DSF.ServiceRequest.Builder encodeRequest(String reqid, Object msg, boolean oneway);
    DSF.ServiceResponse.Builder encodeResponse(Object msg, String reqid, boolean succeed);
    Object decodeRequest(DSF.ServiceRequest msg);
    Object decodeResponse(DSF.ServiceResponse response);
    String makeRequestId();
}
