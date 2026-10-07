package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.temporary.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Binds a handover to the recipient's own usable TEMP credential without issuing an assignment. */
@Component
final class HandoverTemporaryTarget {
    private final HandoverTemporaryEligibility eligibility;
    private final HandoverTemporaryCredential credentials;
    private final TemporaryBaseline baselines;
    private final HandoverStore cases;

    HandoverTemporaryTarget(HandoverTemporaryEligibility eligibility, HandoverTemporaryCredential credentials,
            TemporaryBaseline baselines, HandoverStore cases) {
        this.eligibility = eligibility; this.credentials = credentials; this.baselines = baselines; this.cases = cases;
    }

    JsonNode require(JsonNode command, JsonNode facts, JsonNode body) {
        var row = eligibility.require(command, facts, Long.toString(cases.serverRecoveryEpoch()));
        if (!row.recoveryEpoch().equals(text(body, "sourceRecoveryEpoch")))
            throw new BusinessException(409, "TEMPORARY_RECOVERY_REQUIRED", "原来源来自旧恢复代次，请保留资料并对账");
        var manifest = credentials.verify(row);
        baseline(uuid(facts, "warehouseId"), manifest, body);
        var scope = cases.scopeFacts(uuid(facts, "scopeId"));
        for (String field : java.util.List.of("canonicalOrderId", "sourceKind", "fromWorkRound"))
            HandoverCommand.corruptUnless(facts.path(field).equals(scope.path(field)));
        return object().put("authorizationId", row.id().toString()).put("manifestSha256", row.receipt().manifestSha256())
            .put("expiresAt", row.expiresAt().toString()).put("serverRecoveryEpoch", row.recoveryEpoch())
            .put("userAuthVersion", row.userVersion()).put("deviceVersion", row.deviceVersion())
            .put("sourceKind", "TEMP").put("scopeId", text(facts, "scopeId")).put("mappingVersion", text(scope, "mappingVersion"))
            .put("fromWorkRound", text(facts, "fromWorkRound")).put("toWorkRound", text(facts, "toWorkRound"))
            .put("planSha256", text(body, "planSha256")).put("payloadSha256", sha(body));
    }

    private void baseline(UUID warehouse, JsonNode manifest, JsonNode body) {
        var current = baselines.read(warehouse);
        if (!current.inventorySnapshotId().equals(text(manifest, "inventorySnapshotId"))
                || !current.mappingRevision().equals(text(manifest, "mappingRevision"))
                || !current.inventoryVersion().equals(text(manifest, "inventoryVersion"))
                || !current.policy().version().equals(text(manifest, "policyVersion"))
                || !body.path("inventorySnapshotId").equals(manifest.path("inventorySnapshotId"))
                || !body.path("policyVersion").equals(manifest.path("policyVersion")))
            throw new BusinessException(409, "HANDOVER_TARGET_BASELINE_CHANGED", "接收人临时资料与原计划或有效基线不一致，请重新核对");
    }
}
