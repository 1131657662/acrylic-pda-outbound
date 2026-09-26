package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.protocol.E2aProtocol;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceEvidenceIntakeStopTest {
    private final UUID openKey = UUID.randomUUID();
    private final UUID warehouse = UUID.randomUUID();
    private final UUID operator = UUID.randomUUID();
    private final UUID device = UUID.randomUUID();
    private final UUID submission = UUID.randomUUID();
    private final UUID source = UUID.randomUUID();
    private KeyPair keys;
    private SourceEvidenceIntakeStop.Expected expected;

    @BeforeEach void setUp() throws Exception {
        var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        keys = generator.generateKeyPair();
        var openBody = SubmissionJson.object().put("operation", "OPEN").put("manifestId", "manifest")
            .put("manifestVersion", 1).put("snapshotId", "snapshot").put("source", "TEMP")
            .put("sourceId", source.toString()).put("submissionId", submission.toString()).put("workRound", 1);
        var open = SourceEvidenceIntakeOpen.parse(bytes(openBody), submission, 1);
        expected = new SourceEvidenceIntakeStop.Expected(openKey, open, warehouse, operator, device, 2, keys.getPublic());
    }

    @Test void signedStopBindsManifestAndServerKnownSource() {
        var request = request();
        var parsed = SourceEvidenceIntakeStop.parse(bytes(request), expected);

        assertThat(parsed.openOriginalKey()).isEqualTo(openKey);
        assertThat(parsed.manifestBytesHash()).isEqualTo(request.path("manifestBytesHash").textValue());
        assertThat(parsed.setHash()).isEqualTo(request.path("setHash").textValue());
        assertThat(parsed.canonicalStopProofBytes()).isNotEmpty();
        parsed.canonicalStopProofBytes()[0] = 0;
        assertThat(parsed.canonicalStopProofBytes()[0]).isNotZero();
    }

    @Test void derivedHashesCannotBeReplacedOrWrittenIntoCore() {
        var request = request().put("setHash", "0".repeat(64));
        assertThatThrownBy(() -> SourceEvidenceIntakeStop.parse(bytes(request), expected))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("STOP_MANIFEST_HASH");
        var invalid = request();
        ((ObjectNode) invalid.path("manifestCore")).put("setHash", "0".repeat(64));
        assertThatThrownBy(() -> SourceEvidenceIntakeStop.parse(bytes(invalid), expected))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void resigningWrongActorDoesNotMakeStopTrusted() {
        var request = request();
        var core = ((ObjectNode) request.path("stopProof").path("core")).deepCopy();
        core.put("originalOperatorId", UUID.randomUUID().toString());
        request.set("stopProof", sign(core));

        assertThatThrownBy(() -> SourceEvidenceIntakeStop.parse(bytes(request), expected))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("STOP_PROOF_CONTEXT");
    }

    @Test void unknownKeyAndWrongOpenAreRejected() throws Exception {
        var request = request();
        request.put("openOriginalKey", UUID.randomUUID().toString());
        assertThatThrownBy(() -> SourceEvidenceIntakeStop.parse(bytes(request), expected))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("STOP_OPEN_KEY");
        var other = KeyPairGenerator.getInstance("EC");
        other.initialize(new ECGenParameterSpec("secp256r1"));
        var context = new SourceEvidenceIntakeStop.Expected(openKey, expected.open(), warehouse, operator, device,
            2, other.generateKeyPair().getPublic());
        var original = request();
        assertThatThrownBy(() -> SourceEvidenceIntakeStop.parse(bytes(original), context))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("STOP_SIGNATURE");
    }

    @Test void resignedWrongEpochDoesNotBorrowCurrentAuthority() {
        var request = request();
        var core = ((ObjectNode) request.path("stopProof").path("core")).deepCopy();
        core.put("sourceRecoveryEpoch", 3);
        request.set("stopProof", sign(core));

        assertThatThrownBy(() -> SourceEvidenceIntakeStop.parse(bytes(request), expected))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("STOP_PROOF_CONTEXT");
    }

    @Test void legacyNonUuidStopEventIdRemainsValidWhenSigned() {
        var request = request();
        var core = ((ObjectNode) request.path("stopProof").path("core")).deepCopy();
        core.put("stopEventId", "original-stop-event");
        request.set("stopProof", sign(core));

        assertThat(SourceEvidenceIntakeStop.parse(bytes(request), expected).openOriginalKey()).isEqualTo(openKey);
    }

    @Test void stopRequestLargerThanReceiptColumnFailsBeforeParsing() {
        var oversized = new byte[262145];
        assertThatThrownBy(() -> SourceEvidenceIntakeStop.parse(oversized, expected))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("STOP_REQUEST_SIZE");
    }

    private ObjectNode request() {
        var page = SubmissionJson.object().put("manifestId", "manifest").put("manifestVersion", 1)
            .put("snapshotId", "snapshot").put("pageNo", 1).put("pageCount", 1)
            .put("isFinal", true).put("itemCount", 0).put("byteCount", 0).putNull("prevPageHash");
        page.putArray("items");
        var manifest = SubmissionJson.object().put("schemaVersion", 1).put("manifestId", "manifest")
            .put("manifestVersion", 1).put("snapshotId", "snapshot").put("source", "TEMP")
            .put("sourceId", source.toString()).put("submissionId", submission.toString())
            .put("workRound", 1).put("pageCount", 1).put("entryCount", 0).put("byteCount", 0).put("endPageNo", 1);
        manifest.putArray("pages").add(SubmissionJson.object().put("pageNo", 1).put("pageHash", E2aProtocol.pageHash(page)));
        var hashes = E2aProtocol.manifestHashes(manifest);
        var core = SubmissionJson.object().put("warehouseId", warehouse.toString()).put("sourceId", source.toString())
            .put("submissionId", submission.toString()).put("workRound", 1)
            .put("originalOperatorId", operator.toString()).put("freezeDeviceId", device.toString())
            .put("sourceRecoveryEpoch", 2).put("stopEventId", UUID.randomUUID().toString())
            .put("captureHighWatermark", 0).put("auditThroughSeq", 1).put("stopTransactionVersion", 1)
            .put("manifestId", "manifest").put("manifestVersion", 1)
            .put("manifestBytesHash", hashes.manifestBytesHash()).put("setHash", hashes.setHash());
        var request = SubmissionJson.object().put("operation", "STOP").put("openOriginalKey", openKey.toString())
            .put("manifestBytesHash", hashes.manifestBytesHash()).put("setHash", hashes.setHash());
        request.set("manifestCore", manifest);
        request.set("stopProof", sign(core));
        return request;
    }

    private ObjectNode sign(ObjectNode core) {
        return E2aProtocol.signStop(core, keys.getPrivate(), ImportFiles.sha256(keys.getPublic().getEncoded()));
    }

    private static byte[] bytes(ObjectNode node) {
        return node.toString().getBytes(StandardCharsets.UTF_8);
    }
}
