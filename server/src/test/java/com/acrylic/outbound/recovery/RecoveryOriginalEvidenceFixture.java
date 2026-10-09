package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.protocol.E2aProtocol;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.sha256;

/** Native first-round E2A encodings with synthetic identity and development EC signing, never an original device. */
final class RecoveryOriginalEvidenceFixture {
    final RecoveryInitialFixture initial = new RecoveryInitialFixture("TEMP");
    final UUID openKey = UUID.randomUUID();
    final KeyPair key;
    final ObjectNode open, frozen, stopEvent, manifest, stopCore, stop;
    final List<ObjectNode> pageCores = new ArrayList<>();
    final List<ObjectNode> entries = new ArrayList<>();
    final Map<String, byte[]> originals = new HashMap<>();
    final RecoverySourceIdentity identity;

    RecoveryOriginalEvidenceFixture() throws Exception { this(ignored -> { }); }

    RecoveryOriginalEvidenceFixture(java.util.function.Consumer<RecoveryInitialFixture> scenario) throws Exception {
        scenario.accept(initial);
        var generator = KeyPairGenerator.getInstance("EC"); generator.initialize(new ECGenParameterSpec("secp256r1")); key = generator.generateKeyPair();
        var id = initial.expected();
        frozen = initial.state.payload.deepCopy(); frozen.remove(List.of("auditEvents", "auditCoverage", "auditArchives"));
        int captures = frozen.path("captures").size(), auditHigh = frozen.path("events").size() + 1;
        for (int index = 0; index < captures; index++) ((ObjectNode)frozen.path("captures").get(index)).put("deviceCaptureSeq", Integer.toString(index + 1));
        identity = new RecoverySourceIdentity("TEMP", id.warehouseId(), id.scopeId(), id.submissionId(), id.deviceId(), id.operatorId(),
            id.qualificationId(), null, id.creatorDeviceId(), id.clientOrderId(), id.sourceRecoveryEpoch(), 1, captures, auditHigh, captures);
        stopEvent = ((ObjectNode)frozen.path("events").get(auditHigh - 2)).deepCopy().put("id", UUID.randomUUID().toString())
            .put("type", "STOP").put("localSeq", Integer.toString(auditHigh));
        open = SubmissionJson.object().put("operation", "OPEN").put("manifestId", UUID.randomUUID().toString()).put("manifestVersion", 1)
            .put("snapshotId", frozen.path("inventorySnapshotId").asText()).put("source", "TEMP")
            .put("sourceId", id.qualificationId().toString()).put("submissionId", id.submissionId().toString()).put("workRound", 1);
        for (var capture : frozen.path("captures")) item("CAPTURE", capture, "CAPTURE_RECORDS", "id", "deviceCaptureSeq", "state");
        for (var scan : frozen.path("scans")) item("SCAN", scan, "CAPTURE_RECORDS", "captureId", "localSeq", "status");
        for (var event : frozen.path("events")) item("AUDIT", event, "AUDIT_RECORDS", "id", "localSeq", "type");
        item("AUDIT", stopEvent, "AUDIT_RECORDS", "id", "localSeq", "type");
        item("STOP", frozen, "LOCAL_FROZEN_PAYLOAD", "submissionId", null, null);
        manifest = SubmissionJson.object(); stopCore = SubmissionJson.object(); stop = SubmissionJson.object(); rebuild();
    }

    void item(String kind, JsonNode value, String objectKind, String idField, String sequenceField, String statusField) {
        byte[] raw = bytes(value); String hash = sha256(raw); originals.put(hash, raw);
        var item = SubmissionJson.object().put("itemKind", kind).put("originalRecordId", value.path(idField).asText())
            .put("objectKind", objectKind).put("originalObjectId", UUID.randomUUID().toString()).put("itemBytesSha256", hash)
            .put("byteCount", raw.length).put("originalDeviceSeq", sequenceField == null ? identity.auditThroughSeq() : value.path(sequenceField).asLong())
            .put("recordSha256", hash).put("recordStatus", statusField == null ? "FROZEN" : value.path(statusField).asText());
        for (String field : List.of("baleId", "originalCycle", "captureId")) item.putNull(field);
        if (kind.equals("SCAN")) item.put("baleId", value.path("baleId").asText()).put("originalCycle", value.path("expectedDispatchCycle").asLong())
            .put("captureId", value.path("captureId").asText());
        entries.add(item);
    }

