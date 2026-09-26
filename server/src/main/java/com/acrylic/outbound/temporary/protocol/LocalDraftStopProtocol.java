package com.acrylic.outbound.temporary.protocol;

import com.acrylic.outbound.inventory.ImportFiles;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.util.*;
import static com.acrylic.outbound.temporary.protocol.ProtocolJson.require;

/** Independent unsubmitted-source signature domain. Does not create S, reserve K or certify raw completeness. */
public final class LocalDraftStopProtocol {
    public static final String DOMAIN = "ACRYLIC-R06/E2A/LOCAL-DRAFT-STOP/1\n";
    private LocalDraftStopProtocol() { }

    /** All fields, especially registeredDeviceKey, must be resolved from trusted caller context. */
    public record Expected(UUID evidenceId, UUID clientOrderId, UUID creatorDeviceId, UUID authorizationId,
        UUID warehouseId, UUID originalOperatorId, UUID snapshotId, long sourceRecoveryEpoch, PublicKey registeredDeviceKey) {
        public Expected {
            Objects.requireNonNull(evidenceId); Objects.requireNonNull(clientOrderId); Objects.requireNonNull(creatorDeviceId);
            Objects.requireNonNull(authorizationId); Objects.requireNonNull(warehouseId); Objects.requireNonNull(originalOperatorId);
            Objects.requireNonNull(snapshotId); Objects.requireNonNull(registeredDeviceKey);
            require(sourceRecoveryEpoch > 0 && sourceRecoveryEpoch <= 9007199254740991L, "DRAFT_STOP_EXPECTED_EPOCH");
        }
    }
    public record VerifiedSignature(UUID evidenceId, UUID clientOrderId, UUID stopEventId, String metadataSha256,
        String rootSha256, long recordCount, long byteCount, long captureHighWatermark, long auditThroughSeq, String keyId) { }

    public static VerifiedSignature verify(byte[] proof, byte[] metadata, Expected expected) {
        try {
            Objects.requireNonNull(expected);
            JsonNode envelope = canonical(proof, 16384), meta = canonical(metadata, 131072);
            LocalDraftStopClaims.fields(envelope, Set.of("schemaVersion", "algorithm", "keyId", "core", "signature"));
            LocalDraftStopClaims.number(envelope, "schemaVersion", 1, 1);
            LocalDraftStopClaims.equal(envelope, "algorithm", "ECDSA_P256_SHA256_DER");
            JsonNode core = envelope.path("core");
            LocalDraftStopClaims.validate(core, meta, metadata, expected);
            signature(envelope, core, expected.registeredDeviceKey());
            return new VerifiedSignature(expected.evidenceId(), expected.clientOrderId(), UUID.fromString(core.path("stopEventId").textValue()),
                core.path("metadataSha256").textValue(), core.path("rootSha256").textValue(), core.path("recordCount").longValue(),
                core.path("byteCount").longValue(), core.path("captureHighWatermark").longValue(), core.path("auditThroughSeq").longValue(),
                envelope.path("keyId").textValue());
        } catch (GeneralSecurityException | RuntimeException error) {
            throw new IllegalArgumentException("LOCAL_DRAFT_STOP_INVALID", error);
        }
    }

    private static JsonNode canonical(byte[] bytes, int maximum) {
        require(bytes != null && bytes.length > 0 && bytes.length <= maximum, "DRAFT_STOP_SIZE");
        JsonNode node = ProtocolJson.parse(bytes);
        require(Arrays.equals(bytes, ProtocolJson.canonical(node)), "DRAFT_STOP_CANONICAL"); return node;
    }

    private static void signature(JsonNode envelope, JsonNode core, PublicKey key) throws GeneralSecurityException {
        require(key instanceof ECPublicKey ec && p256(ec), "DRAFT_STOP_KEY");
        LocalDraftStopClaims.equal(envelope, "keyId", ImportFiles.sha256(key.getEncoded()));
        String encoded = LocalDraftStopClaims.text(envelope, "signature");
        byte[] signature = Base64.getDecoder().decode(encoded);
        require(signature.length <= 80 && Base64.getEncoder().encodeToString(signature).equals(encoded), "DRAFT_STOP_BASE64");
        Signature verifier = Signature.getInstance("SHA256withECDSA"); verifier.initVerify(key);
        verifier.update(DOMAIN.getBytes(StandardCharsets.UTF_8)); verifier.update(ProtocolJson.canonical(core));
        require(verifier.verify(signature), "DRAFT_STOP_SIGNATURE");
    }

    private static boolean p256(ECPublicKey key) throws GeneralSecurityException {
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC"); parameters.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec expected = parameters.getParameterSpec(ECParameterSpec.class), actual = key.getParams();
        return actual != null && actual.getCurve().equals(expected.getCurve()) && actual.getGenerator().equals(expected.getGenerator())
            && actual.getOrder().equals(expected.getOrder()) && actual.getCofactor() == expected.getCofactor();
    }
}
