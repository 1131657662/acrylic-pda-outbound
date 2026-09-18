package com.acrylic.outbound.sync;

import java.nio.charset.StandardCharsets;
import java.util.*;

final class SyncCursor {
    private SyncCursor() { }
    static long parse(String value, Namespace namespace) {
        try {
            if (value.length() > 256) throw new IllegalArgumentException();
            var parts = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.US_ASCII).split("\\|", -1);
            if (parts.length != 5 || !parts[0].equals("1") || !parts[1].equals(namespace.epoch())
                || !parts[2].equals(namespace.warehouse().toString()) || !parts[3].equals("ORDER")) throw new IllegalArgumentException();
            long sequence = Long.parseLong(parts[4]);
            if (sequence < 0) throw new IllegalArgumentException();
            return sequence;
        } catch (IllegalArgumentException error) { throw new IllegalArgumentException("同步游标无效或不属于当前仓库及恢复代次"); }
    }
    static String encode(Namespace namespace, long sequence) {
        var raw = "1|" + namespace.epoch() + "|" + namespace.warehouse() + "|ORDER|" + sequence;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.US_ASCII));
    }
    record Namespace(String epoch, UUID warehouse) { }
}
