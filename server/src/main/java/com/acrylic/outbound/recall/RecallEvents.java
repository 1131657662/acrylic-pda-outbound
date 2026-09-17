package com.acrylic.outbound.recall;

import com.acrylic.outbound.audit.AuditWriter;
import com.acrylic.outbound.common.Ids;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Component
public class RecallEvents {
    private final JdbcTemplate jdbc;private final AuditWriter audit;
    public RecallEvents(JdbcTemplate jdbc,AuditWriter audit) { this.jdbc=jdbc;this.audit=audit; }
    public void transition(RecallStore.Head head,UUID actor,String event,String status) {
        jdbc.update("UPDATE outbound_order SET status=?,row_version=row_version+1,updated_at=UTC_TIMESTAMP(3) WHERE id=?",status,Ids.bytes(head.order()));
        long version=jdbc.queryForObject("SELECT row_version FROM outbound_order WHERE id=?",Long.class,Ids.bytes(head.order()));
        String detail=canonical(object().put("changeRequestId",head.id().toString()).put("status",status));
        jdbc.update("INSERT INTO order_sync_outbox VALUES(?,?,?,?,?,?,?,NULL,UTC_TIMESTAMP(3))",Ids.bytes(Ids.next()),Long.parseLong(head.epoch()),
            Ids.bytes(head.warehouse()),Ids.bytes(head.order()),version,event,detail);
        audit.append(new AuditWriter.Event(actor,event,"ORDER",head.order(),detail),head.id().toString());
    }
}
