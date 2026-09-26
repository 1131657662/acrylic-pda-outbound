package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.JsonText;
import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.protocol.E2aProtocol;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class SourceEvidenceIntakeStopReceiptCodecTest {
    private final UUID submission = UUID.randomUUID(), source = UUID.randomUUID(), operator = UUID.randomUUID();
    private final UUID warehouse = UUID.randomUUID(), device = UUID.randomUUID();
    private final UUID openKey = UUID.randomUUID(), stopKey = UUID.randomUUID();
    @TempDir Path directory;
    private OfflineSigner signer;
    private SourceEvidenceIntakeReceiptCodec codec;
    private java.security.KeyPair deviceKeys;

    @BeforeEach void setUp() throws Exception {
        var rsa = KeyPairGenerator.getInstance("RSA"); rsa.initialize(2048);
        var pair = rsa.generateKeyPair();
        Files.write(directory.resolve("private.pk8"), pair.getPrivate().getEncoded());
        Files.write(directory.resolve("public.der"), pair.getPublic().getEncoded());
        signer = new OfflineSigner(directory.resolve("private.pk8").toString(),
            directory.resolve("public.der").toString(), new JsonText(new ObjectMapper()));
        codec = new SourceEvidenceIntakeReceiptCodec(signer);
        var ec = KeyPairGenerator.getInstance("EC"); ec.initialize(new ECGenParameterSpec("secp256r1"));
        deviceKeys = ec.generateKeyPair();
    }

    @Test void signedStopReceiptBindsSourceProofAndNoReleaseCapability() {
        var prepared = prepared();
        var receipt = codec.stop(UUID.randomUUID(), stopKey, prepared.open(), prepared.stop(),
            prepared.context(), prepared.object(), Instant.parse("2026-09-24T01:02:03.123456Z"));

        var response = SubmissionJson.parse(receipt.originalResponseBytes());
        assertThat(response.path("operationKind").asText()).isEqualTo("STOP");
        assertThat(response.path("status").asText()).isEqualTo("PARTIAL");
        assertThat(response.path("stopEventId").asText()).isEqualTo(prepared.stop().stopEventId());
        assertThat(response.path("objectBytesSha256").asText()).isEqualTo(prepared.object().stored().sha256());
        assertThat(response.path("manifestBytesHash").asText()).isEqualTo(prepared.stop().manifestBytesHash());
        assertThat(response.path("originalContext").path("principalId").asText()).isEqualTo(operator.toString());
        assertThat(response.path("recordedAt").asText()).isEqualTo("2026-09-24T01:02:03.123Z");
        assertThat(E2aProtocol.verifyRsaReceipt(response, signer.publicKey())).isEqualTo("VERIFIED");
        assertThat(response.path("capabilities").path("releaseAllowed").asBoolean()).isFalse();
        assertThat(response.path("capabilities").path("localDeletionAllowed").asBoolean()).isFalse();
    }

    @Test void forgedProofObjectCannotBeSignedAsReceived() {
        var prepared = prepared();
        var forged = new SourceEvidenceIntakeStopObjects.StopObject(prepared.object().id(),
            prepared.object().originalId(), new SourceEvidenceIntakeObjectFiles.Stored(
                "source-evidence-intake/objects/" + "0".repeat(64) + ".bin", "0".repeat(64),
                prepared.object().stored().bytes()));

        assertThatThrownBy(() -> codec.stop(UUID.randomUUID(), stopKey, prepared.open(), prepared.stop(),
            prepared.context(), forged, Instant.EPOCH))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("STOP_RECEIPT_INPUT");
    }

    private Prepared prepared() {
        var open = SourceEvidenceIntakeOpen.parse(bytes(SubmissionJson.object().put("operation", "OPEN")
            .put("manifestId", "manifest").put("manifestVersion", 2).put("snapshotId", "snapshot")
            .put("source", "TEMP").put("sourceId", source.toString())
            .put("submissionId", submission.toString()).put("workRound", 3)), submission, 3);
        var page = SubmissionJson.object().put("manifestId", "manifest").put("manifestVersion", 2)
            .put("snapshotId", "snapshot").put("pageNo", 1).put("pageCount", 1).put("isFinal", true)
            .put("itemCount", 0).put("byteCount", 0).putNull("prevPageHash");
        page.putArray("items");
        var manifest = SubmissionJson.object().put("schemaVersion", 1).put("manifestId", "manifest")
            .put("manifestVersion", 2).put("snapshotId", "snapshot").put("source", "TEMP")
            .put("sourceId", source.toString()).put("submissionId", submission.toString())
            .put("workRound", 3).put("pageCount", 1).put("entryCount", 0).put("byteCount", 0).put("endPageNo", 1);
        manifest.putArray("pages").add(SubmissionJson.object().put("pageNo", 1).put("pageHash", E2aProtocol.pageHash(page)));
        var hashes = E2aProtocol.manifestHashes(manifest);
        var proof = SubmissionJson.object().put("warehouseId", warehouse.toString()).put("sourceId", source.toString())
            .put("submissionId", submission.toString()).put("workRound", 3)
            .put("originalOperatorId", operator.toString()).put("freezeDeviceId", device.toString())
            .put("sourceRecoveryEpoch", 4).put("stopEventId", "stop-event")
            .put("captureHighWatermark", 0).put("auditThroughSeq", 1).put("stopTransactionVersion", 1)
            .put("manifestId", "manifest").put("manifestVersion", 2)
            .put("manifestBytesHash", hashes.manifestBytesHash()).put("setHash", hashes.setHash());
        var body = SubmissionJson.object().put("operation", "STOP").put("openOriginalKey", openKey.toString())
            .put("manifestBytesHash", hashes.manifestBytesHash()).put("setHash", hashes.setHash());
        body.set("manifestCore", manifest);
        body.set("stopProof", E2aProtocol.signStop(proof, deviceKeys.getPrivate(),
            ImportFiles.sha256(deviceKeys.getPublic().getEncoded())));
        var stop = SourceEvidenceIntakeStop.parse(bytes(body), new SourceEvidenceIntakeStop.Expected(
            openKey, open, warehouse, operator, device, 4, deviceKeys.getPublic()));
        var context = new SourceEvidenceIntakeReceipts.OpenInsert(operator, stopKey, warehouse, UUID.randomUUID(),
            submission, 3, device, UUID.randomUUID(), source, operator, 4, UUID.randomUUID(), "7", "2",
            stop.canonicalBytes(), stop.requestSha256());
        var proofBytes = stop.canonicalStopProofBytes();
        var hash = ImportFiles.sha256(proofBytes);
        var object = new SourceEvidenceIntakeStopObjects.StopObject(UUID.randomUUID(),
            SourceEvidenceIntakeStopObjects.originalId(openKey, stop.stopEventId()),
            new SourceEvidenceIntakeObjectFiles.Stored("source-evidence-intake/objects/" + hash + ".bin",
                hash, proofBytes.length));
        return new Prepared(open, stop, context, object);
    }

    private static byte[] bytes(ObjectNode value) { return value.toString().getBytes(StandardCharsets.UTF_8); }
    private record Prepared(SourceEvidenceIntakeOpen.Parsed open, SourceEvidenceIntakeStop.Parsed stop,
        SourceEvidenceIntakeReceipts.OpenInsert context, SourceEvidenceIntakeStopObjects.StopObject object) { }
}
