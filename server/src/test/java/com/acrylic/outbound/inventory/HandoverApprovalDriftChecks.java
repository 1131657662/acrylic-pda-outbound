package com.acrylic.outbound.inventory;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;

/** Real database drift fixtures are restored after refusal; each refused command leaves all business tables intact. */
final class HandoverApprovalDriftChecks {
    private final HandoverApprovalWorkflowChecks checks;
    HandoverApprovalDriftChecks(HandoverApprovalWorkflowChecks checks) { this.checks = checks; }
    void verify(ObjectNode command) throws Exception {
        var f = checks.f; var bale = f.r.body.path("scans").get(0).path("baleId").asText();
        f.jdbc.update("UPDATE bale SET dispatch_cycle=dispatch_cycle+1 WHERE id=UUID_TO_BIN(?)", bale);
        try { checks.refuse(command, "DISPATCH_CYCLE_CONFLICT"); }
        finally { f.jdbc.update("UPDATE bale SET dispatch_cycle=dispatch_cycle-1 WHERE id=UUID_TO_BIN(?)", bale); }
        f.jdbc.update("UPDATE warehouse SET mapping_revision=mapping_revision+1 WHERE id=?", com.acrylic.outbound.common.Ids.bytes(f.r.f.web.identity().warehouse()));
        try { checks.refuse(command, "ORDER_BASELINE_CHANGED"); }
        finally { f.jdbc.update("UPDATE warehouse SET mapping_revision=mapping_revision-1 WHERE id=?", com.acrylic.outbound.common.Ids.bytes(f.r.f.web.identity().warehouse())); }
        f.jdbc.update("UPDATE inventory_position SET blocked_reason='approval test conflict' WHERE bale_id=UUID_TO_BIN(?)", bale);
        try { checks.refuse(command, "INVENTORY_UNAVAILABLE"); }
        finally { f.jdbc.update("UPDATE inventory_position SET blocked_reason=NULL WHERE bale_id=UUID_TO_BIN(?)", bale); }
    }
}
