package net.arksea.dsf.codes;

import com.google.protobuf.*;
import net.arksea.dsf.DSF;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * Created by xiaohaixing on 2018/5/7.
 */
public class ProtocolBufferCodes extends JavaSerializeCodes {
    private final Map<String,ParserInfo> parserMap = new HashMap<>();
    class ParserInfo {
        Parser parser;
        Class messageType;
        Field tracingSpanField;
        ParserInfo(Parser parser, Class messageType, Field tracingSpanField) {
            this.parser = parser;
            this.messageType = messageType;
            this.tracingSpanField = tracingSpanField;
        }
    }
    public ProtocolBufferCodes(Descriptors.FileDescriptor descriptor) {
        String pkg = descriptor.getPackage();
        DescriptorProtos.FileOptions ops =descriptor.getOptions();
        String outerClassName = pkg+"."+ops.getJavaOuterClassname();
        try {
            for (Descriptors.Descriptor d : descriptor.getMessageTypes()) {
                String n = outerClassName+"$"+d.getName();
                Class clazz = Class.forName(n, true, descriptor.getClass().getClassLoader());
                Method method = clazz.getMethod("parser");
                Parser parser = (Parser)method.invoke(null);
                Field field = clazz.getDeclaredField("tracingSpan_");
                field.setAccessible(true);
                parserMap.put(d.getName(), new ParserInfo(parser,clazz,field));
            }
        } catch (Exception ex) {
            throw new RuntimeException("get ProtocolBuffer parser failed: "+outerClassName, ex);
        }
    }

    @Override
    public DSF.ServiceRequest.Builder encodeRequest(String requestId, Object obj, boolean oneway) {
        if (obj instanceof Message) {
            Message msg = (Message) obj;
            ByteString payload = msg.toByteString();
            return DSF.ServiceRequest.newBuilder()
                .setOneway(oneway)
                .setRequestId(requestId)
                .setPayload(payload)
                .setSerialize(DSF.EnumSerialize.PROTO)
                .setTypeName(msg.getDescriptorForType().getName());
        } else {
            return super.encodeRequest(requestId, obj, oneway);
        }
    }

    @Override
    public Object decodeRequest(DSF.ServiceRequest msg) {
        try {
            if (msg.getSerialize() == DSF.EnumSerialize.PROTO) {
                ParserInfo info = this.parserMap.get(msg.getTypeName());
                Object obj = info.parser.parseFrom(msg.getPayload());
                if (info.tracingSpanField != null && msg.getTracingSpan() != null) {
                    info.tracingSpanField.set(obj, msg.getTracingSpan());
                }
                return obj;
            } else {
                return super.decodeRequest(msg);
            }
        } catch (Exception e) {
            throw new RuntimeException("protocol error", e);
        }
    }

    @Override
    public DSF.ServiceResponse.Builder encodeResponse(Object obj, String reqid, boolean succeed) {
        if (obj instanceof Message) {
            Message msg = (Message) obj;
            ByteString payload = msg.toByteString();
            return DSF.ServiceResponse.newBuilder()
                .setRequestId(reqid)
                .setPayload(payload)
                .setSerialize(DSF.EnumSerialize.PROTO)
                .setTypeName(msg.getDescriptorForType().getName())
                .setSucceed(succeed);
        } else {
            return super.encodeResponse(obj, reqid, succeed);
        }
    }

    @Override
    public Object decodeResponse(DSF.ServiceResponse response) {
        try {
            if (response.getSerialize() == DSF.EnumSerialize.PROTO) {
                ParserInfo info = this.parserMap.get(response.getTypeName());
                Object obj = info.parser.parseFrom(response.getPayload());
                if (info.tracingSpanField != null && response.getTracingSpan() != null) {
                    info.tracingSpanField.set(obj, response.getTracingSpan());
                }
                return obj;
            } else  {
                return super.decodeResponse(response);
            }
        } catch (Exception e) {
            throw new RuntimeException("Invalid protocol", e);
        }
    }
}
