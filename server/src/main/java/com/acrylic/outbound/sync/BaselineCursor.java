package com.acrylic.outbound.sync;

import java.nio.charset.StandardCharsets;
import java.util.*;

final class BaselineCursor {
    private BaselineCursor() { }
    static UUID parse(String value, UUID baseline) {
        if (value == null || value.isBlank()) return null;
        try {
            if (value.length() > 160) throw new IllegalArgumentException();
            var parts = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.US_ASCII).split("\\|", -1);
            if (parts.length != 2 || !parts[0].equals(baseline.toString())) throw new IllegalArgumentException();
            return UUID.fromString(parts[1]);
        } catch (IllegalArgumentException error) { throw new IllegalArgumentException("基线游标无效或不属于当前基线"); }
    }
    static String encode(UUID baseline, UUID order) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
            (baseline + "|" + order).getBytes(StandardCharsets.US_ASCII));
    }
}
