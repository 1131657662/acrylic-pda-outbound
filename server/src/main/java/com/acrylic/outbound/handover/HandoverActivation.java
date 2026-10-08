package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.devices.DeviceCrypto;
import com.acrylic.outbound.inventory.master.WarehouseBaseline;
import com.acrylic.outbound.pda.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Issues the target qualification on the command transaction; original package commitments remain held. */
@Component
public class HandoverActivation {
    private final AssignmentStore assignments;
    private final OfflineSigner signer;
    private final JdbcTemplate jdbc;
    private final WarehouseBaseline warehouses;
    private final HandoverTargetQualification targets;

    public HandoverActivation(AssignmentStore assignments, OfflineSigner signer, JdbcTemplate jdbc,
            WarehouseBaseline warehouses, HandoverTargetQualification targets) {
        this.assignments = assignments;
        this.signer = signer;
        this.jdbc = jdbc;
        this.warehouses = warehouses;
        this.targets = targets;
    }

    public int terminateActive(UUID warehouse, UUID order) {
        return jdbc.update("UPDATE order_assignment SET status='REVOKED' WHERE order_id=? AND warehouse_id=? AND status IN ('ACTIVE','RECALL_PENDING')",
            Ids.bytes(order), Ids.bytes(warehouse));
    }

    public void advanceRounds(UUID order, UUID scope, int fromRound, int toRound) {
        int slot = jdbc.update("UPDATE order_submission_slot SET work_round=? WHERE order_id=? AND scope_id=? AND work_round=?",
            (long) toRound, Ids.bytes(order), Ids.bytes(scope), (long) fromRound);
        if (slot != 1) throw new BusinessException(409, "HANDOVER_ROUND_CHANGED", "来源活动轮次已变化，不能激活交接");
        int scopeRows = jdbc.update("UPDATE submission_scope SET work_round=? WHERE id=? AND work_round=?",
            toRound, Ids.bytes(scope), (long) fromRound);
        if (scopeRows != 1) throw new BusinessException(409, "HANDOVER_ROUND_CHANGED", "来源轮次已变化，不能激活交接");
    }

