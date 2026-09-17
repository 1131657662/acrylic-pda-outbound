package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.BusinessException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

record ImportCursor(Instant createdAt, UUID id) {
    static ImportCursor decode(String cursor) {
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.US_ASCII).split("\\|");
            if (parts.length != 2) throw new IllegalArgumentException();
            return new ImportCursor(Instant.ofEpochMilli(Long.parseLong(parts[0])), UUID.fromString(parts[1]));
        } catch (RuntimeException error) {
            throw new BusinessException(400, "INVALID_CURSOR", "分页游标无效，请返回第一页");
        }
    }
    static String encode(ImportSummary item) {
        String value = item.createdAt().toEpochMilli() + "|" + item.id();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.US_ASCII));
    }
}
