package net.arksea.dsf.codes;

import com.google.protobuf.*;
import net.arksea.dsf.DSF;

/**
 *
 * Created by xiaohaixing on 2018/5/7.
 */
public class ProtocolBufferCodes extends JavaSerializeCodes {
    private final Descriptors.GenericDescriptor descriptor;
    public ProtocolBufferCodes(Descriptors.GenericDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    @Override
    public DSF.ServiceRequest.Builder encodeRequest(String requestId, Object obj, boolean oneway) {
        if (obj instanceof Message) {
            Message msg = (Message) obj;
            ByteString payload = msg.toByteString();
            return encodeRequest(requestId, payload, oneway);
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
                DynamicMessage.Builder b = DynamicMessage.newBuilder(d)
                    .mergeFrom(msg.getPayload());
                if (msg.getTracingSpan() != null) {
                    Descriptors.FieldDescriptor field = d.findFieldByName("tracingSpan");
                    if (field != null) {
                        b.setField(field, msg.getTracingSpan());
                    }
                }
                return b.build();
            }
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException("protocol error", e);
        }
    }

    @Override
    public DSF.ServiceResponse.Builder encodeResponse(Object obj, String reqid, boolean succeed) {
        if (obj instanceof Message) {
            Message msg = (Message) obj;
            ByteString payload = msg.toByteString();
            return encodeResponse(msg, reqid, payload, succeed);
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
                DynamicMessage.Builder b = DynamicMessage.newBuilder(d)
                    .mergeFrom(response.getPayload());
                if (response.getTracingSpan() != null) {
                    Descriptors.FieldDescriptor field = d.findFieldByName("tracingSpan");
                    if (field != null) {
                        b.setField(field, response.getTracingSpan());
                    }
                }
                return b.build();
            }
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException("Invalid protocol", e);
        }
    }
}
