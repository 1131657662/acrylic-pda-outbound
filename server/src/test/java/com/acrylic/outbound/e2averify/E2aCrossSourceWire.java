package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.protocol.E2aPageItem;
import com.acrylic.outbound.temporary.protocol.E2aProtocol;
import com.acrylic.outbound.inventory.ImportFiles;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.security.KeyPair;
import java.util.List;
import java.util.UUID;

final class E2aCrossSourceWire {
    final E2aCrossSourceFixture fixture;
    final String manifest = UUID.randomUUID().toString(), snapshot = UUID.randomUUID().toString();
    final UUID openKey = UUID.randomUUID(), pageKey = UUID.randomUUID(), stopKey = UUID.randomUUID();
    final ObjectNode page, manifestCore;
    final E2aProtocol.Hashes hashes;

    E2aCrossSourceWire(E2aCrossSourceFixture fixture, List<E2aPageItem> items) {
        this.fixture = fixture;
        page = page(items); manifestCore = manifest(items);
        hashes = E2aProtocol.manifestHashes(manifestCore);
    }

    ObjectNode open() {
        return SubmissionJson.object().put("operation", "OPEN").put("manifestId", manifest).put("manifestVersion", 1)
            .put("snapshotId", snapshot).put("source", "TEMP").put("sourceId", fixture.seed.get("authorizationId"))
            .put("submissionId", fixture.submission.toString()).put("workRound", 1);
    }

    ObjectNode pageRequest() {
        var request = SubmissionJson.object().put("operation", "PAGE").put("openOriginalKey", openKey.toString())
            .put("pageHash", E2aProtocol.pageHash(page));
        request.set("pageCore", page); return request;
    }

    ObjectNode stop(ObjectNode audit, KeyPair deviceKey) {
        var core = SubmissionJson.object().put("warehouseId", fixture.seed.get("warehouseId"))
            .put("sourceId", fixture.seed.get("authorizationId")).put("submissionId", fixture.submission.toString())
            .put("workRound", 1).put("originalOperatorId", fixture.seed.get("operatorId"))
            .put("freezeDeviceId", fixture.seed.get("deviceId")).put("sourceRecoveryEpoch", 1)
            .put("stopEventId", audit.path("id").asText()).put("captureHighWatermark", captureWatermark())
            .put("auditThroughSeq", SubmissionJson.number(audit, "localSeq"))
            .put("stopTransactionVersion", 1).put("manifestId", manifest).put("manifestVersion", 1)
            .put("manifestBytesHash", hashes.manifestBytesHash()).put("setHash", hashes.setHash());
        var request = SubmissionJson.object().put("operation", "STOP").put("openOriginalKey", openKey.toString())
            .put("manifestBytesHash", hashes.manifestBytesHash()).put("setHash", hashes.setHash());
        request.set("manifestCore", manifestCore);
        request.set("stopProof", E2aProtocol.signStop(core, deviceKey.getPrivate(),
            ImportFiles.sha256(deviceKey.getPublic().getEncoded())));
        return request;
    }

    private long captureWatermark() {
        long high = 0;
        for (var item : page.path("items")) if ("CAPTURE".equals(item.path("itemKind").asText()))
            high = Math.max(high, item.path("originalDeviceSeq").longValue());
        return high;
    }

    ObjectNode verify(long version) {
        return SubmissionJson.object().put("operation", "VERIFY").put("openOriginalKey", openKey.toString())
            .put("stopOriginalKey", stopKey.toString()).put("manifestId", manifest).put("manifestVersion", 1)
            .put("setHash", hashes.setHash()).put("verificationVersion", version).put("algorithmVersion", 1);
    }

    private ObjectNode page(List<E2aPageItem> items) {
        var core = SubmissionJson.object().put("manifestId", manifest).put("manifestVersion", 1)
            .put("snapshotId", snapshot).put("pageNo", 1).put("pageCount", 1).put("isFinal", true)
            .put("itemCount", items.size()).put("byteCount", items.stream().mapToLong(E2aPageItem::byteCount).sum())
            .putNull("prevPageHash");
        var entries = core.putArray("items"); items.forEach(item -> entries.add(itemJson(item))); return core;
    }

    private ObjectNode manifest(List<E2aPageItem> items) {
        var core = SubmissionJson.object().put("schemaVersion", 1).put("manifestId", manifest).put("manifestVersion", 1)
            .put("snapshotId", snapshot).put("source", "TEMP").put("sourceId", fixture.seed.get("authorizationId"))
            .put("submissionId", fixture.submission.toString()).put("workRound", 1).put("pageCount", 1)
            .put("entryCount", items.size()).put("byteCount", page.path("byteCount").longValue()).put("endPageNo", 1);
        core.putArray("pages").add(SubmissionJson.object().put("pageNo", 1).put("pageHash", E2aProtocol.pageHash(page)));
        return core;
    }

    private static ObjectNode itemJson(E2aPageItem item) {
        var json = SubmissionJson.object().put("itemKind", item.itemKind()).put("originalRecordId", item.originalRecordId().toString())
            .put("objectKind", item.objectKind()).put("originalObjectId", item.originalObjectId().toString())
            .put("itemBytesSha256", item.itemBytesSha256()).put("byteCount", item.byteCount())
            .put("originalDeviceSeq", item.originalDeviceSeq()).put("recordSha256", item.recordSha256())
            .put("recordStatus", item.recordStatus()).putNull("baleId").putNull("originalCycle").putNull("captureId");
        if (item.baleId() != null) json.put("baleId", item.baleId().toString());
        if (item.originalCycle() != null) json.put("originalCycle", item.originalCycle());
        if (item.captureId() != null) json.put("captureId", item.captureId().toString());
        return json;
    }
}
