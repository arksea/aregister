package net.arksea.dsf.codes;

import com.google.protobuf.*;
import net.arksea.dsf.DSF;

import java.util.UUID;

/**
 *
 * Created by xiaohaixing on 2018/5/7.
 */
public class ProtocolBufferCodes implements ICodes {
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
            throw new RuntimeException("The object is not a Protocol Buffer message: " + obj.getClass().getName());
        }
    }

    @Override
    public Object decodeRequest(DSF.ServiceRequest msg) {
        try {
            Descriptors.Descriptor d = descriptor.getFile().findMessageTypeByName(msg.getTypeName());
            DynamicMessage.Builder b = DynamicMessage.newBuilder(d);
            return b.mergeFrom(msg.getPayload()).build();
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException("protocol error", e);
        }
    }

    @Override
    public DSF.ServiceResponse encodeResponse(Object obj, String reqid) {
        if (obj instanceof Message) {
            Message msg = (Message) obj;
            ByteString payload = msg.toByteString();
            return DSF.ServiceResponse.newBuilder()
                .setRequestId(reqid)
                .setPayload(payload)
                .setSerialize(DSF.EnumSerialize.PROTO)
                .setTypeName(msg.getDescriptorForType().getName())
                .build();
        } else {
            throw new RuntimeException("The object is not a Protocol Buffer message: " + obj.getClass().getName());
        }
    }

    @Override
    public Object decodeResponse(DSF.ServiceResponse response) {
        try {
            Descriptors.Descriptor d = descriptor.getFile().findMessageTypeByName(response.getTypeName());
            DynamicMessage.Builder b = DynamicMessage.newBuilder(d);
            return b.mergeFrom(response.getPayload()).build();
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException("Invalid protocol", e);
        }
    }
}
