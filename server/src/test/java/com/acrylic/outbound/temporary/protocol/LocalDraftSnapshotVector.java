package com.acrylic.outbound.temporary.protocol;

import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Consumer;

final class LocalDraftSnapshotVector {
    final LocalDraftStopVector proof;
    final List<LocalDraftStopSnapshotProtocol.Item> items = new ArrayList<>();
    LocalDraftStopProtocol.Expected expected;

    LocalDraftSnapshotVector(String name) throws Exception {
        proof = new LocalDraftStopVector("/e2a/local-draft-snapshots/" + name + ".json"); expected = proof.expected;
        for (var item : proof.document.path("items")) items.add(new LocalDraftStopSnapshotProtocol.Item(item.path("ordinal").longValue(),
            item.path("kind").textValue(), UUID.fromString(item.path("recordId").textValue()), item.path("deviceSeq").longValue(),
            item.path("recordStatus").textValue(), item.path("bytesSha256").textValue(), LocalDraftStopVector.bytes(item.path("raw"))));
    }

    LocalDraftStopSnapshotProtocol.SnapshotIntegrity verify() {
        return LocalDraftStopSnapshotProtocol.verify(proof.proofBytes(), proof.metadataBytes(), expected, items);
    }
    int index(String kind) {
        for (int i = 0; i < items.size(); i++) if (items.get(i).kind().equals(kind)) return i;
        throw new IllegalArgumentException("missing synthetic item");
    }
    void raw(int index, Consumer<ObjectNode> mutation) {
        var item = items.get(index); var node = (ObjectNode) SubmissionJson.parse(item.rawBytes()); mutation.accept(node);
        byte[] raw = LocalDraftStopVector.bytes(node);
        items.set(index, new LocalDraftStopSnapshotProtocol.Item(item.ordinal(), item.kind(), item.recordId(), item.deviceSeq(),
            item.recordStatus(), ImportFiles.sha256(raw), raw));
    }
    void descriptor(int index, long sequence, String status) {
        var item = items.get(index);
        items.set(index, new LocalDraftStopSnapshotProtocol.Item(item.ordinal(), item.kind(), item.recordId(), sequence,
            status, item.bytesSha256(), item.rawBytes()));
    }
    void resignSet() throws Exception {
        proof.bindMetadata(); byte[] metadata = proof.metadataBytes(); long size = metadata.length;
        var digest = MessageDigest.getInstance("SHA-256");
        digest.update("acrylic-pda-local-draft-stop-v1\n".getBytes(StandardCharsets.UTF_8)); digest.update(metadata); digest.update((byte)'\n');
        for (int i = 0; i < items.size(); i++) {
            var old = items.get(i);
            var item = new LocalDraftStopSnapshotProtocol.Item(i + 1, old.kind(), old.recordId(), old.deviceSeq(), old.recordStatus(), old.bytesSha256(), old.rawBytes());
            items.set(i, item); size += item.rawBytes().length;
            var frame = SubmissionJson.object().put("ordinal", Long.toString(item.ordinal())).put("kind", item.kind())
                .put("recordId", item.recordId().toString()).put("deviceSeq", Long.toString(item.deviceSeq())).put("recordStatus", item.recordStatus())
                .put("bytesSha256", item.bytesSha256()).put("byteCount", Integer.toString(item.rawBytes().length));
            digest.update((SubmissionJson.canonical(frame) + "\n").getBytes(StandardCharsets.UTF_8));
        }
        proof.core().put("rootSha256", HexFormat.of().formatHex(digest.digest())).put("recordCount", items.size()).put("byteCount", size);
        proof.sign(); expected = proof.mutableExpected();
    }
}
