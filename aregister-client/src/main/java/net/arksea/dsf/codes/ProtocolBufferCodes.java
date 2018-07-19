package net.arksea.dsf.codes;

import com.google.protobuf.*;
import net.arksea.dsf.DSF;

import java.util.UUID;

/**
 *
 * Created by xiaohaixing on 2018/5/7.
 */
public class ProtocolBufferCodes extends JavaSerializeCodes {
    private final Descriptors.GenericDescriptor descriptor;
    public ProtocolBufferCodes(Descriptors.GenericDescriptor descriptor) {
        this.descriptor = descriptor;
    }
    private String makeRequestId() {
        return UUID.randomUUID().toString();
    }
    @Override
    public DSF.ServiceRequest encodeRequest(Object obj, boolean oneway) {
        if (obj instanceof Message) {
            Message msg = (Message) obj;
            ByteString payload = msg.toByteString();
            return DSF.ServiceRequest.newBuilder()
                .setOneway(oneway)
                .setRequestId(makeRequestId())
                .setPayload(payload)
                .setSerialize(DSF.EnumSerialize.PROTO)
                .setTypeName(msg.getDescriptorForType().getName())
                .build();
        } else {
            return super.encodeRequest(obj, oneway);
        }
    }

    @Override
    public Object decodeRequest(DSF.ServiceRequest msg) {
        try {
            if ("_JAVA_".equals(msg.getTypeName())) {
                return super.decodeRequest(msg);
            } else {
                Descriptors.Descriptor d = descriptor.getFile().findMessageTypeByName(msg.getTypeName());
                DynamicMessage.Builder b = DynamicMessage.newBuilder(d);
                return b.mergeFrom(msg.getPayload()).build();
            }
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException("protocol error", e);
        }
    }

    @Override
    public DSF.ServiceResponse encodeResponse(Object obj, String reqid, boolean succeed) {
        if (obj instanceof Message) {
            Message msg = (Message) obj;
            ByteString payload = msg.toByteString();
            return DSF.ServiceResponse.newBuilder()
                .setRequestId(reqid)
                .setPayload(payload)
                .setSerialize(DSF.EnumSerialize.PROTO)
                .setTypeName(msg.getDescriptorForType().getName())
                .setSucceed(succeed)
                .build();
        } else {
            return super.encodeResponse(obj, reqid, succeed);
        }
    }

    @Override
    public Object decodeResponse(DSF.ServiceResponse response) {
        try {
            if ("_JAVA_".equals(response.getTypeName())) {
                return super.decodeResponse(response);
            } else {
                Descriptors.Descriptor d = descriptor.getFile().findMessageTypeByName(response.getTypeName());
                DynamicMessage.Builder b = DynamicMessage.newBuilder(d);
                return b.mergeFrom(response.getPayload()).build();
            }
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException("Invalid protocol", e);
        }
    }
}
