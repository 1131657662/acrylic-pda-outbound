package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.protocol.E2aProtocol;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class SourceEvidenceIntakeStop {
    private static final Set<String> FIELDS = Set.of("operation", "openOriginalKey", "manifestCore",
        "manifestBytesHash", "setHash", "stopProof");

    private SourceEvidenceIntakeStop() { }

    public static Parsed parse(byte[] bytes, Expected expected) {
        require(expected != null && bytes != null && bytes.length > 0 && bytes.length <= 262144,
            "STOP_REQUEST_SIZE");
        var request = SourceEvidenceIntakeReplay.canonicalize(bytes);
        require(request.bytes().length <= 262144, "STOP_REQUEST_SIZE");
        var body = SubmissionJson.parse(request.bytes());
        require(body.isObject() && fields(body).equals(FIELDS), "STOP_REQUEST_FIELDS");
        require("STOP".equals(body.path("operation").textValue()), "STOP_OPERATION");
        require(expected.openKey().toString().equals(body.path("openOriginalKey").textValue()), "STOP_OPEN_KEY");
        require(body.path("manifestCore") instanceof ObjectNode, "STOP_MANIFEST_CORE");
        var manifest = (ObjectNode) body.path("manifestCore");
        var hashes = E2aProtocol.manifestHashes(manifest);
        validateManifest(manifest, expected);
        require(hashes.manifestBytesHash().equals(body.path("manifestBytesHash").textValue())
            && hashes.setHash().equals(body.path("setHash").textValue()), "STOP_MANIFEST_HASH");
        var proof = body.path("stopProof");
        require(E2aProtocol.verifyStop(proof, expected.trustedKey()), "STOP_SIGNATURE");
        validateProof(proof.path("core"), hashes, expected);
        byte[] manifestBytes = canonical(manifest);
        require(manifestBytes.length <= 262144, "STOP_MANIFEST_SIZE");
        return new Parsed(request.bytes(), request.sha256(), manifestBytes, hashes.manifestBytesHash(),
            hashes.setHash(), canonical(proof), expected.openKey(), proof.path("core").path("stopEventId").textValue());
    }

    private static void validateManifest(ObjectNode core, Expected expected) {
        var open = expected.open();
        require("TEMP".equals(core.path("source").textValue())
            && open.manifestId().equals(core.path("manifestId").textValue())
            && open.manifestVersion() == core.path("manifestVersion").longValue()
            && open.snapshotId().equals(core.path("snapshotId").textValue())
            && open.sourceId().equals(core.path("sourceId").textValue())
            && open.submissionId().toString().equals(core.path("submissionId").textValue())
            && open.workRound() == core.path("workRound").longValue(), "STOP_MANIFEST_CONTEXT");
    }

    private static void validateProof(JsonNode core, E2aProtocol.Hashes hashes, Expected expected) {
        var open = expected.open();
        require(expected.warehouseId().toString().equals(core.path("warehouseId").textValue())
            && open.sourceId().equals(core.path("sourceId").textValue())
            && open.submissionId().toString().equals(core.path("submissionId").textValue())
            && open.workRound() == core.path("workRound").longValue()
            && expected.operatorId().toString().equals(core.path("originalOperatorId").textValue())
            && expected.deviceId().toString().equals(core.path("freezeDeviceId").textValue())
            && expected.recoveryEpoch() == core.path("sourceRecoveryEpoch").longValue()
            && open.manifestId().equals(core.path("manifestId").textValue())
            && open.manifestVersion() == core.path("manifestVersion").longValue()
            && hashes.manifestBytesHash().equals(core.path("manifestBytesHash").textValue())
            && hashes.setHash().equals(core.path("setHash").textValue()), "STOP_PROOF_CONTEXT");
    }

    private static byte[] canonical(JsonNode value) {
        return E2aProtocol.canonical(value.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static Set<String> fields(JsonNode value) {
        var keys = new HashSet<String>();
        value.fieldNames().forEachRemaining(keys::add);
        return keys;
    }

    private static void require(boolean condition, String code) {
        if (!condition) throw new IllegalArgumentException(code);
    }

    public record Expected(UUID openKey, SourceEvidenceIntakeOpen.Parsed open, UUID warehouseId,
        UUID operatorId, UUID deviceId, long recoveryEpoch, PublicKey trustedKey) {
        public Expected {
            if (openKey == null || open == null || warehouseId == null || operatorId == null
                || deviceId == null || recoveryEpoch < 0 || trustedKey == null)
                throw new IllegalArgumentException("STOP_EXPECTED_CONTEXT");
        }
    }

    public record Parsed(byte[] canonicalBytes, String requestSha256, byte[] manifestBytes,
        String manifestBytesHash, String setHash, byte[] canonicalStopProofBytes, UUID openOriginalKey,
        String stopEventId) {
        public Parsed {
            canonicalBytes = canonicalBytes.clone();
            manifestBytes = manifestBytes.clone();
            canonicalStopProofBytes = canonicalStopProofBytes.clone();
        }
        @Override public byte[] canonicalBytes() { return canonicalBytes.clone(); }
        @Override public byte[] manifestBytes() { return manifestBytes.clone(); }
        @Override public byte[] canonicalStopProofBytes() { return canonicalStopProofBytes.clone(); }
    }
}
