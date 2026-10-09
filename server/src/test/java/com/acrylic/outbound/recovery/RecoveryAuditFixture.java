package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;

/** Synthetic immutable device history; original source trust is explicit, never inferred from these events. */
final class RecoveryAuditFixture {
    final RecoverySourceFixture source;
    final RecoveryReceiptFixture key = new RecoveryReceiptFixture();
    final ObjectNode body = SubmissionJson.object();
    final ArrayNode events = SubmissionJson.object().putArray("events");
    final List<RecoveryAuditArchiveProof.Segment> prefix = new ArrayList<>();

    RecoveryAuditFixture() throws Exception { this("FORMAL"); }

    RecoveryAuditFixture(String kind) throws Exception {
        source = RecoverySourceFixture.initial(kind);
        var id = source.identity;
        body.put("sourceRecoveryEpoch", id.sourceRecoveryEpoch()).put("freezeDeviceId", id.deviceId().toString())
            .put("workRound", Long.toString(id.workRound())).put("submissionId", id.submissionId().toString());
        body.putArray("auditCoverage").addObject().put("originDeviceId", id.deviceId().toString())
            .put("afterSeq", "0").put("throughSeq", Long.toString(id.auditThroughSeq()));
        for (long sequence = 1; sequence <= id.auditThroughSeq(); sequence++) events.add(event(sequence));
        body.set("events", events.deepCopy()); body.set("auditEvents", events.deepCopy());
    }

    private ObjectNode event(long sequence) {
        var id = source.identity; boolean last = sequence == id.auditThroughSeq();
        var event = SubmissionJson.object().put("id", UUID.randomUUID().toString()).putNull("captureId")
            .put("originDeviceId", id.deviceId().toString()).put("operatorId", id.operatorId().toString())
            .put("assignmentId", id.qualificationId().toString()).put("localSeq", Long.toString(sequence))
            .put("type", last ? "FROZEN" : "RECEIVED").put("detail", last ? id.submissionId().toString() : "original detail")
            .put("wallMs", "1000").put("elapsedMs", "1000").put("boot", 1);
        if (id.sourceKind().equals("TEMP")) {
            event.remove("assignmentId"); event.put("authorizationId", id.qualificationId().toString());
            event.putObject("source").put("kind", "TEMP").put("creatorDeviceId", id.creatorDeviceId().toString())
                .put("clientOrderId", id.clientOrderId().toString());
        }
        return event;
    }

    void archivePrefix() throws Exception {
        var archived = SubmissionJson.object().put("originDeviceId", source.identity.deviceId().toString())
            .put("afterSeq", "0").put("throughSeq", "4"); var first = archived.putArray("events");
        for (int index = 0; index < 4; index++) first.add(events.get(index).deepCopy());
        byte[] bytes = RecoveryReceiptFixture.bytes(archived);
        var proof = SubmissionJson.object().put("archiveVersion", 1).put("originDeviceId", source.identity.deviceId().toString())
            .put("warehouseId", source.identity.warehouseId().toString()).put("recoveryEpoch", source.identity.sourceRecoveryEpoch())
            .put("afterSeq", "0").put("throughSeq", "4").put("payloadSha256", RecoveryControlFormat.sha256(bytes))
            .put("byteCount", Integer.toString(bytes.length)).put("storagePolicy", "SERVER_DURABLE_FILES_V1")
            .put("localDeletionAllowed", false).put("archivedAt", "2026-09-20T00:00:00Z").put("retainUntil", "2031-09-20T00:00:00Z");
        var claims = SubmissionJson.object().put("iss", "acrylic-outbound").put("aud", "acrylic-pda-audit-archive");
        claims.set("archive", proof.deepCopy());
        var header = SubmissionJson.object().put("alg", "RS256").put("typ", "JWT").put("kid", key.kid);
        proof.put("signedArchive", key.token(header, claims)); setPrefix(proof, bytes);
        ((ObjectNode)body.at("/auditCoverage/0")).put("afterSeq", "4");
        var tail = body.putArray("auditEvents"); for (int index = 4; index < events.size(); index++) tail.add(events.get(index).deepCopy());
    }

    void setPrefix(ObjectNode proof, byte[] bytes) {
        prefix.clear(); prefix.add(new RecoveryAuditArchiveProof.Segment(proof, bytes));
        body.putArray("auditArchives").add(proof.deepCopy());
    }

    void resignPrefix(ObjectNode proof, String audience) throws Exception {
        var unsigned = proof.deepCopy(); unsigned.remove("signedArchive");
        var claims = SubmissionJson.object().put("iss", "acrylic-outbound").put("aud", audience);
        claims.set("archive", unsigned);
        var header = SubmissionJson.object().put("alg", "RS256").put("typ", "JWT").put("kid", key.kid);
        proof.put("signedArchive", key.token(header, claims)); setPrefix(proof, prefix.getFirst().payload());
    }

    RecoverySourceAudit verifier() { return new RecoverySourceAudit(new RecoveryReceiptKeys(List.of(key.pair.getPublic().getEncoded()))); }
    RecoverySourceAudit.Context context() { return new RecoverySourceAudit.Context(source.identity, Map.of(source.identity.qualificationId(), source.identity), prefix); }
    byte[] bytes() { return RecoveryReceiptFixture.bytes(body); }
}
