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
    public String makeRequestId() {
        return UUID.randomUUID().toString().replace("-","");
    }

    @Override
    public DSF.ServiceRequest.Builder encodeRequest(Object msg, boolean oneway) {
        return encodeRequest(makeRequestId(), msg, oneway);
    }

    @Override
    public DSF.ServiceRequest.Builder encodeRequest(String requestId, Object msg, boolean oneway) {
        EncodedPayload p = encode(msg);
        return DSF.ServiceRequest.newBuilder()
            .setOneway(oneway)
            .setRequestId(requestId)
            .setPayload(p.payload)
            .setSerialize(p.serialize)
            .setTypeName(p.typeName);
    }

    @Override
    public Object decodeRequest(DSF.ServiceRequest msg) {
        return decode(new EncodedPayload(msg.getPayload(), msg.getSerialize(), msg.getTypeName()));
//            if (msg.getTracingSpan() != null && msg.getTracingSpan().size() > 0) {
//                obj = TracingUtils.fillTracingSpan(obj, msg.getTracingSpan());
//            }
    }

    @Override
    public DSF.ServiceResponse.Builder encodeResponse(Object msg, String reqid, boolean succeed) {
        EncodedPayload p = encode(msg);
        return DSF.ServiceResponse.newBuilder()
            .setRequestId(reqid)
            .setPayload(p.payload)
            .setSerialize(p.serialize)
            .setTypeName(p.typeName)
            .setSucceed(succeed);
    }

    @Override
    public Object decodeResponse(DSF.ServiceResponse r) {
        return decode(new EncodedPayload(r.getPayload(), r.getSerialize(), r.getTypeName()));
    }

    @Override
    public EncodedPayload encode(Object msg) {
        try {
            ByteString payload;
            DSF.EnumSerialize seri;
            if (msg instanceof ByteString) {
                payload = (ByteString) msg;
                seri = DSF.EnumSerialize.BYTESTR;
            } else if (msg instanceof ByteString[]) {
                ByteString[] arr = (ByteString[]) msg;
                DSF.WrapBytesArray.Builder b = DSF.WrapBytesArray.newBuilder();
                for (ByteString bytes : arr) {
                    b.addValue(bytes);
                }
                payload = b.build().toByteString();
                seri = DSF.EnumSerialize.BYTESTR_ARRAY;
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
            } else {
                ByteArrayOutputStream buff = new ByteArrayOutputStream();
                ObjectOutputStream out = new ObjectOutputStream(buff);
                out.writeObject(msg);
                byte[] bytes = buff.toByteArray();
                payload = ByteString.copyFrom(bytes);
                seri = DSF.EnumSerialize.JAVA;
            }
            //System.out.println("encode type=JAVA, size="+payload.size());
            return new EncodedPayload(payload, seri, "");
        } catch (IOException ex) {
            throw new RuntimeException("Invalid protocol", ex);
        }
    }

    @Override
    public Object decode(EncodedPayload encodedPayload) {
        try {
            ByteString payload = encodedPayload.payload;
            //System.out.println("decode type=JAVA, size="+encodedPayload.payload.size()+", typeName="+encodedPayload.typeName);
            switch (encodedPayload.serialize) {
                case BYTESTR:
                    return encodedPayload.payload;
                case BYTESTR_ARRAY:
                    return DSF.WrapBytesArray.parseFrom(payload).getValueList().toArray(new ByteString[0]);
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
