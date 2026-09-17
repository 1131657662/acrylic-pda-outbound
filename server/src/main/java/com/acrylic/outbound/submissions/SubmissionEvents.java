package com.acrylic.outbound.submissions;

import com.acrylic.outbound.audit.AuditWriter;
import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Component
public class SubmissionEvents {
    private final JdbcTemplate jdbc;private final AuditWriter audit;
    public SubmissionEvents(JdbcTemplate jdbc,AuditWriter audit) { this.jdbc=jdbc;this.audit=audit; }
    public void transition(SubmissionStore.Head head,String status,UUID actor) {
        var c=head.control();jdbc.update("UPDATE outbound_order SET status=?,row_version=row_version+1,updated_at=UTC_TIMESTAMP(3) WHERE id=?",status,Ids.bytes(c.order()));
        var detail=object().put("submissionId",c.id().toString()).put("status",status);
        long version=jdbc.queryForObject("SELECT row_version FROM outbound_order WHERE id=?",Long.class,Ids.bytes(c.order()));
        jdbc.update("INSERT INTO order_sync_outbox VALUES(?,?,?,?,?,?,?,NULL,UTC_TIMESTAMP(3))",Ids.bytes(Ids.next()),Long.parseLong(c.epoch()),
            Ids.bytes(head.warehouse()),Ids.bytes(c.order()),version,"SUBMISSION_"+status,canonical(detail));
        audit.append(new AuditWriter.Event(actor,"SUBMISSION_"+status,"ORDER",c.order(),canonical(detail)),c.id().toString());
    }
    public void evidence(SubmissionStore.Head head,JsonNode body) {
        var c=head.control();
        var captures=new java.util.ArrayList<JsonNode>();array(body,"captures").forEach(captures::add);
        jdbc.batchUpdate("INSERT INTO submitted_capture VALUES(?,?,?,?,?,?,?)",captures,200,(ps,row)->{
            ps.setBytes(1,Ids.bytes(uuid(row,"id")));ps.setBytes(2,Ids.bytes(c.id()));ps.setBytes(3,Ids.bytes(c.order()));
            ps.setBytes(4,Ids.bytes(uuid(row,"originDeviceId")));ps.setLong(5,number(row,"deviceCaptureSeq"));ps.setLong(6,number(row,"orderScanSeq"));ps.setString(7,sha(row));
        });
        var auditEvents=new java.util.ArrayList<JsonNode>();array(body,"events").forEach(auditEvents::add);
        jdbc.batchUpdate("INSERT INTO submitted_event VALUES(?,?,?,?,?)",auditEvents,200,(ps,row)->{
            ps.setBytes(1,Ids.bytes(uuid(row,"id")));ps.setBytes(2,Ids.bytes(c.id()));ps.setBytes(3,Ids.bytes(uuid(row,"originDeviceId")));
            ps.setLong(4,number(row,"localSeq"));ps.setString(5,sha(row));
        });
    }
}
