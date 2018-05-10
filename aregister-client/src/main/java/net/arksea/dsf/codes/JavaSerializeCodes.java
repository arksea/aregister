package net.arksea.dsf.codes;

import com.google.protobuf.ByteString;
import net.arksea.dsf.DSF;

import java.io.*;
import java.util.UUID;

/**
 *
 * Created by xiaohaixing on 2018/5/7.
 */
public class JavaSerializeCodes implements ICodes {
    private String makeRequestId() {
        return UUID.randomUUID().toString();
    }

    @Override
    public DSF.ServiceRequest encodeRequest(Object msg, boolean oneway) {
        try {
            ByteArrayOutputStream buff = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(buff);
            out.writeObject(msg);
            byte[] bytes = buff.toByteArray();
            ByteString payload = ByteString.copyFrom(bytes);
            return DSF.ServiceRequest.newBuilder()
                .setOneway(oneway)
                .setRequestId(makeRequestId())
                .setPayload(payload)
                .setSerialize(DSF.EnumSerialize.JAVA)
                .setTypeName("_JAVA_")
                .build();
        } catch (IOException ex) {
            throw new RuntimeException("Invalid protocol", ex);
        }
    }

    @Override
    public Object decodeRequest(DSF.ServiceRequest msg) {
        try {
            ByteArrayInputStream buff = new ByteArrayInputStream(msg.getPayload().toByteArray());
            ObjectInputStream in = new ObjectInputStream(buff);
            return in.readObject();
        } catch (Exception ex) {
            throw new RuntimeException("protocol error", ex);
        }
    }

    @Override
    public DSF.ServiceResponse encodeResponse(Object msg, String reqid) {
        try {
            ByteArrayOutputStream buff = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(buff);
            out.writeObject(msg);
            byte[] bytes = buff.toByteArray();
            ByteString payload = ByteString.copyFrom(bytes);
            return DSF.ServiceResponse.newBuilder()
                .setRequestId(reqid)
                .setPayload(payload)
                .setSerialize(DSF.EnumSerialize.JAVA)
                .setTypeName("_JAVA_")
                .build();
        } catch (IOException ex) {
            throw new RuntimeException("protocol error", ex);
        }
    }

    @Override
    public Object decodeResponse(DSF.ServiceResponse response) {
        try {
            ByteArrayInputStream buff = new ByteArrayInputStream(response.getPayload().toByteArray());
            ObjectInputStream in = new ObjectInputStream(buff);
            return in.readObject();
        } catch (Exception ex) {
            throw new RuntimeException("protocol error", ex);
        }
    }
}
