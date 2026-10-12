package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Strict business request allowlist; the original command remains canonical and immutable. */
final class ReconciliationInput {
    private ReconciliationInput() { }
    static void validate(JsonNode body) {
        var allowed = new HashSet<>(Set.of("commandVersion", "commandId", "commandType", "warehouseId",
            "transportRecoveryEpoch", "reason", "expected"));
        String kind=body.path("commandType").asText();
        String id = kind.endsWith("_DISCOVER") ? kind.startsWith("RECONCILE_QUARANTINE_") ? "baleId" : "dispatchId" : "caseId";
        allowed.add(id);
        if (body.path("commandType").asText().endsWith("_SUBMIT")) allowed.add("evidence");
        var actual = new HashSet<String>(); body.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(allowed) || !body.path("commandVersion").isIntegralNumber() || body.path("commandVersion").asInt()!=1)
            throw invalid();
        for (String key : Set.of("commandId", "warehouseId", id)) {
            if (!body.path(key).isTextual()) throw invalid();
            try { if (!UUID.fromString(body.path(key).asText()).toString().equals(body.path(key).asText())) throw invalid(); }
            catch (IllegalArgumentException failure) { throw invalid(); }
        }
        ReconciliationPolicy.text(body.path("reason"), 500);
        if (!body.path("transportRecoveryEpoch").isTextual()
            || !body.path("transportRecoveryEpoch").asText().matches("[1-9][0-9]{0,18}")) throw invalid();
    }
    private static BusinessException invalid() { return new BusinessException(400,"INVALID_RECONCILIATION_COMMAND","差异命令字段或身份格式无效"); }
}