    void rebuild() {
        pageCores.clear(); int count = (entries.size() + 2) / 3; String previous = null;
        for (int index = 0; index < count; index++) {
            var page = SubmissionJson.object();
            for (String field : List.of("manifestId", "manifestVersion", "snapshotId")) page.set(field, open.path(field));
            page.put("pageNo", index + 1).put("pageCount", count).put("isFinal", index + 1 == count);
            if (previous == null) page.putNull("prevPageHash"); else page.put("prevPageHash", previous);
            var items = page.putArray("items"); long total = 0;
            for (int entry = index * 3; entry < Math.min(entries.size(), index * 3 + 3); entry++) { items.add(entries.get(entry)); total += entries.get(entry).path("byteCount").asLong(); }
            page.put("itemCount", items.size()).put("byteCount", total); previous = E2aProtocol.pageHash(page); pageCores.add(page);
        }
        manifest.removeAll(); manifest.put("schemaVersion", 1);
        for (String field : List.of("manifestId", "manifestVersion", "snapshotId", "source", "sourceId", "submissionId", "workRound")) manifest.set(field, open.path(field));
        manifest.put("pageCount", count).put("endPageNo", count).put("entryCount", entries.size())
            .put("byteCount", entries.stream().mapToLong(item -> item.path("byteCount").asLong()).sum());
        var roots = manifest.putArray("pages");
        for (var page : pageCores) roots.addObject().put("pageNo", page.path("pageNo").asInt()).put("pageHash", E2aProtocol.pageHash(page));
        refreshStop();
    }

    void refreshStop() {
        var hashes = E2aProtocol.manifestHashes(manifest);
        stopCore.put("warehouseId", identity.warehouseId().toString()).put("sourceId", identity.qualificationId().toString())
            .put("submissionId", identity.submissionId().toString()).put("workRound", 1).put("originalOperatorId", identity.operatorId().toString())
            .put("freezeDeviceId", identity.deviceId().toString()).put("sourceRecoveryEpoch", 1).put("stopEventId", stopEvent.path("id").asText())
            .put("captureHighWatermark", identity.captureHighWatermark()).put("auditThroughSeq", identity.auditThroughSeq()).put("stopTransactionVersion", 1)
            .put("manifestId", open.path("manifestId").asText()).put("manifestVersion", 1)
            .put("manifestBytesHash", hashes.manifestBytesHash()).put("setHash", hashes.setHash());
        stop.put("operation", "STOP").put("openOriginalKey", openKey.toString()).put("manifestBytesHash", hashes.manifestBytesHash()).put("setHash", hashes.setHash());
        stop.set("manifestCore", manifest.deepCopy()); signStop();
    }

    void signStop() { stop.set("stopProof", E2aProtocol.signStop(stopCore, key.getPrivate(), sha256(key.getPublic().getEncoded()))); }

    void replace(String kind, JsonNode value) {
        String id = value.path(kind.equals("STOP") ? "submissionId" : kind.equals("SCAN") ? "captureId" : "id").asText();
        var item = entries.stream().filter(entry -> entry.path("itemKind").asText().equals(kind)
            && entry.path("originalRecordId").asText().equals(id)).findFirst().orElseThrow();
        originals.remove(item.path("itemBytesSha256").asText()); byte[] raw = bytes(value); String hash = sha256(raw); originals.put(hash, raw);
        item.put("itemBytesSha256", hash).put("recordSha256", hash).put("byteCount", raw.length);
        if (kind.equals("SCAN")) item.put("originalDeviceSeq", value.path("localSeq").asLong());
    }

    RecoveryOriginalEvidence.Request request() { return request(key.getPublic(), identity); }
    RecoveryOriginalEvidence.Request request(PublicKey publicKey, RecoverySourceIdentity expected) {
        var pages = new ArrayList<byte[]>();
        for (var core : pageCores) {
            var page = SubmissionJson.object().put("operation", "PAGE").put("openOriginalKey", openKey.toString()).put("pageHash", E2aProtocol.pageHash(core));
            page.set("pageCore", core); pages.add(bytes(page));
        }
        return new RecoveryOriginalEvidence.Request(new RecoveryOriginalEvidence.Bundle(bytes(open), pages, bytes(stop), originals), openKey, publicKey, expected);
    }

    static byte[] bytes(JsonNode value) { return SubmissionJson.canonical(value).getBytes(StandardCharsets.UTF_8); }

    static void failedCaptureBeforeSave(RecoveryInitialFixture initial) {
        var body = initial.state.payload; var captures = (ArrayNode)body.path("captures");
        var failure = ((ObjectNode)captures.get(0)).deepCopy().put("id", UUID.randomUUID().toString())
            .put("state", "INVALID").put("error", "original invalid input").put("rawInput", "bad").putNull("confirmedAt")
            .put("failureAckAt", "2026-09-20T00:35:00Z");
        ((ObjectNode)captures.get(0)).put("orderScanSeq", "2"); captures.insert(0, failure);
        var scan = (ObjectNode)body.path("scans").get(0); scan.put("orderScanSeq", "2").put("localSeq", "7");
        var original = body.path("events").deepCopy(); var events = body.putArray("events");
        for (String type : List.of("RECEIVED", "INVALID", "FAILURE_ACKNOWLEDGED")) {
            var event = ((ObjectNode)original.get(0)).deepCopy().put("id", UUID.randomUUID().toString())
                .put("localSeq", Integer.toString(events.size() + 1)).put("captureId", failure.path("id").asText()).put("type", type);
            event.put("detail", type.equals("RECEIVED") ? "" : "original invalid input"); events.add(event);
        }
        for (var value : original) events.add(((ObjectNode)value).deepCopy().put("localSeq", Integer.toString(events.size() + 1)));
        body.put("captureHighWatermark", "2"); ((ObjectNode)body.path("captureSummary")).put("received", "2").put("invalid", "1");
    }
}
