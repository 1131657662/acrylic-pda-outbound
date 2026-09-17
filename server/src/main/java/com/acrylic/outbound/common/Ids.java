package com.acrylic.outbound.common;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.UUID;

public final class Ids {
    private static final SecureRandom RANDOM = new SecureRandom();
    private Ids() { }

    public static UUID next() {
        long msb = (System.currentTimeMillis() << 16) | 0x7000L | RANDOM.nextInt(4096);
        long lsb = (RANDOM.nextLong() & 0x3fffffffffffffffL) | 0x8000000000000000L;
        return new UUID(msb, lsb);
    }

    public static byte[] bytes(UUID id) {
        return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits())
            .putLong(id.getLeastSignificantBits()).array();
    }
}
