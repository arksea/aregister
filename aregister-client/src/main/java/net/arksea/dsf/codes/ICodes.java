package net.arksea.dsf.codes;

import net.arksea.dsf.DSF;

/**
 *
 * Created by xiaohaixing on 2018/5/7.
 */
public interface ICodes {
    DSF.ServiceRequest encodeRequest(Object msg, boolean oneway);
    DSF.ServiceRequest encodeRequest(String reqid, Object msg, boolean oneway);
    Object decodeRequest(DSF.ServiceRequest msg);
    DSF.ServiceResponse encodeResponse(Object msg, String reqid, boolean succeed);
    Object decodeResponse(DSF.ServiceResponse response);
}
