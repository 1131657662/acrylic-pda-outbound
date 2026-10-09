package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.evidence.*;
import com.acrylic.outbound.temporary.protocol.E2aPageItem;
import com.fasterxml.jackson.databind.JsonNode;
import java.security.PublicKey;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Original first-round TEMP E2A bytes, with a separately selected historical device public key. */
public final class RecoveryOriginalEvidence {
    public Proof read(Request request) {
        require(request != null && request.expected().sourceKind().equals("TEMP")
            && request.expected().workRound() == 1, "RECOVERY_E2A_SOURCE");
        try { return verify(request); }
        catch (RecoveryControlException refusal) { throw refusal; }
        catch (RuntimeException invalid) { throw new RecoveryControlException("RECOVERY_E2A_INVALID", invalid); }
    }

    private Proof verify(Request request) {
        var expected = request.expected(); var bytes = request.bundle();
        var open = SourceEvidenceIntakeOpen.parse(bytes.openBytes(), expected.submissionId(), expected.workRound());
        require(open.sourceId().equals(expected.qualificationId().toString()), "RECOVERY_E2A_SOURCE_ID");
        var stop = SourceEvidenceIntakeStop.parse(bytes.stopBytes(), new SourceEvidenceIntakeStop.Expected(
            request.openOriginalKey(), open, expected.warehouseId(), expected.operatorId(), expected.deviceId(),
            Long.parseLong(expected.sourceRecoveryEpoch()), request.trustedKey()));
        var manifest = SubmissionJson.parse(stop.manifestBytes());
        var pages = pages(bytes.pageBytes(), request.openOriginalKey(), open);
        var items = manifest(manifest, pages); var original = bytes.originalObjects();
        var records = RecoveryEvidenceRecords.read(items, original);
        RecoveryEvidenceSequence.verify(records, SubmissionJson.parse(stop.canonicalStopProofBytes()).path("core"), expected);
        require(open.snapshotId().equals(text(records.frozen(), "inventorySnapshotId")), "RECOVERY_E2A_SNAPSHOT");
        return new Proof(expected, request.openOriginalKey(), sha256(request.trustedKey().getEncoded()),
            stop.manifestBytesHash(), stop.setHash(), SubmissionJson.sha(records.frozen()), stop.stopEventId(), pages.size(), items.size());
    }

    private List<SourceEvidenceIntakePage.Parsed> pages(List<byte[]> bytes, UUID key, SourceEvidenceIntakeOpen.Parsed open) {
        var result = new ArrayList<SourceEvidenceIntakePage.Parsed>();
        for (byte[] page : bytes) result.add(SourceEvidenceIntakePage.parse(page, key, open));
        return List.copyOf(result);
    }

    private List<E2aPageItem> manifest(JsonNode manifest, List<SourceEvidenceIntakePage.Parsed> pages) {
        require(manifest.path("pageCount").longValue() == pages.size(), "RECOVERY_E2A_PAGE_COUNT");
        var result = new ArrayList<E2aPageItem>(); var records = new HashSet<String>(); var objects = new HashSet<UUID>();
        String prior = null; long total = 0;
        for (int index = 0; index < pages.size(); index++) {
            var page = pages.get(index);
            require(page.pageNo() == index + 1 && page.pageCount() == pages.size()
                && text(manifest.path("pages").get(index), "pageHash").equals(page.pageHash()), "RECOVERY_E2A_PAGE_ORDER");
            require(Objects.equals(prior, page.prevPageHash()), "RECOVERY_E2A_PAGE_CHAIN"); prior = page.pageHash();
            for (var item : page.items()) {
                require(records.add(item.itemKind() + "/" + item.originalRecordId()) && objects.add(item.originalObjectId()),
                    "RECOVERY_E2A_ITEM_DUPLICATE");
                total = Math.addExact(total, item.byteCount()); result.add(item);
            }
        }
        require(result.size() == manifest.path("entryCount").longValue() && total == manifest.path("byteCount").longValue(),
            "RECOVERY_E2A_MANIFEST_TOTALS");
        return List.copyOf(result);
    }
    public record Request(Bundle bundle, UUID openOriginalKey, PublicKey trustedKey, RecoverySourceIdentity expected) {
        public Request {
            require(bundle != null && openOriginalKey != null && trustedKey != null && expected != null, "RECOVERY_E2A_REQUEST");
        }
    }

    public record Bundle(byte[] openBytes, List<byte[]> pageBytes, byte[] stopBytes, Map<String, byte[]> originalObjects) {
        public Bundle {
            require(openBytes != null && stopBytes != null && pageBytes != null && originalObjects != null
                && openBytes.length > 0 && openBytes.length <= 262144 && stopBytes.length > 0 && stopBytes.length <= 262144
                && !pageBytes.isEmpty() && pageBytes.size() <= 4096, "RECOVERY_E2A_REQUEST");
            long total = 0;
            for (byte[] page : pageBytes) {
                require(page != null && page.length > 0 && page.length <= 262144, "RECOVERY_E2A_REQUEST"); total += page.length;
            }
            require(total <= 134217728, "RECOVERY_E2A_REQUEST"); total = 0;
            for (var entry : originalObjects.entrySet()) {
                require(entry.getKey() != null && entry.getKey().matches("[0-9a-f]{64}") && entry.getValue() != null
                    && entry.getValue().length > 0 && entry.getValue().length <= 33554432, "RECOVERY_E2A_REQUEST");
                total = Math.addExact(total, entry.getValue().length);
            }
            require(total <= 134217728, "RECOVERY_E2A_REQUEST");
            openBytes = openBytes.clone(); stopBytes = stopBytes.clone();
            pageBytes = pageBytes.stream().map(byte[]::clone).toList(); originalObjects = copy(originalObjects);
        }
        @Override public byte[] openBytes() { return openBytes.clone(); }
        @Override public List<byte[]> pageBytes() { return pageBytes.stream().map(byte[]::clone).toList(); }
        @Override public byte[] stopBytes() { return stopBytes.clone(); }
        @Override public Map<String, byte[]> originalObjects() { return copy(originalObjects); }
        private static Map<String, byte[]> copy(Map<String, byte[]> values) {
            var result = new HashMap<String, byte[]>(); values.forEach((key, value) -> result.put(key, value.clone()));
            return Map.copyOf(result);
        }
    }

    public record Proof(RecoverySourceIdentity identity, UUID openOriginalKey, String deviceKeySha256,
            String manifestBytesHash, String setHash, String payloadSha256, String stopEventId, int pageCount, int itemCount) {
        public boolean ordinaryWritesAllowed() { return false; }
        public boolean localDeletionAllowed() { return false; }
    }
}
