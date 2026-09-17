package com.acrylic.outbound.orders;

import com.acrylic.outbound.common.BusinessException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

record OrderCursor(Timestamp createdAt,UUID id) {
    static OrderCursor parse(String value) {
        try {
            if(value.length()>160) throw new IllegalArgumentException();
            String[] parts=new String(Base64.getUrlDecoder().decode(value),StandardCharsets.US_ASCII).split("\\|",-1);
            if(parts.length!=2) throw new IllegalArgumentException();
            return new OrderCursor(Timestamp.from(Instant.parse(parts[0])),UUID.fromString(parts[1]));
        } catch(IllegalArgumentException | java.time.DateTimeException e) {
            throw new BusinessException(400,"INVALID_CURSOR","订单分页游标无效");
        }
    }
    static String encode(OrderViews.Summary row) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString((row.createdAt()+"|"+row.id()).getBytes(StandardCharsets.US_ASCII));
    }
}
