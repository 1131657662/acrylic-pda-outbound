package com.acrylic.outbound.temporary.review;

import com.acrylic.outbound.common.BusinessException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

record TemporaryReviewCursor(Timestamp time, UUID id) {
    static TemporaryReviewCursor parse(String value, UUID warehouse) {
        try {
            if (value.length() > 200) throw new IllegalArgumentException();
            var parts = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.US_ASCII).split("\\|", -1);
            if (parts.length != 3 || !parts[0].equals(warehouse.toString())) throw new IllegalArgumentException();
            var time = Instant.parse(parts[1]);
            if (time.isBefore(Instant.parse("1000-01-01T00:00:00Z")) || time.isAfter(Instant.parse("9999-12-31T23:59:59.999Z"))
                || time.getNano() % 1_000_000 != 0 || !UUID.fromString(parts[2]).toString().equals(parts[2])) throw new IllegalArgumentException();
            return new TemporaryReviewCursor(Timestamp.from(time), UUID.fromString(parts[2]));
        } catch (IllegalArgumentException | DateTimeException error) {
            throw new BusinessException(400, "INVALID_CURSOR", "临时待审分页位置无效，请返回首页");
        }
    }
    static String encode(UUID warehouse, TemporaryReviewQueries.Item row) {
        String value = warehouse + "|" + row.receivedAt() + "|" + row.submissionId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.US_ASCII));
    }
}
