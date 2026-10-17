package com.acrylic.outbound.common;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class IdempotencyRequests {
    private final JdbcTemplate jdbc;
    public IdempotencyRequests(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public UUID find(Key key, String digest) {
        if (!key.value().matches("[A-Za-z0-9._:-]{16,80}"))
            throw new BusinessException(400, "INVALID_IDEMPOTENCY_KEY", "幂等键须为16至80位ASCII标识");
        var found = jdbc.query("""
            SELECT request_sha256,BIN_TO_UUID(resource_id) AS resource_id FROM api_idempotency_request
            WHERE principal_id=? AND route_scope=? AND idempotency_key=?
            """, (rs, row) -> new Stored(rs.getString("request_sha256"), rs.getString("resource_id")),
            Ids.bytes(key.actor()), key.scope(), key.value());
        if (found.isEmpty()) return null;
        if (!found.getFirst().digest().equals(digest))
            throw new BusinessException(409, "IDEMPOTENCY_CONTENT_MISMATCH", "同一幂等键对应了不同请求内容");
        return UUID.fromString(found.getFirst().resource());
    }
    public void record(Key key, String digest, UUID resource) {
        jdbc.update("""
            INSERT INTO api_idempotency_request(id,principal_id,route_scope,idempotency_key,request_sha256,resource_id,created_at)
            VALUES(?,?,?,?,?,?,UTC_TIMESTAMP(3))
            """, Ids.bytes(Ids.next()), Ids.bytes(key.actor()), key.scope(), key.value(), digest, Ids.bytes(resource));
    }
    public record Key(UUID actor, String scope, String value) { }
    private record Stored(String digest, String resource) { }
}
