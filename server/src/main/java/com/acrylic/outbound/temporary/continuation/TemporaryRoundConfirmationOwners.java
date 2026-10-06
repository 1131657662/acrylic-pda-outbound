package com.acrylic.outbound.temporary.continuation;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionSnapshot;
import java.util.Comparator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

/** A lost retained owner can never be recreated by generic confirmation reservation. */
@Component
@Transactional(propagation=Propagation.MANDATORY)
public class TemporaryRoundConfirmationOwners {
    private final JdbcTemplate jdbc;
    public TemporaryRoundConfirmationOwners(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public void require(TemporaryRoundPayload.Input input,SubmissionSnapshot.Validated value) {
        for(var scan:value.scans().stream().sorted(Comparator.comparing(s->s.bale().id().toString())).toList()) {
            jdbc.queryForList("SELECT id FROM bale WHERE id=? FOR UPDATE",Ids.bytes(scan.bale().id()));
            jdbc.queryForList("SELECT bale_id FROM inventory_position WHERE bale_id=? FOR UPDATE",Ids.bytes(scan.bale().id()));
            jdbc.queryForList("SELECT bale_id FROM bale_commitment WHERE bale_id=? FOR UPDATE",Ids.bytes(scan.bale().id()));
        }
        var source=input.source();var head=input.head();
        for(var item:input.retained()) {
            int valid=jdbc.queryForObject("""
                SELECT COUNT(*) FROM bale_commitment k JOIN bale b ON b.id=k.bale_id AND b.dispatch_cycle=k.dispatch_cycle
                WHERE k.bale_id=? AND k.dispatch_cycle=? AND k.submission_id=? AND k.order_id=?
                  AND k.source_scope_id=? AND k.source_warehouse_id=? AND k.state='REVIEW_RESERVED'
                  AND k.temporary_continuation_id IS NULL AND k.source_evidence_set_id IS NULL
                """,Integer.class,Ids.bytes(item.baleId()),item.cycle(),Ids.bytes(head.control().id()),Ids.bytes(head.order()),
                Ids.bytes(source.scope()),Ids.bytes(source.warehouse()));
            if(valid!=1)throw TemporaryRoundFacts.conflict("CONTINUATION_OWNER_CHANGED");
        }
    }
}
