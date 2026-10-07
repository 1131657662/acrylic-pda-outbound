package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.inventory.master.WarehouseBaseline;
import com.acrylic.outbound.temporary.command.PhysicalDispositionVerification;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Locks TEMP source reservations and their original baseline; approval keeps all owners and balances unchanged. */
@Component
final class HandoverTemporaryApprovalStock {
    private final PhysicalDispositionVerification verification;
    private final WarehouseBaseline warehouses;
    private final JdbcTemplate jdbc;

    HandoverTemporaryApprovalStock(PhysicalDispositionVerification verification, WarehouseBaseline warehouses, JdbcTemplate jdbc) {
        this.verification = verification; this.warehouses = warehouses; this.jdbc = jdbc;
    }

    void require(JsonNode facts, HandoverApprovalEvidence.Verified evidence, JsonNode dispositions) {
        for (var line : array(dispositions, "dispositions")) if (!"RETAIN".equals(line.path("action").asText()))
            throw failure("HANDOVER_TEMP_REDUCTION_NOT_READY", "临时来源减包须先完成对应实物处置，不能由交接审批释放");
        var stop = evidence.receipt().path("temporaryStop");
        var proof = verification.readForContinuation(uuid(facts, "warehouseId"), uuid(facts, "scopeId"),
            uuid(facts, "submissionId"), number(facts, "fromWorkRound"), uuid(stop, "sourceEvidenceSetId"),
            uuid(stop, "verificationId"), text(stop, "setSha256"), 1, 1);
        baseline(facts, evidence.body(), uuid(stop, "authorizationId"));
        var competing = jdbc.queryForList("SELECT id FROM temporary_source_continuation WHERE scope_id=? AND from_work_round=? FOR UPDATE",
            Ids.bytes(uuid(facts, "scopeId")), number(facts, "fromWorkRound"));
        if (!competing.isEmpty()) throw failure("HANDOVER_CONTINUATION_CONFLICT", "原轮次已有续作票据，请先核对原作业");
        for (var bale : proof.activeBales()) stock(bale);
    }

    private void baseline(JsonNode facts, JsonNode body, UUID authorization) {
        var baseline = warehouses.read(uuid(facts, "warehouseId"), true);
        var row = jdbc.queryForMap("SELECT BIN_TO_UUID(inventory_snapshot_id) snapshot,CAST(mapping_revision AS CHAR) mapping "
            + "FROM temporary_authorization WHERE id=? AND warehouse_id=?", Ids.bytes(authorization), Ids.bytes(uuid(facts, "warehouseId")));
        if (!text(body, "inventorySnapshotId").equals(baseline.activeSnapshotId())
                || !row.get("snapshot").equals(baseline.activeSnapshotId()) || !row.get("mapping").equals(baseline.mappingRevision()))
            throw failure("ORDER_BASELINE_CHANGED", "临时来源库存或映射已变化，请重新核对");
    }

    private void stock(UUID bale) {
        var rows = jdbc.queryForList("SELECT upstream_present,book_status,blocked_reason FROM inventory_position WHERE bale_id=? FOR UPDATE",
            Ids.bytes(bale));
        if (rows.size() != 1) throw failure("HANDOVER_RESERVATION_CHANGED", "原包库存记录不可核验");
        var row = rows.getFirst(); Object present = row.get("upstream_present");
        boolean available = Boolean.TRUE.equals(present) || present instanceof Number n && n.intValue() == 1;
        if (!available || !"NOT_DEDUCTED".equals(row.get("book_status")) || row.get("blocked_reason") != null)
            throw failure("HANDOVER_STOCK_CONFLICT", "原包存在独立库存冲突，交接审批不能清除");
    }

    private BusinessException failure(String code, String message) { return new BusinessException(409, code, message); }
}
