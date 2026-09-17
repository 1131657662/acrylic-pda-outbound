package com.acrylic.outbound.audit;

import com.acrylic.outbound.common.Ids;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AuditWriter {
    private final JdbcTemplate jdbc;
    public AuditWriter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void append(Event event, String requestId) {
        jdbc.update("""
            INSERT INTO operation_event(actor_id,event_type,entity_type,entity_id,details,server_time,request_id)
            VALUES(?,?,?,?,?,UTC_TIMESTAMP(3),?)
            """, event.actor() == null ? null : Ids.bytes(event.actor()), event.type(), event.entityType(),
            event.entity() == null ? null : Ids.bytes(event.entity()), event.json(), requestId);
    }
    public record Event(UUID actor, String type, String entityType, UUID entity, String json) { }
}
