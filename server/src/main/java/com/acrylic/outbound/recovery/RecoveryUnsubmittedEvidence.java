package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakePage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Stable original E2A descriptors; database surrogate IDs never replace original object IDs. */
final class RecoveryUnsubmittedEvidence {
    private RecoveryUnsubmittedEvidence() { }

    static Expected bind(RecoverySourceIdentity id, byte[] payload, RecoveryOriginalEvidence.Request evidence) {
        require(id != null && evidence != null && id.sourceKind().equals("TEMP") && id.workRound() == 1, "RECOVERY_UNSUBMITTED_SOURCE");
        var stop = new RecoverySourceIdentity(id.sourceKind(), id.warehouseId(), id.scopeId(), id.submissionId(), id.deviceId(),
            id.operatorId(), id.qualificationId(), id.canonicalOrderId(), id.creatorDeviceId(), id.clientOrderId(), id.sourceRecoveryEpoch(),
            id.workRound(), id.captureHighWatermark(), Math.addExact(id.auditThroughSeq(), 1), id.orderScanHighWatermark());
        require(stop.equals(evidence.expected()), "RECOVERY_UNSUBMITTED_E2A_SOURCE");
        var expected = expected(evidence);
        require(expected.proof().payloadSha256().equals(SubmissionJson.sha(SubmissionJson.parse(payload))), "RECOVERY_UNSUBMITTED_E2A_PAYLOAD");
        return expected;
    }

    static Expected expected(RecoveryOriginalEvidence.Request request) {
        var proof = new RecoveryOriginalEvidence().read(request);
        var items = SubmissionJson.object().putArray("items");
        var open = com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeOpen.parse(request.bundle().openBytes(),
            request.expected().submissionId(), 1);
        for (byte[] page : request.bundle().pageBytes()) {
            for (var item : SourceEvidenceIntakePage.parse(page, request.openOriginalKey(), open).items()) {
                require(items.size() < 65536, "RECOVERY_UNSUBMITTED_ITEM_LIMIT");
                var row = items.addObject().put("itemKind", item.itemKind()).put("originalRecordId", item.originalRecordId().toString())
                    .put("originalObjectId", item.originalObjectId().toString()).put("objectKind", item.objectKind())
                    .put("originalDeviceSeq", Long.toString(item.originalDeviceSeq())).put("recordSha256", item.recordSha256())
                    .put("objectSha256", item.itemBytesSha256()).put("objectByteCount", Integer.toString(item.byteCount()))
                    .put("recordStatus", item.recordStatus());
                nullable(row, "baleId", item.baleId()); nullable(row, "captureId", item.captureId());
                if (item.originalCycle() == null) row.putNull("originalCycle"); else row.put("originalCycle", item.originalCycle().toString());
            }
        }
        var sorted = SubmissionJson.object().putArray("items");
        var list = new ArrayList<JsonNode>(); items.forEach(list::add);
        list.sort(Comparator.comparing(row -> text(row, "itemKind") + "/" + text(row, "originalRecordId"))); list.forEach(sorted::add);
        var stop = SubmissionJson.parse(request.bundle().stopBytes());
        byte[] manifest = SubmissionJson.canonical(stop.path("manifestCore")).getBytes(StandardCharsets.UTF_8);
        byte[] signature = SubmissionJson.canonical(stop.path("stopProof")).getBytes(StandardCharsets.UTF_8);
        return new Expected(proof, manifest, signature, SubmissionJson.sha(sorted));
    }

    static void verify(JsonNode set, Expected expected) {
        fields(set, "id manifestBytesSha256 setSha256 captureHighWatermark auditThroughSeq orderScanHighWatermark itemCount evidenceItemsSha256 stopProofSha256");
        uuid(set, "id"); var proof = expected.proof(); var id = proof.identity();
        require(text(set, "manifestBytesSha256").equals(sha256(expected.manifestBytes()))
            && text(set, "setSha256").equals(proof.setHash()) && text(set, "evidenceItemsSha256").equals(expected.itemsSha256())
            && text(set, "stopProofSha256").equals(sha256(expected.stopProofBytes()))
            && revision(set, "itemCount") == proof.itemCount() && revision(set, "captureHighWatermark") == id.captureHighWatermark()
            && revision(set, "auditThroughSeq") == id.auditThroughSeq()
            && revision(set, "orderScanHighWatermark") == id.orderScanHighWatermark(), "RECOVERY_UNSUBMITTED_EVIDENCE");
    }

    private static void nullable(ObjectNode row, String field, UUID id) {
        if (id == null) row.putNull(field); else row.put(field, id.toString());
    }

    record Expected(RecoveryOriginalEvidence.Proof proof, byte[] manifestBytes, byte[] stopProofBytes, String itemsSha256) {
        Expected { manifestBytes = manifestBytes.clone(); stopProofBytes = stopProofBytes.clone(); }
        @Override public byte[] manifestBytes() { return manifestBytes.clone(); }
        @Override public byte[] stopProofBytes() { return stopProofBytes.clone(); }
    }
}
