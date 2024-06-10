package com.github.sepgh.internal.index.tree.node.data;

import com.github.sepgh.internal.utils.BinaryUtils;
import com.google.common.primitives.Longs;

public class LongIdentifier extends NodeData<Long> {
    public static final int BYTES = Long.BYTES + 1;

    public LongIdentifier(byte[] bytes, int beginning) {
        super(bytes, beginning);
    }

    public LongIdentifier(byte[] bytes) {
        super(bytes);
    }

    public LongIdentifier(Long aLong) {
        super(aLong);
    }

    @Override
    protected byte[] valueToByteArray(Long aLong) {
        byte[] result = new byte[BYTES];
        result[0] = 0x01;
        System.arraycopy(
                Longs.toByteArray(aLong),
                0,
                result,
                1,
                Long.BYTES
        );

        return result;
    }

    @Override
    public boolean exists() {
        return bytes[0] == 0x01;
    }

    @Override
    public Long data() {
        return BinaryUtils.bytesToLong(bytes, 1);
    }

    @Override
    public int size() {
        return BYTES;
    }

}
