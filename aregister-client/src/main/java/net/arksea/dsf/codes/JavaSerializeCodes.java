package net.arksea.dsf.codes;

import com.google.protobuf.ByteString;
import net.arksea.dsf.DSF;
import net.arksea.zipkin.akka.TracingUtils;
import zipkin2.Span;
import zipkin2.codec.SpanBytesEncoder;

import java.io.*;
import java.util.Optional;
import java.util.UUID;

/**
 *
 * Created by xiaohaixing on 2018/5/7.
 */
public class JavaSerializeCodes implements ICodes {
    protected SpanBytesEncoder spanEncoder = SpanBytesEncoder.PROTO3;

    protected String makeRequestId() {
        return UUID.randomUUID().toString();
    }

    @Override
    public DSF.ServiceRequest encodeRequest(Object msg, boolean oneway) {
        return encodeRequest(makeRequestId(), msg, oneway);
    }

    @Override
    public DSF.ServiceRequest encodeRequest(String requestId, Object msg, boolean oneway) {
        try {
            ByteArrayOutputStream buff = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(buff);
            out.writeObject(msg);
            byte[] bytes = buff.toByteArray();
            ByteString payload = ByteString.copyFrom(bytes);
            return encodeRequest(requestId, msg, payload, oneway);
        } catch (IOException ex) {
            throw new RuntimeException("Invalid protocol", ex);
        }
    }

    protected DSF.ServiceRequest encodeRequest(String requestId, Object msg, ByteString payload, boolean oneway) {
        DSF.ServiceRequest.Builder builder = DSF.ServiceRequest.newBuilder()
            .setOneway(oneway)
            .setRequestId(requestId)
            .setPayload(payload)
            .setSerialize(DSF.EnumSerialize.JAVA)
            .setTypeName("_JAVA_");
        Optional<Span> op = TracingUtils.getTracingSpan(msg);
        if (op != null && op.isPresent()) {
            byte[] sb = spanEncoder.encode(op.get());
            builder.setTracingSpan(ByteString.copyFrom(sb));
        }
        return builder.build();
    }

    @Override
    public Object decodeRequest(DSF.ServiceRequest msg) {
        try {
            ByteArrayInputStream buff = new ByteArrayInputStream(msg.getPayload().toByteArray());
            ObjectInputStream in = new ObjectInputStream(buff);
            Object obj = in.readObject();
            if (msg.getTracingSpan() != null) {
                obj = TracingUtils.fillTracingSpan(obj, msg.getTracingSpan());
            }
            return obj;
        } catch (Exception ex) {
            throw new RuntimeException("protocol error", ex);
        }
    }

    @Override
    public DSF.ServiceResponse encodeResponse(Object msg, String reqid, boolean succeed) {
        try {
            ByteArrayOutputStream buff = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(buff);
            out.writeObject(msg);
            byte[] bytes = buff.toByteArray();
            ByteString payload = ByteString.copyFrom(bytes);
            return encodeResponse(msg, reqid, payload, succeed);
        } catch (IOException ex) {
            throw new RuntimeException("protocol error", ex);
        }
    }


    protected DSF.ServiceResponse encodeResponse(Object msg, String reqid, ByteString payload, boolean succeed) {
        DSF.ServiceResponse.Builder builder = DSF.ServiceResponse.newBuilder()
            .setRequestId(reqid)
            .setPayload(payload)
            .setSerialize(DSF.EnumSerialize.JAVA)
            .setTypeName("_JAVA_")
            .setSucceed(succeed);
        Optional<Span> op = TracingUtils.getTracingSpan(msg);
        if (op != null && op.isPresent()) {
            byte[] sb = spanEncoder.encode(op.get());
            builder.setTracingSpan(ByteString.copyFrom(sb));
        }
        return builder.build();
    }

    @Override
    public Object decodeResponse(DSF.ServiceResponse response) {
        try {
            ByteArrayInputStream buff = new ByteArrayInputStream(response.getPayload().toByteArray());
            ObjectInputStream in = new ObjectInputStream(buff);
            Object obj = in.readObject();
            if (response.getTracingSpan() != null) {
                obj = TracingUtils.fillTracingSpan(obj, response.getTracingSpan());
            }
            return obj;
        } catch (Exception ex) {
            throw new RuntimeException("protocol error", ex);
        }
    }
}
