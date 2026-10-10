package com.acrylic.outbound.handover;

import com.acrylic.outbound.auth.SessionUser;
import com.acrylic.outbound.common.BusinessException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** Rejected approvals and final SQL failures must leave the entire restored database unchanged. */
final class HandoverRecoveredPhysicalRefusals {
    static void verify(HandoverRecoveredTemporaryFixture f,HandoverRecoveredPhysicalReview service,
            HandoverRecoveredPhysicalReview.Request context,ObjectNode command) throws Exception {
        refused(f,service,context,new Attempt(f.requester,command,"HANDOVER_SELF_APPROVAL_DENIED"));
        refused(f,service,context,new Attempt(f.unprivileged,command,"PERMISSION_DENIED"));
        refused(f,service,context,new Attempt(f.supervisor,command.deepCopy().put("physicalState","UNCHECKED"),"HANDOVER_PHYSICAL_MISMATCH"));
        var missing=command.deepCopy(); missing.putArray("checklist");
        refused(f,service,context,new Attempt(f.supervisor,missing,"HANDOVER_CHECKLIST_MISMATCH"));
        refused(f,service,context,new Attempt(f.supervisor,command.deepCopy().put("transportRecoveryEpoch","1"),"HANDOVER_RECEIPT_INTEGRITY_FAILURE"));
        refused(f,service,context,new Attempt(f.supervisor,command.deepCopy().put("caseId",UUID.randomUUID().toString()),"HANDOVER_RECEIPT_INTEGRITY_FAILURE"));
        var before=f.snapshots.capture();
        f.jdbc.execute("CREATE TRIGGER received_approval_tail BEFORE INSERT ON operation_event FOR EACH ROW BEGIN "
            +"IF NEW.event_type='HANDOVER_APPROVE' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='RECEIVED_APPROVAL_TAIL'; END IF; END");
        try { assertThatThrownBy(()->HandoverRecoveredPhysicalChecks.execute(service,f.supervisor,command,context)).hasMessageContaining("RECEIVED_APPROVAL_TAIL"); }
        finally { f.jdbc.execute("DROP TRIGGER received_approval_tail"); }
        assertThat(f.snapshots.capture()).isEqualTo(before);
        unknownScope(f,service,context,command);
    }
    private static void unknownScope(HandoverRecoveredTemporaryFixture f,HandoverRecoveredPhysicalReview service,
            HandoverRecoveredPhysicalReview.Request context,ObjectNode command) throws Exception {
        var original=f.snapshots.capture();
        var other=f.jdbc.queryForMap("SELECT id,name FROM warehouse WHERE id<>? LIMIT 1",
            com.acrylic.outbound.common.Ids.bytes(f.binding.request().evidence().expected().warehouseId()));
        f.jdbc.update("UPDATE warehouse SET name=CONCAT(name,'-unknown') WHERE id=?",other.get("id"));
        var changed=f.snapshots.capture();
        try {
            assertThatThrownBy(()->HandoverRecoveredPhysicalChecks.execute(service,f.supervisor,command,context))
                .hasMessage("RECOVERY_SCOPE_ORIGINAL_CHANGED_warehouse");
            unchanged(changed,f.snapshots.capture());
        } finally { f.jdbc.update("UPDATE warehouse SET name=? WHERE id=?",other.get("name"),other.get("id")); }
        unchanged(original,f.snapshots.capture());
    }
    private static void unchanged(com.fasterxml.jackson.databind.JsonNode before,com.fasterxml.jackson.databind.JsonNode after) {
        var a=before.deepCopy(); var b=after.deepCopy();
        for(var value:java.util.List.of(a,b)) {
            var schema=(ObjectNode)value.path("schema");
            schema.put("operation_event",schema.path("operation_event").asText().replaceAll(" AUTO_INCREMENT=[0-9]+",""));
        }
        assertThat(b).isEqualTo(a);
    }
    private static void refused(HandoverRecoveredTemporaryFixture f,HandoverRecoveredPhysicalReview service,
            HandoverRecoveredPhysicalReview.Request context,Attempt attempt) throws Exception {
        var before=f.snapshots.capture();
        assertThatThrownBy(()->HandoverRecoveredPhysicalChecks.execute(service,attempt.actor(),attempt.command(),context))
            .isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.code()).isEqualTo(attempt.code()));
        assertThat(f.snapshots.capture()).isEqualTo(before);
    }
    private record Attempt(SessionUser actor,ObjectNode command,String code) { }
    private HandoverRecoveredPhysicalRefusals() { }
}
