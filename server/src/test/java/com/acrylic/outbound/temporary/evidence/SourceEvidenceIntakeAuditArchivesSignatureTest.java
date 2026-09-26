package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.audit.AuditArchiveReader;
import com.acrylic.outbound.audit.AuditArchiveStore;
import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.JsonText;
import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.submissions.SubmissionArchiveProofs;
import com.acrylic.outbound.submissions.SubmissionFiles;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.protocol.E2aPageItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SourceEvidenceIntakeAuditArchivesSignatureTest {
    @TempDir Path directory;
    private final UUID device = UUID.randomUUID(), warehouse = UUID.randomUUID();
    private final AuditArchiveStore store = mock(AuditArchiveStore.class);
    private final SourceEvidenceIntakeAuditPrefix prefix = mock(SourceEvidenceIntakeAuditPrefix.class);
    private SubmissionFiles files;
    private SourceEvidenceIntakeAuditArchives archives;
    private OfflineSigner signer;

    @BeforeEach void setUp() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        Files.write(directory.resolve("private.pk8"), pair.getPrivate().getEncoded());
        Files.write(directory.resolve("public.der"), pair.getPublic().getEncoded());
        signer = new OfflineSigner(directory.resolve("private.pk8").toString(),
            directory.resolve("public.der").toString(), new JsonText(new ObjectMapper()));
        files = new SubmissionFiles(directory.toString());
        archives = new SourceEvidenceIntakeAuditArchives(store,
            new AuditArchiveReader(files, new SubmissionArchiveProofs(signer)), prefix);
    }

    @Test void validServerSignatureAndDurableBodyMustBothExist() throws Exception {
        var proof = proof();
        when(store.find("2", device, 0)).thenReturn(proof);
        var item = item(proof);

        archives.requireItem(item, bytes(proof), context());

        Files.write(directory.resolve("submissions").resolve(proof.path("payloadSha256").asText() + ".json"),
            "changed".getBytes(StandardCharsets.UTF_8));
        assertUnavailable(() -> archives.requireItem(item, bytes(proof), context()));
    }

    @Test void substitutedServerSignatureCannotBorrowAStoredArchive() {
        var proof = proof();
        proof.put("signedArchive", "not-a-valid-jwt");
        when(store.find("2", device, 0)).thenReturn(proof);

        assertUnavailable(() -> archives.requireItem(item(proof), bytes(proof), context()));
    }

    private ObjectNode proof() {
        var body = SubmissionJson.object().put("originDeviceId", device.toString())
            .put("afterSeq", "0").put("throughSeq", "1");
        body.putArray("events").add(SubmissionJson.object().put("id", UUID.randomUUID().toString())
            .putNull("captureId").put("originDeviceId", device.toString())
            .put("operatorId", UUID.randomUUID().toString()).put("localSeq", "1")
            .put("type", "STOP").put("detail", "saved").put("wallMs", "1")
            .put("elapsedMs", "1").put("boot", 1).put("assignmentId", UUID.randomUUID().toString()));
        byte[] content = bytes(body);
        String digest = files.store(content);
        var proof = SubmissionJson.object().put("archiveVersion", 1)
            .put("originDeviceId", device.toString()).put("warehouseId", warehouse.toString())
            .put("recoveryEpoch", "2").put("afterSeq", "0").put("throughSeq", "1")
            .put("payloadSha256", digest).put("byteCount", Integer.toString(content.length))
            .put("archivedAt", "2026-09-24T00:00:00Z").put("retainUntil", "2031-09-24T00:00:00Z")
            .put("storagePolicy", "SERVER_DURABLE_FILES_V1").put("localDeletionAllowed", false);
        var claims = SubmissionJson.object().put("iss", "acrylic-outbound")
            .put("aud", "acrylic-pda-audit-archive");
        claims.set("archive", proof.deepCopy());
        proof.put("signedArchive", signer.sign(claims).token());
        return proof;
    }

    private E2aPageItem item(ObjectNode proof) {
        byte[] original = bytes(proof);
        String digest = SubmissionJson.sha(proof);
        return new E2aPageItem("ARCHIVE", SourceEvidenceIntakeAuditArchives.recordId(device,
            SubmissionJson.number(proof, "recoveryEpoch"), SubmissionJson.number(proof, "afterSeq")),
            "AUDIT_ARCHIVE", UUID.randomUUID(), ImportFiles.sha256(original), original.length,
            1, digest, "SAVED", null, null, null);
    }

    private SourceEvidenceIntakeAuditArchives.Context context() {
        return new SourceEvidenceIntakeAuditArchives.Context(device, warehouse, 2, 1);
    }

    private static byte[] bytes(ObjectNode value) {
        return SubmissionJson.canonical(value).getBytes(StandardCharsets.UTF_8);
    }

    private static void assertUnavailable(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
            error -> org.assertj.core.api.Assertions.assertThat(error.code())
                .isEqualTo("ARCHIVE_EVIDENCE_UNAVAILABLE"));
    }
}
