package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.BusinessException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

/** Continuation position bound to the warehouse and order, never an authorization grant. */
record HandoverCaseCursor(UUID warehouse, UUID order, LocalDateTime requestedAt, UUID id) {
    static HandoverCaseCursor parse(String value, UUID warehouse, UUID order) {
        if (value == null) return null;
        if (value.isEmpty() || value.length() > 300) throw invalid();
        try {
            var parts = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8).split("\\|", -1);
            if (parts.length != 4) throw invalid();
            var cursor = new HandoverCaseCursor(UUID.fromString(parts[0]), UUID.fromString(parts[1]),
                LocalDateTime.parse(parts[2]), UUID.fromString(parts[3]));
            if (!cursor.warehouse.equals(warehouse) || !cursor.order.equals(order) || !cursor.encode().equals(value)) throw invalid();
            return cursor;
        } catch (IllegalArgumentException | java.time.DateTimeException error) { throw invalid(); }
    }

    String encode() {
        String value = warehouse + "|" + order + "|" + requestedAt + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    static BusinessException invalid() {
        return new BusinessException(400, "INVALID_HANDOVER_PAGE", "交接查询位置无效，请返回首页重新查询");
    }
}
