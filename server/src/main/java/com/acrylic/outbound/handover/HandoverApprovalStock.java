package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.continuation.*;
import com.acrylic.outbound.devices.DeviceCrypto;
import com.acrylic.outbound.inventory.master.WarehouseBaseline;
import com.acrylic.outbound.pda.AssignmentStore;
import com.acrylic.outbound.recall.RecallStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.continuation.ContinuationChecks.conflict;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Locks and rechecks existing stock and approved physical decisions; it never releases or transfers ownership. */
@Component
final class HandoverApprovalStock {
    private final JdbcTemplate jdbc;
    private final AssignmentStore assignments;
    private final RecallStore recalls;
    private final WarehouseBaseline warehouses;
    private final DispositionEvidence dispositions;
    private final ContinuationChecks stock;

    HandoverApprovalStock(JdbcTemplate jdbc, AssignmentStore assignments, RecallStore recalls, WarehouseBaseline warehouses,
            DispositionEvidence dispositions, ContinuationChecks stock) {
        this.jdbc = jdbc; this.assignments = assignments; this.recalls = recalls; this.warehouses = warehouses;
        this.dispositions = dispositions; this.stock = stock;
    }

    void require(JsonNode facts, JsonNode body, JsonNode command) {
        var recall = recalls.get(uuid(body, "changeRequestId"));
        var pack = recalls.recovery(recall.id());
        conflict(parse(pack.issues()).isEmpty(), "RECOVERY_RECONCILIATION_REQUIRED", "恢复包存在冲突，须先对账");
        baseline(facts, body, recall);
        for (var line : array(command, "dispositions")) if (line.hasNonNull("approvedEvidenceId")) {
            var decision = dispositions.get(uuid(line, "approvedEvidenceId"));
            ((ObjectNode) line).put("action", decision.action());
        }
        dispositions.validate(recall, body, command);
        var proofs = DispositionProofs.compose(body, command);
        stock.stock(uuid(facts, "canonicalOrderId"), body, proofs);
        owners(facts, body);
    }

    private void baseline(JsonNode facts, JsonNode body, RecallStore.Head recall) {
        var assignment = assignments.get(uuid(body, "assignmentId"));
        var order = jdbc.queryForMap("SELECT status,current_revision FROM outbound_order WHERE id=?", Ids.bytes(recall.order()));
        conflict("RECALL_PENDING".equals(order.get("status")) && "REVOKED".equals(assignment.status())
            && ((Number) order.get("current_revision")).intValue() == recall.revision()
            && assignment.revision() == recall.revision(), "HANDOVER_CONTEXT_CHANGED", "原订单计划或资格已变化");
        var publication = assignments.publication(recall.order(), recall.revision());
        var baseline = warehouses.read(uuid(facts, "warehouseId"), true);
        conflict(publication.snapshotId().equals(baseline.activeSnapshotId()) && publication.mappingRevision().equals(baseline.mappingRevision())
            && assignment.inventorySnapshotId().equals(publication.snapshotId()) && assignment.planSha256().equals(DeviceCrypto.sha(publication.snapshotJson()))
            && assignment.inventorySha256().equals(publication.inventorySha()) && publication.recoveryEpoch().equals(recall.epoch()),
            "ORDER_BASELINE_CHANGED", "交接计划、库存或映射已变化，请重新核对");
    }

    private void owners(JsonNode facts, JsonNode body) {
        for (var scan : DispositionProofs.effective(body)) {
            var rows = jdbc.queryForList("SELECT state,BIN_TO_UUID(submission_id) submission_id,"
                + "BIN_TO_UUID(recovery_package_id) recovery_id,BIN_TO_UUID(continuation_id) continuation_id "
                + "FROM bale_commitment WHERE bale_id=? FOR UPDATE", Ids.bytes(uuid(scan, "baleId")));
            conflict(rows.size() == 1, "HANDOVER_RESERVATION_CHANGED", "原包占用已变化");
            var owner = rows.getFirst();
            boolean own = switch (owner.get("state").toString()) {
                case "REVIEW_RESERVED" -> text(facts, "submissionId").equals(owner.get("submission_id"));
                case "RECOVERY_RESERVED" -> text(body, "recoveryPackageId").equals(owner.get("recovery_id"));
                case "CONTINUATION_RESERVED" -> body.hasNonNull("continuationId") && text(body, "continuationId").equals(owner.get("continuation_id"));
                default -> false;
            };
            conflict(own, "HANDOVER_RESERVATION_CHANGED", "原包占用不属于本次交接来源");
        }
    }
}
