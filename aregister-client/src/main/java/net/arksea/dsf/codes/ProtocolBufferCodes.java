package net.arksea.dsf.codes;

import com.google.protobuf.*;
import net.arksea.dsf.DSF;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 *
 * Created by xiaohaixing on 2018/5/7.
 */
public class ProtocolBufferCodes extends JavaSerializeCodes {
    private static final String EMPTY_ARRAY = "_EMPTY_ARRAY_";
    private static final String EMPTY_LIST = "_EMPTY_LIST_";
    private final Map<String,ParserInfo> parserMap = new HashMap<>();
    class ParserInfo {
        Parser<Message> parser;
        Class messageType;
        Field tracingSpanField;
        ParserInfo(Parser<Message> parser, Class messageType, Field tracingSpanField) {
            this.parser = parser;
            this.messageType = messageType;
            this.tracingSpanField = tracingSpanField;
        }
    }

    public ProtocolBufferCodes(Map<String,ParserInfo> map) {
        this.parserMap.putAll(map);
    }

    public ProtocolBufferCodes(Descriptors.FileDescriptor ...descriptors) {
        addDescriptors(this.parserMap, descriptors);
    }
    private void addDescriptors(Map<String,ParserInfo> map, Descriptors.FileDescriptor ...descriptors) {
        for (Descriptors.FileDescriptor descriptor : descriptors) {
            String pkg = descriptor.getPackage();
            DescriptorProtos.FileOptions ops = descriptor.getOptions();
            String outerClassName = pkg + "." + ops.getJavaOuterClassname();
            try {
                for (Descriptors.Descriptor d : descriptor.getMessageTypes()) {
                    String n = outerClassName + "$" + d.getName();
                    Class clazz = Class.forName(n, true, descriptor.getClass().getClassLoader());
                    Method method = clazz.getMethod("parser");
                    Parser parser = (Parser) method.invoke(null);
                    Field field = null;
                    Field[] fields = clazz.getDeclaredFields();
                    for (Field value : fields) {
                        if ("tracingSpan_".equals(value.getName())) {
                            field = value;
                        }
                    }
                    map.put(d.getName(), new ParserInfo(parser, clazz, field));
                }
            } catch (Exception ex) {
                throw new RuntimeException("get ProtocolBuffer parser failed: " + outerClassName, ex);
            }
        }
    }

    public ProtocolBufferCodes merge(Descriptors.FileDescriptor ...descriptors) {
        Map<String,ParserInfo> map = new HashMap<>();
        addDescriptors(map, descriptors);
        return new ProtocolBufferCodes(map);
    }

    @Override
    @SuppressWarnings("unchecked")
    public EncodedPayload encode(Object obj) {
        if (obj instanceof Message) {
            Message msg = (Message) obj;
            ByteString payload = msg.toByteString();
            //System.out.println("encode type=PROTO, size="+payload.size());
            return new EncodedPayload(payload, DSF.EnumSerialize.PROTO, msg.getDescriptorForType().getName());
        } else if (obj instanceof Message[]) {
            Message[] arr = (Message[])obj;
            DSF.WrapBytesArray.Builder b = DSF.WrapBytesArray.newBuilder();
            final String typeName = arr.length>0 ? arr[0].getDescriptorForType().getName() : EMPTY_ARRAY;
            for (Message m: arr) {
                b.addValue(m.toByteString());
            }
            ByteString payload = b.build().toByteString();
            //System.out.println("encode type=PROTO[], size="+payload.size());
            return new EncodedPayload(payload, DSF.EnumSerialize.PROTO_ARRAY, typeName);
        } else if (obj instanceof List) {
            List objList = (List)obj;
            if (objList.size() > 0 && !(objList.get(0) instanceof Message)) {
                return super.encode(obj);
            }
            List<Message> list = (List<Message>)obj;
            DSF.WrapBytesArray.Builder b = DSF.WrapBytesArray.newBuilder();
            final String typeName = list.size()>0 ? list.get(0).getDescriptorForType().getName() : EMPTY_LIST;
            for (Message m: list) {
                b.addValue(m.toByteString());
            }
            ByteString payload = b.build().toByteString();
            //System.out.println("encode type=List<PROTO>, size="+payload.size());
            return new EncodedPayload(payload, DSF.EnumSerialize.PROTO_LIST, typeName);
        } else {
            return super.encode(obj);
        }
    }

    @Override
    public Object decode(ByteString payload, DSF.EnumSerialize serialize,String typeName) {
        try {
            if (serialize == DSF.EnumSerialize.PROTO) {
                ParserInfo info = this.parserMap.get(typeName);
                if (info == null) {
                    return super.decode(payload, serialize, typeName);
                } else {
                    //System.out.println("decode type=PROTO, size="+encodedPayload.payload.size()+", typeName="+encodedPayload.typeName);
                    return info.parser.parseFrom(payload);
                }
            } else if (typeName.intern() == EMPTY_ARRAY) {
                return new Message[0];
            } else if (typeName.intern() == EMPTY_LIST) {
                return new LinkedList<Message>();
            } else if (serialize == DSF.EnumSerialize.PROTO_ARRAY) {
                ParserInfo info = this.parserMap.get(typeName);
                if (info == null) {
                    return super.decode(payload, serialize, typeName);
                } else {
                    //System.out.println("decode type=PROTO[], size="+encodedPayload.payload.size()+", typeName="+encodedPayload.typeName);
                    DSF.WrapBytesArray arr = DSF.WrapBytesArray.parseFrom(payload);
                    List<ByteString> anyList = arr.getValueList();
                    List<Message> msgList = new LinkedList<>();
                    for (ByteString a : anyList) {
                        Message obj = info.parser.parseFrom(a);
                        msgList.add(obj);
                    }
                    Message[] msgArray = (Message[]) Array.newInstance(info.messageType, msgList.size());
                    return msgList.toArray(msgArray);
                }
            } else if (serialize == DSF.EnumSerialize.PROTO_LIST) {
                ParserInfo info = this.parserMap.get(typeName);
                if (info == null) {
                    return super.decode(payload, serialize, typeName);
                } else {
                    //System.out.println("decode type=List<PROTO>, size="+encodedPayload.payload.size()+", typeName="+encodedPayload.typeName);
                    DSF.WrapBytesArray arr = DSF.WrapBytesArray.parseFrom(payload);
                    List<ByteString> anyList = arr.getValueList();
                    List<Message> msgList = new LinkedList<>();
                    for (ByteString a : anyList) {
                        Message obj = info.parser.parseFrom(a);
                        msgList.add(obj);
                    }
                    return msgList;
                }
            } else  {
                return super.decode(payload, serialize, typeName);
            }
        } catch (Exception e) {
            throw new RuntimeException("Invalid protocol", e);
        }
    }
}
