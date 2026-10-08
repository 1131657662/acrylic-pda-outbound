package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.*;
import java.util.Comparator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

/** Never releases retained stock while moving its owner to the new accepted submission. */
@Component
@Transactional(propagation=Propagation.MANDATORY)
class TemporaryHandoverSubmissionOwners {
    private final JdbcTemplate jdbc;private final TemporaryHandoverOwnerProof proofs;
    TemporaryHandoverSubmissionOwners(JdbcTemplate jdbc,TemporaryHandoverOwnerProof proofs) { this.jdbc=jdbc;this.proofs=proofs; }
    void transfer(TemporaryHandoverIntakeFacts.Facts facts,SubmissionStore.Head head,SubmissionSnapshot.Validated snapshot) {
        var t=facts.ticket();lock(snapshot);
        for(var owner:proofs.read(t)) {
            if(snapshot.scans().stream().noneMatch(s->s.bale().id().equals(owner.baleId()) && s.cycle()==owner.dispatchCycle()))
                throw TemporaryHandoverIntakeFacts.changed();
            int changed=jdbc.update("""
                UPDATE bale_commitment k JOIN bale b ON b.id=k.bale_id AND b.dispatch_cycle=k.dispatch_cycle
                SET k.state='REVIEW_RESERVED',k.submission_id=?,k.order_id=?,k.temporary_handover_activation_id=NULL,k.source_evidence_set_id=NULL
                WHERE k.bale_id=? AND k.dispatch_cycle=? AND k.source_scope_id=? AND k.source_warehouse_id=?
                  AND k.source_evidence_set_id=? AND k.temporary_handover_activation_id=? AND k.state='TEMP_HANDOVER_RESERVED'
                  AND k.submission_id IS NULL AND k.order_id IS NULL
                """,Ids.bytes(head.control().id()),Ids.bytes(head.order()),Ids.bytes(owner.baleId()),owner.dispatchCycle(),
                Ids.bytes(t.scopeId()),Ids.bytes(facts.warehouse()),Ids.bytes(t.sourceEvidenceSetId()),Ids.bytes(t.id()));
            if(changed!=1)throw TemporaryHandoverIntakeFacts.changed();
        }
    }
    private void lock(SubmissionSnapshot.Validated snapshot) {
        for(var scan:snapshot.scans().stream().sorted(Comparator.comparing(s->s.bale().id().toString())).toList()) {
            jdbc.queryForList("SELECT id FROM bale WHERE id=? FOR UPDATE",Ids.bytes(scan.bale().id()));
            jdbc.queryForList("SELECT bale_id FROM inventory_position WHERE bale_id=? FOR UPDATE",Ids.bytes(scan.bale().id()));
            jdbc.queryForList("SELECT bale_id FROM bale_commitment WHERE bale_id=? FOR UPDATE",Ids.bytes(scan.bale().id()));
        }
    }
    void require(TemporaryHandoverIntakeFacts.Facts facts,SubmissionStore.Head head,SubmissionSnapshot.Validated snapshot) {
        lock(snapshot);var t=facts.ticket();
        for(var owner:proofs.read(t)) {
            int count=jdbc.queryForObject("""
                SELECT COUNT(*) FROM bale_commitment k JOIN bale b ON b.id=k.bale_id AND b.dispatch_cycle=k.dispatch_cycle
                WHERE k.bale_id=? AND k.dispatch_cycle=? AND k.submission_id=? AND k.order_id=?
                  AND k.source_scope_id=? AND k.source_warehouse_id=? AND k.state='REVIEW_RESERVED'
                  AND k.temporary_handover_activation_id IS NULL AND k.source_evidence_set_id IS NULL
                """,Integer.class,Ids.bytes(owner.baleId()),owner.dispatchCycle(),Ids.bytes(head.control().id()),Ids.bytes(head.order()),
                Ids.bytes(t.scopeId()),Ids.bytes(facts.warehouse()));
            if(count!=1)throw TemporaryHandoverIntakeFacts.changed();
        }
    }
}
