package com.acrylic.outbound.temporary.protocol;

import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import static com.acrylic.outbound.temporary.protocol.ProtocolJson.require;

/** Checks signed original-set integrity only; it neither trusts preparation/other sources nor reserves business packages. */
public final class LocalDraftStopSnapshotProtocol {
    private LocalDraftStopSnapshotProtocol() { }
    public record Item(long ordinal, String kind, UUID recordId, long deviceSeq, String recordStatus, String bytesSha256, byte[] rawBytes) { }
    public record SnapshotIntegrity(LocalDraftStopProtocol.VerifiedSignature signature, long ownCaptureCount, long otherCaptureCount, long archiveCount) { }

    public static SnapshotIntegrity verify(byte[] proof, byte[] metadata, LocalDraftStopProtocol.Expected expected, Iterable<Item> items) {
        var signed = LocalDraftStopProtocol.verify(proof, metadata, expected);
        var state = new State(signed, metadata, expected);
        require(items != null, "DRAFT_ITEMS_MISSING");
        for (Item item : items) state.append(item);
        return state.finish();
    }

    private static final class State {
        private final LocalDraftStopProtocol.VerifiedSignature signed;
        private final LocalDraftStopCoverage coverage;
        private final MessageDigest digest;
        private final Set<String> ids = new HashSet<>();
        private long count, bytes;

        State(LocalDraftStopProtocol.VerifiedSignature signed, byte[] metadata, LocalDraftStopProtocol.Expected expected) {
            this.signed = signed; bytes = metadata.length;
            coverage = new LocalDraftStopCoverage(signed, ProtocolJson.parse(metadata), expected);
            try { digest = MessageDigest.getInstance("SHA-256"); }
            catch (GeneralSecurityException error) { throw new IllegalStateException(error); }
            digest.update("acrylic-pda-local-draft-stop-v1\n".getBytes(StandardCharsets.UTF_8));
            digest.update(metadata); digest.update((byte)'\n');
        }

        void append(Item item) {
            require(item != null && item.ordinal() == count + 1 && count < signed.recordCount(), "DRAFT_ITEM_ORDER");
            require(item.kind() != null && Set.of("CAPTURE", "SCAN", "AUDIT", "ARCHIVE").contains(item.kind()) && item.recordId() != null, "DRAFT_ITEM_KIND");
            require(item.deviceSeq() > 0 && item.deviceSeq() <= 9007199254740991L && item.recordStatus() != null, "DRAFT_ITEM_SEQUENCE");
            require(ids.add(item.kind() + "/" + item.recordId()), "DRAFT_ITEM_DUPLICATE");
            require(item.rawBytes() != null && item.rawBytes().length > 0 && item.rawBytes().length <= 33554432, "DRAFT_ITEM_SIZE");
            byte[] raw = item.rawBytes().clone();
            require(ImportFiles.sha256(raw).equals(item.bytesSha256()), "DRAFT_ITEM_HASH");
            bytes = Math.addExact(bytes, raw.length);
            require(bytes <= signed.byteCount() && bytes <= 134217728, "DRAFT_ITEM_TOTAL_BYTES");
            JsonNode body = LocalDraftStopRaw.parse(raw);
            coverage.append(item, body);
            var frame = SubmissionJson.object().put("ordinal", Long.toString(item.ordinal())).put("kind", item.kind())
                .put("recordId", item.recordId().toString()).put("deviceSeq", Long.toString(item.deviceSeq()))
                .put("recordStatus", item.recordStatus()).put("bytesSha256", item.bytesSha256()).put("byteCount", Integer.toString(raw.length));
            digest.update((SubmissionJson.canonical(frame) + "\n").getBytes(StandardCharsets.UTF_8)); count++;
        }

        SnapshotIntegrity finish() {
            require(count == signed.recordCount() && bytes == signed.byteCount(), "DRAFT_SET_COUNT");
            require(HexFormat.of().formatHex(digest.digest()).equals(signed.rootSha256()), "DRAFT_SET_ROOT");
            coverage.finish();
            return new SnapshotIntegrity(signed, coverage.ownCaptures(), signed.captureHighWatermark() - coverage.ownCaptures(), coverage.archives());
        }
    }
}
