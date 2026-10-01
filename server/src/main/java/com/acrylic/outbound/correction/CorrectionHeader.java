package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Set;

/** Validates scalar changes to business header fields at creation and approval. */
final class CorrectionHeader {
    private static final Set<String> OWNED = Set.of("submissionId","source","orderId","status",
        "payloadSha256","freezeDeviceId","workRound","issuedRecoveryEpoch","resultVersion","receiptVersion",
        "actualCount","actualWeightGrams","inventoryDeducted","items","signedReceipt","firstOrderScanSeq",
        "lastOrderScanSeq","completedAt","captureHighWatermark","mayResumeScanning","stockReleased","orderStatus");
    static String merge(String snapshot, JsonNode patch) {
        var current = SubmissionJson.parse(snapshot); var merged = (ObjectNode) current.deepCopy();
        boolean changed = false; var fields = patch.fields();
        while (fields.hasNext()) {
            var field = fields.next(); String key = field.getKey();
            if (!current.has(key) || OWNED.contains(key) || !field.getValue().isValueNode())
                throw new BusinessException(400, "INVALID_CORRECTION_COMMAND", "表头字段不可更正：" + key);
            if (!current.get(key).equals(field.getValue())) { merged.replace(key, field.getValue()); changed = true; }
        }
        if (!changed) throw CorrectionCommand.targetUnchanged();
        return SubmissionJson.canonical(merged);
    }
}
