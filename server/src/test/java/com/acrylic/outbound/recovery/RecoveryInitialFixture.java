package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.*;
import java.nio.file.Files;
import java.util.*;

/** Complete single-source files and original encodings, with synthetic control and in-memory development RSA. */
final class RecoveryInitialFixture {
    final RecoveryPreparationFixture prep;
    final RecoverySourceStateFixture state;
    final List<RecoveryAuditArchiveProof.Segment> prefix = new ArrayList<>();

    RecoveryInitialFixture(String kind) throws Exception {
        prep = kind.equals("FORMAL") ? new RecoveryFormalActivationFixture().prep
            : new RecoveryTemporaryActivationFixture().baseline.prep;
        state = prep.state; complete(); publish();
    }

    private void complete() {
        var id = state.authority.source.identity; var body = state.payload;
        body.put("schemaVersion", 2).put("submissionId", id.submissionId().toString()).put("captureHighWatermark", "1");
        var origin = body.putObject("source").put("kind", id.sourceKind());
        if (id.sourceKind().equals("FORMAL")) origin.put("sourceOrderId", id.canonicalOrderId().toString());
        else origin.put("creatorDeviceId", id.creatorDeviceId().toString()).put("clientOrderId", id.clientOrderId().toString());
        capture().put("deviceCaptureSeq", "7").put("inputSource", "MANUAL").put("confirmationPolicy", "MANUAL")
            .put("rawInput", "01234567890").put("error", "").put("receivedWallMs", "1000")
            .put("receivedElapsedMs", "1000").put("receivedBoot", 1).putNull("failureAckAt");
        scan().put("localSeq", "4");
        var events = body.putArray("events");
        for (String type : List.of("RECEIVED", "WAITING_CONFIRM", "CONFIRM_REQUESTED", "SAVED", "FROZEN"))
            events.add(event(type, events.size() + 1));
        body.set("auditEvents", events.deepCopy());
        body.putArray("auditCoverage").addObject().put("originDeviceId", id.deviceId().toString()).put("afterSeq", "0").put("throughSeq", "5");
        body.putObject("captureSummary").put("received", "1").put("saved", "1").put("voided", "0")
            .put("pending", "0").put("invalid", "0").put("businessRejected", "0").put("discarded", "0");
        ((ObjectNode)state.authority.source.manifest.path("source")).put("captureHighWatermark", "7")
            .put("auditThroughSeq", "5").put("orderScanHighWatermark", "1");
    }

    private ObjectNode event(String type, long sequence) {
        var id = state.authority.source.identity;
        var value = SubmissionJson.object().put("id", UUID.randomUUID().toString()).put("localSeq", Long.toString(sequence))
            .put("originDeviceId", id.deviceId().toString()).put("operatorId", id.operatorId().toString()).put("type", type)
            .put("detail", type.equals("FROZEN") ? id.submissionId().toString() : "").put("wallMs", "1000").put("elapsedMs", "1000").put("boot", 1);
        if (type.equals("FROZEN")) value.putNull("captureId"); else value.put("captureId", capture().path("id").asText());
        if (id.sourceKind().equals("FORMAL")) value.put("assignmentId", id.qualificationId().toString());
        else {
            value.put("authorizationId", id.qualificationId().toString());
            value.set("source", state.payload.path("source").deepCopy());
        }
        return value;
    }

    void publish() throws Exception {
        var source = state.authority.source; String hash = SubmissionJson.sha(state.payload);
        state.permission.put("payloadSha256", hash).put("credentialSha256", RecoveryControlFormat.sha256(Files.readAllBytes(source.object("origin"))));
        state.occupancy.put("payloadSha256", hash);
        state.permission.set("source", source.manifest.path("source").deepCopy());
        state.occupancy.set("source", source.manifest.path("source").deepCopy());
        var receipt = state.authority.key.receipt(source, state.status).put("payloadSha256", hash);
        state.object("payload", state.payload); state.object("result", state.authority.key.signed(receipt));
        state.object("permission", state.permission); state.object("occupancy", state.occupancy);
    }

    void eventType(int index, String type) {
        ((ObjectNode)state.payload.path("events").get(index)).put("type", type);
        ((ObjectNode)state.payload.path("auditEvents").get(index)).put("type", type);
    }

