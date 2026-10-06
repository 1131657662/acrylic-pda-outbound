package com.acrylic.outbound.temporary.continuation;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.*;
import java.util.Comparator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.temporary.continuation.TemporaryRoundFacts.conflict;

/** Locks every accepted bale in order before transferring only the exact ticket's retained owners. */
@Component
@Transactional(propagation=Propagation.MANDATORY)
public class TemporaryRoundStock {
    private final JdbcTemplate jdbc;
    public TemporaryRoundStock(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public void transfer(TemporaryRoundPayload.Input input,SubmissionSnapshot.Validated value) {
        var head=input.head();var source=input.source();
        if(head.order()==null)throw conflict("TEMP_CONTINUATION_TARGET_CHANGED");
        for(var scan:value.scans().stream().sorted(Comparator.comparing(s->s.bale().id().toString())).toList()) {
            jdbc.queryForList("SELECT id FROM bale WHERE id=? FOR UPDATE",Ids.bytes(scan.bale().id()));
            jdbc.queryForList("SELECT bale_id FROM inventory_position WHERE bale_id=? FOR UPDATE",Ids.bytes(scan.bale().id()));
            jdbc.queryForList("SELECT bale_id FROM bale_commitment WHERE bale_id=? FOR UPDATE",Ids.bytes(scan.bale().id()));
        }
        for(var item:input.retained()) {
            int moved=jdbc.update("""
                UPDATE bale_commitment k JOIN bale b ON b.id=k.bale_id
                SET k.state='REVIEW_RESERVED',k.submission_id=?,k.order_id=?,k.temporary_continuation_id=NULL,k.source_evidence_set_id=NULL
                WHERE k.bale_id=? AND k.dispatch_cycle=? AND b.dispatch_cycle=k.dispatch_cycle
                  AND k.source_scope_id=? AND k.source_warehouse_id=? AND k.source_evidence_set_id=?
                  AND k.temporary_continuation_id=? AND k.state='TEMP_CONTINUATION_RESERVED'
                  AND k.submission_id IS NULL AND k.order_id IS NULL
                """,Ids.bytes(head.control().id()),Ids.bytes(head.order()),Ids.bytes(item.baleId()),item.cycle(),
                Ids.bytes(source.scope()),Ids.bytes(source.warehouse()),Ids.bytes(source.evidenceSet()),Ids.bytes(input.request().predecessor().ticket()));
            if(moved!=1)throw conflict("CONTINUATION_OWNER_CHANGED");
        }
    }
}