    public long retainedReservations(UUID submission) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE submission_id=?",
            Long.class, Ids.bytes(submission));
    }

    /** Retained for the legacy recovery-epoch guard probe; a valid issuance requires a verified high watermark. */
    @Deprecated
    public Activated activate(UUID warehouse, UUID order, UUID targetDevice, UUID targetUser,
            String recoveryEpoch, String areaNote) {
        return activate(warehouse, order, targetDevice, targetUser, recoveryEpoch, areaNote, -1);
    }

    public Activated activate(UUID warehouse, UUID order, UUID targetDevice, UUID targetUser,
            String recoveryEpoch, String areaNote, long inputHighWatermark) {
        var target = targets.require(targetDevice, targetUser, warehouse);
        int revision = jdbc.queryForObject("SELECT current_revision FROM outbound_order WHERE id=? AND warehouse_id=?",
            Integer.class, Ids.bytes(order), Ids.bytes(warehouse));
        var publication = assignments.publication(order, revision);
        if (!publication.recoveryEpoch().equals(recoveryEpoch))
            throw new BusinessException(409, "ORDER_RECOVERY_REQUIRED", "发布版本来自旧恢复代次，需先对账");
        var baseline = warehouses.read(warehouse, false);
        if (!publication.snapshotId().equals(baseline.activeSnapshotId())
                || !publication.mappingRevision().equals(baseline.mappingRevision()))
            throw new BusinessException(409, "ORDER_BASELINE_CHANGED", "库存或映射已变化，需后台核对并重新发布后激活");
        long startSeq = nextScanSeq(inputHighWatermark);
        var principal = new PdaSessions.Principal(null, targetUser, target.username(), targetDevice, warehouse,
            target.authVersion(), target.deviceVersion());
        var issued = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        var work = new AssignmentStore.Work(Ids.next(), Ids.next(), order, revision, assignments.nextEpoch(order),
            principal, DeviceCrypto.sha(publication.snapshotJson()), areaNote, issued, issued.plusSeconds(48 * 3600));
        var signed = signer.sign(new Claims("acrylic-outbound", "acrylic-pda-offline", work.authId().toString(),
            targetUser.toString(), targetDevice.toString(), warehouse.toString(), work.id().toString(),
            order.toString(), revision, Long.toString(work.epoch()), recoveryEpoch, publication.snapshotId(),
            publication.policyVersion(), publication.autoDeduct(), work.planSha(), publication.inventorySha(),
            Long.toString(startSeq), "0", target.authVersion(), target.deviceVersion(), "pda:handover-import",
            issued.getEpochSecond(), work.expires().getEpochSecond()));
        assignments.insert(work, publication, signed);
        if (jdbc.update("UPDATE order_assignment SET start_order_scan_seq=? WHERE id=?",
                startSeq, Ids.bytes(work.id())) != 1)
            throw new BusinessException(409, "HANDOVER_ASSIGNMENT_CHANGED", "目标授权未能保存，请重新核对");
        return new Activated(work.id(), work.epoch(), work.expires(), principal.sessionId(), startSeq);
    }

    private long nextScanSeq(long high) {
        if (high < 0) throw new BusinessException(409, "HANDOVER_HIGH_WATERMARK_INVALID", "原输入高水位无效");
        try { return Math.addExact(high, 1); }
        catch (ArithmeticException overflow) {
            throw new BusinessException(409, "HANDOVER_HIGH_WATERMARK_INVALID", "原输入高水位超出可签发范围");
        }
    }

    public record Activated(UUID assignmentId, long epoch, Instant expires, UUID sessionId, long startOrderScanSeq) { }
    private record Claims(String iss, String aud, String jti, String sub, String deviceId, String warehouseId,
        String assignmentId, String orderId, int revision, String assignmentEpoch, String serverRecoveryEpoch,
        String inventorySnapshotId, String policyVersion, boolean autoDeductInventory, String planSha256,
        String inventorySha256, String startOrderScanSeq, String allocationVersion, String userAuthVersion,
        String deviceVersion, String scope, long iat, long exp) { }

    static final class Activate implements HandoverCommand.Operation {
        private final HandoverStore store;
        private final HandoverActivation activation;
        private final HandoverTemporaryActivationOperation temporary;
        Activate(HandoverStore store, HandoverActivation activation, HandoverTemporaryActivationOperation temporary) {
            this.store = store;
            this.activation = activation;
            this.temporary = temporary;
        }
        @Override public String commandType() { return "ACTIVATE"; }
        @Override public JsonNode execute(HandoverCommand.Write write) {
            var input = write.command();
            var facts = write.caseFacts();
            if ("TEMP".equals(facts.path("sourceKind").asText())) return temporary.execute(write);
            if (!facts.path("status").asText().equals("TARGET_PREPARED"))
                throw new BusinessException(409, "HANDOVER_STATE_INVALID", "只有已准备接收机的案件可以激活");
            UUID warehouse = SubmissionJson.uuid(facts, "warehouseId");
            UUID order = SubmissionJson.uuid(facts, "canonicalOrderId");
            UUID scope = SubmissionJson.uuid(facts, "scopeId");
            UUID device = SubmissionJson.uuid(facts, "targetDeviceId");
            UUID user = SubmissionJson.uuid(facts, "targetUserId");
            if (!device.equals(input.uuid("targetDeviceId")) || !user.equals(input.uuid("targetUserId")))
                throw new BusinessException(409, "HANDOVER_CONTEXT_CHANGED", "激活目标与准备票据不一致");
            long before = activation.retainedReservations(SubmissionJson.uuid(facts, "submissionId"));
            activation.terminateActive(warehouse, order);
            var activated = activation.activate(warehouse, order, device, user,
                input.text("transportRecoveryEpoch"), "交接激活 " + write.caseId(),
                SubmissionJson.number(facts, "inputHighWatermark"));
            long after = activation.retainedReservations(SubmissionJson.uuid(facts, "submissionId"));
            if (before != after)
                throw new BusinessException(409, "HANDOVER_RESERVATION_RELEASED", "激活不得释放保留占用");
            activation.advanceRounds(order, scope, Integer.parseInt(facts.path("fromWorkRound").asText()),
                Integer.parseInt(facts.path("toWorkRound").asText()));
            var decision = SubmissionJson.object().put("caseId", write.caseId().toString())
                .put("assignmentId", activated.assignmentId().toString())
                .put("assignmentEpoch", Long.toString(activated.epoch()))
                .put("expiresAt", activated.expires().toString())
                .put("targetDeviceId", device.toString()).put("targetUserId", user.toString())
                .put("workRound", facts.path("toWorkRound").asText())
                .put("startOrderScanSeq", Long.toString(activated.startOrderScanSeq()));
            byte[] receipt = HandoverReceiptCodec.bytes(decision.deepCopy()
                .put("activatedAt", write.recordedAt().toString()));
            store.advanceCase(write.caseId(), write.caseVersion(), "ACTIVATED", null, null, null,
                null, null, null, null, null, null, receipt,
                SubmissionJson.uuid(write.actor(), "principalId"), write.recordedAt());
            long epoch = store.serverRecoveryEpoch();
            var payload = decision.deepCopy();
            payload.remove("expiresAt");
            payload.put("orderId", order.toString()).put("status", "ASSIGNED")
                .put("activatedAt", write.recordedAt().toString());
            store.insertOutbox(order, epoch, store.nextOutboxEntityVersion(order, epoch),
                "HANDOVER_ACTIVATED", SubmissionJson.canonical(payload), write.recordedAt());
            return decision.put("status", "ACTIVATED").put("retainedReservations", Long.toString(after));
        }

    }
}