    void archivePrefix() throws Exception {
        var id = expected(); var key = state.authority.key;
        var body = SubmissionJson.object().put("originDeviceId", id.deviceId().toString()).put("afterSeq", "0").put("throughSeq", "2");
        body.putArray("events").add(state.payload.path("events").get(0)).add(state.payload.path("events").get(1));
        byte[] bytes = RecoveryReceiptFixture.bytes(body);
        var proof = SubmissionJson.object().put("archiveVersion", 1).put("originDeviceId", id.deviceId().toString())
            .put("warehouseId", id.warehouseId().toString()).put("recoveryEpoch", id.sourceRecoveryEpoch())
            .put("afterSeq", "0").put("throughSeq", "2").put("byteCount", Integer.toString(bytes.length))
            .put("payloadSha256", RecoveryControlFormat.sha256(bytes)).put("storagePolicy", "SERVER_DURABLE_FILES_V1")
            .put("localDeletionAllowed", false).put("archivedAt", "2026-09-20T00:00:00Z").put("retainUntil", "2031-09-20T00:00:00Z");
        var claims = SubmissionJson.object().put("iss", "acrylic-outbound").put("aud", "acrylic-pda-audit-archive"); claims.set("archive", proof.deepCopy());
        proof.put("signedArchive", key.token(SubmissionJson.object().put("alg", "RS256").put("typ", "JWT").put("kid", key.kid), claims));
        prefix.add(new RecoveryAuditArchiveProof.Segment(proof, bytes));
        state.payload.putArray("auditArchives").add(proof);
        ((ObjectNode)state.payload.at("/auditCoverage/0")).put("afterSeq", "2");
        var tail = state.payload.putArray("auditEvents");
        for (int index = 2; index < 5; index++) tail.add(state.payload.path("events").get(index));
        publish();
    }

    void terminalCapture(String terminal) throws Exception {
        capture().put("state", terminal).putNull("failureAckAt").put("error", "original reason");
        boolean rejected = terminal.equals("BUSINESS_REJECTED");
        if (!rejected) capture().putNull("confirmedAt");
        if (!terminal.equals("DISCARDED")) capture().put("failureAckAt", "2026-09-20T00:35:00Z");
        ((ArrayNode)state.payload.path("scans")).removeAll(); ((ArrayNode)state.occupancy.path("rows")).removeAll();
        state.payload.put("expectedCount", "0").put("expectedWeightGrams", "0").put("shortShipmentReason", "original reason");
        var summary = (ObjectNode)state.payload.path("captureSummary"); summary.put("saved", "0");
        summary.put(terminal.equals("INVALID") ? "invalid" : rejected ? "businessRejected" : "discarded", "1");
        var types = rejected ? List.of("RECEIVED", "WAITING_CONFIRM", "CONFIRM_REQUESTED", terminal, "FAILURE_ACKNOWLEDGED", "FROZEN")
            : terminal.equals("INVALID") ? List.of("RECEIVED", terminal, "FAILURE_ACKNOWLEDGED", "FROZEN")
            : List.of("RECEIVED", "WAITING_CONFIRM", terminal, "FROZEN");
        var events = state.payload.putArray("events");
        for (String type : types) {
            var value = event(type, events.size() + 1);
            if (type.equals(terminal) || type.equals("FAILURE_ACKNOWLEDGED")) value.put("detail", "original reason");
            events.add(value);
        }
        state.payload.set("auditEvents", events.deepCopy());
        ((ObjectNode)state.payload.at("/auditCoverage/0")).put("throughSeq", Integer.toString(events.size()));
        ((ObjectNode)state.authority.source.manifest.path("source")).put("auditThroughSeq", Integer.toString(events.size()));
        publish();
    }

    RecoverySourceIdentity expected() { return RecoverySourceIdentity.read(state.authority.source.manifest.path("source")); }
    RecoveryInitialSource.Request request() {
        var source = state.authority.source;
        return new RecoveryInitialSource.Request(source.materials, source.pin, expected(), prefix);
    }
    RecoveryInitialSource reader() { return new RecoveryInitialSource(state.authority.source.reader(), keys()); }
    RecoveryReceiptKeys keys() { return new RecoveryReceiptKeys(List.of(state.authority.key.pair.getPublic().getEncoded())); }
    ObjectNode capture() { return (ObjectNode)state.payload.path("captures").get(0); }
    ObjectNode scan() { return (ObjectNode)state.payload.path("scans").get(0); }
    RecoveryPinnedSource source() { var request = request(); return state.authority.source.reader().read(request.materialRoot(), request.independentManifestSha256(), expected()); }
}
