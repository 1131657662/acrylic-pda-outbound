package com.acrylic.outbound.temporary.command;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.*;

public final class CommandRecord {
    private final Map<String,Object> row;
    CommandRecord(Map<String,Object> row) { this.row=Collections.unmodifiableMap(new HashMap<>(row)); }
    public String text(String key) { var value=row.get(key);return value==null?null:value.toString(); }
    public byte[] bytes(String key) { var value=(byte[])row.get(key);return value==null?null:value.clone(); }
    public UUID id(String key) {
        byte[] value=bytes(key);if(value==null)return null;
        var buffer=ByteBuffer.wrap(value);return new UUID(buffer.getLong(),buffer.getLong());
    }
    public Instant recordedAt() { return instant(row.get("recorded_at")); }
    static Instant instant(Object value) {
        if(value instanceof java.sql.Timestamp timestamp)return timestamp.toInstant();
        if(value instanceof java.time.LocalDateTime datetime)return datetime.toInstant(java.time.ZoneOffset.UTC);
        throw CommandErrors.corrupt();
    }
}
