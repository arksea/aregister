package net.arksea.dsf.codes;

import com.google.protobuf.ByteString;
import net.arksea.dsf.DSF;

/**
 * Create by xiaohaixing on 2019/12/19
 */
public class EncodedPayload {
    public final ByteString payload;
    public final DSF.EnumSerialize serialize;
    public final String typeName;

    public EncodedPayload(ByteString payload, DSF.EnumSerialize serialize, String typeName) {
        this.payload = payload;
        this.serialize = serialize;
        this.typeName = typeName;
    }
}
