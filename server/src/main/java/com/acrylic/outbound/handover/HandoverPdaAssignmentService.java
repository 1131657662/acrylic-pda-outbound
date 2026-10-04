package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.pda.AssignmentStore;
import com.acrylic.outbound.pda.PdaAccess;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import static com.acrylic.outbound.handover.HandoverCommand.corruptUnless;

/** Reads the new target credential only after the complete signed handover chain is verified. */
@Service
@ConditionalOnProperty(prefix="outbound.handover.commands", name="enabled", havingValue="true")
@Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
public class HandoverPdaAssignmentService {
    private final HandoverPdaEvidenceService evidence;
    private final AssignmentStore assignments;
    private final PdaAccess access;

    HandoverPdaAssignmentService(HandoverPdaEvidenceService evidence, AssignmentStore assignments,
            PdaAccess access) {
        this.evidence = evidence;
        this.assignments = assignments;
        this.access = access;
    }

    public AssignmentStore.View assignment(UUID caseId) {
        var manifest = evidence.manifest(caseId);
        if (!"ACTIVATED".equals(manifest.path("status").asText())) throw HandoverCommand.missing();
        var decision = manifest.path("activationReceipt").path("businessDecision");
        var source = manifest.path("stopReceipt").path("source");
        UUID assignmentId = SubmissionJson.uuid(decision, "assignmentId");
        AssignmentStore.View target;
        try { target = assignments.get(assignmentId); }
        catch (EmptyResultDataAccessException missing) { throw HandoverCommand.corrupt(); }
        var principal = access.require("pda:scan");
        access.require("pda:submit");
        corruptUnless(target.id().equals(assignmentId.toString()) && !target.id().equals(source.path("assignmentId").asText())
            && target.orderId().equals(source.path("orderId").asText())
            && target.deviceId().equals(principal.deviceId().toString())
            && target.operatorId().equals(principal.userId().toString())
            && target.deviceId().equals(decision.path("targetDeviceId").asText())
            && target.operatorId().equals(decision.path("targetUserId").asText())
            && target.epoch().equals(decision.path("assignmentEpoch").asText())
            && target.startOrderScanSeq().equals(decision.path("startOrderScanSeq").asText())
            && target.offlineToken() != null && !target.offlineToken().isBlank());
        var active = assignments.active(UUID.fromString(target.orderId()));
        if (active == null || !active.id().equals(target.id()) || !"ACTIVE".equals(target.status())
                || !assignments.credentialActive(assignmentId))
            throw new BusinessException(409, "HANDOVER_ASSIGNMENT_INACTIVE", "交接目标授权已失效，请保留资料并核对");
        return target;
    }
}
