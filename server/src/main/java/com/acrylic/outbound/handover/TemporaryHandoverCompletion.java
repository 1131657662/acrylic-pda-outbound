package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.temporary.command.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Component
@Transactional(propagation=Propagation.MANDATORY)
public class TemporaryHandoverCompletion {
    private final JdbcTemplate jdbc;private final TemporaryHandoverIntakeFacts facts;
    TemporaryHandoverCompletion(JdbcTemplate jdbc,TemporaryHandoverIntakeFacts facts) { this.jdbc=jdbc;this.facts=facts; }
    public void complete(CommandWrite write,TemporaryConfirmationSource.Value value,JsonNode receipt) {
        var head=value.head();var request=TemporaryHandoverControl.read(value.body());
        equal(request.predecessor(),facts.reference(head.control().id()),"交接结算绑定不符");var source=facts.read(request);var t=source.ticket();
        require("SUCCEEDED".equals(text(receipt,"status")) && head.control().id().equals(uuid(receipt,"submissionId")),"交接结算回执不符");
        int count=jdbc.queryForObject("""
            SELECT COUNT(*) FROM temporary_handover_activation_owner r JOIN bale_commitment k ON k.bale_id=r.bale_id AND k.dispatch_cycle=r.dispatch_cycle
            WHERE r.ticket_id=? AND k.state='DISPATCHED' AND k.submission_id=? AND k.order_id=?
              AND k.temporary_handover_activation_id IS NULL AND k.source_evidence_set_id IS NULL
            """,Integer.class,Ids.bytes(t.id()),Ids.bytes(head.control().id()),Ids.bytes(head.order()));
        equal((long)count,number(source.activation(),"retainedCount"),"交接保留占用尚未结算");
        jdbc.update("""
            INSERT INTO temporary_handover_completion(ticket_id,submission_id,closure_receipt_id,receipt_text,completed_at)
            VALUES(?,?,?,?,?)
            """,Ids.bytes(t.id()),Ids.bytes(head.control().id()),Ids.bytes(write.receiptId()),canonical(receipt),java.sql.Timestamp.from(write.recordedAt()));
    }
}
