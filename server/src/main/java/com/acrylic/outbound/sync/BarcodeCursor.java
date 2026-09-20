package com.acrylic.outbound.sync;

import java.nio.charset.StandardCharsets;
import java.util.*;

final class BarcodeCursor {
    private BarcodeCursor() { }
    static long parse(String value, Namespace namespace) {
        try {
            if (value.length()>256) throw new IllegalArgumentException();
            var parts = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.US_ASCII).split("\\|", -1);
            if (parts.length!=5 || !parts[0].equals("1") || !parts[1].equals(namespace.epoch())
                || !parts[2].equals(namespace.warehouse().toString()) || !parts[3].equals("BARCODE")
                || !parts[4].matches("0|[1-9][0-9]*")) throw new IllegalArgumentException();
            return Long.parseLong(parts[4]);
        } catch (IllegalArgumentException error) { throw new IllegalArgumentException("条码同步游标不属于当前仓库及恢复代次"); }
    }
    static String encode(Namespace namespace, long sequence) {
        String raw = "1|"+namespace.epoch()+"|"+namespace.warehouse()+"|BARCODE|"+sequence;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.US_ASCII));
    }
    record Namespace(String epoch, UUID warehouse) { }
}
