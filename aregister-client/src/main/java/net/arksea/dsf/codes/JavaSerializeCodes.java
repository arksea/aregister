package net.arksea.dsf.codes;

import com.google.protobuf.ByteString;
import net.arksea.dsf.DSF;
import org.apache.commons.lang3.tuple.Pair;

import java.io.*;
import java.util.UUID;

/**
 *
 * Created by xiaohaixing on 2018/5/7.
 */
public class JavaSerializeCodes implements ICodes {
    public String makeRequestId() {
        return UUID.randomUUID().toString().replace("-","");
    }

    @Override
    public DSF.ServiceRequest.Builder encodeRequest(Object msg, boolean oneway) {
        return this.encodeRequest(makeRequestId(), msg, oneway);
    }

    @Override
    public DSF.ServiceRequest.Builder encodeRequest(String requestId, Object msg, boolean oneway) {
        Pair<ByteString,DSF.EnumSerialize> p = encodeObj(msg);
        return DSF.ServiceRequest.newBuilder()
            .setOneway(oneway)
            .setRequestId(requestId)
            .setPayload(p.getLeft())
            .setSerialize(p.getRight())
            .setTypeName("");
    }

    @Override
    public Object decodeRequest(DSF.ServiceRequest msg) {
        return decodeObj(msg.getPayload(), msg.getSerialize());
//            if (msg.getTracingSpan() != null && msg.getTracingSpan().size() > 0) {
//                obj = TracingUtils.fillTracingSpan(obj, msg.getTracingSpan());
//            }
    }

    @Override
    public DSF.ServiceResponse.Builder encodeResponse(Object msg, String reqid, boolean succeed) {
        Pair<ByteString,DSF.EnumSerialize> p = encodeObj(msg);
        return DSF.ServiceResponse.newBuilder()
            .setRequestId(reqid)
            .setPayload(p.getLeft())
            .setSerialize(p.getRight())
            .setTypeName("")
            .setSucceed(succeed);
    }

    @Override
    public Object decodeResponse(DSF.ServiceResponse response) {
        return decodeObj(response.getPayload(), response.getSerialize());
    }

    private Pair<ByteString,DSF.EnumSerialize> encodeObj(Object msg) {
        try {
            ByteString payload;
            DSF.EnumSerialize seri;
            if (msg instanceof String) {
                payload = DSF.WrapStr.newBuilder().setValue((String) msg).build().toByteString();
                seri = DSF.EnumSerialize.STRING;
            } else if (msg instanceof Integer) {
                payload = DSF.WrapInt.newBuilder().setValue((Integer) msg).build().toByteString();
                seri = DSF.EnumSerialize.INT;
            } else if (msg instanceof Long) {
                payload = DSF.WrapLong.newBuilder().setValue((Long) msg).build().toByteString();
                seri = DSF.EnumSerialize.LONG;
            } else if (msg instanceof Boolean) {
                payload = DSF.WrapBool.newBuilder().setValue((Boolean) msg).build().toByteString();
                seri = DSF.EnumSerialize.BOOL;
            } else if (msg instanceof Float) {
                payload = DSF.WrapFloat.newBuilder().setValue((Float) msg).build().toByteString();
                seri = DSF.EnumSerialize.FLOAT;
            } else if (msg instanceof Double) {
                payload = DSF.WrapDouble.newBuilder().setValue((Double) msg).build().toByteString();
                seri = DSF.EnumSerialize.DOUBLE;
            } else if (msg instanceof ByteString) {
                payload = DSF.WrapBytes.newBuilder().setValue((ByteString) msg).build().toByteString();
                seri = DSF.EnumSerialize.BYTES;
            } else {
                ByteArrayOutputStream buff = new ByteArrayOutputStream();
                ObjectOutputStream out = new ObjectOutputStream(buff);
                out.writeObject(msg);
                byte[] bytes = buff.toByteArray();
                payload = ByteString.copyFrom(bytes);
                seri = DSF.EnumSerialize.JAVA;
            }
            return Pair.of(payload, seri);
        } catch (IOException ex) {
            throw new RuntimeException("Invalid protocol", ex);
        }
    }

    private Object decodeObj(ByteString payload, DSF.EnumSerialize seri) {
        try {
            switch (seri) {
                case STRING:
                    return DSF.WrapStr.parseFrom(payload).getValue();
                case INT:
                    return DSF.WrapInt.parseFrom(payload).getValue();
                case LONG:
                    return DSF.WrapLong.parseFrom(payload).getValue();
                case BOOL:
                    return DSF.WrapBool.parseFrom(payload).getValue();
                case FLOAT:
                    return DSF.WrapFloat.parseFrom(payload).getValue();
                case DOUBLE:
                    return DSF.WrapDouble.parseFrom(payload).getValue();
                case BYTES:
                    return DSF.WrapBytes.parseFrom(payload).getValue();
                default:
                    ByteArrayInputStream buff = new ByteArrayInputStream(payload.toByteArray());
                    ObjectInputStream in = new ObjectInputStream(buff);
                    return in.readObject();
            }
        } catch (Exception ex) {
            throw new RuntimeException("Invalid protocol", ex);
        }
    }
}
