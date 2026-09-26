package com.acrylic.outbound.temporary.protocol;

import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

/** Android Room stop export contains only synthetic data and its public key, never a private key. */
final class LocalDraftStopVector {
    final ObjectNode proof, metadata;
    final PublicKey originalKey;
    final LocalDraftStopProtocol.Expected expected;
    final KeyPair mutableKey;
    final JsonNode document;

    LocalDraftStopVector() throws Exception {
        this("/e2a/local-draft-stop-v1-android.json");
    }
    LocalDraftStopVector(String resource) throws Exception {
        try (var stream = getClass().getResourceAsStream(resource)) {
            JsonNode vector = SubmissionJson.parse(Objects.requireNonNull(stream).readAllBytes());
            document = vector;
            proof = ((ObjectNode) vector.path("proof")).deepCopy(); metadata = ((ObjectNode) vector.path("metadata")).deepCopy();
            originalKey = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(vector.path("publicKey").asText())));
        }
        expected = expected(originalKey); mutableKey = keys("secp256r1");
    }

    ObjectNode core() { return (ObjectNode) proof.path("core"); }
    byte[] proofBytes() { return bytes(proof); }
    byte[] metadataBytes() { return bytes(metadata); }
    LocalDraftStopProtocol.Expected expected(PublicKey key) {
        return new LocalDraftStopProtocol.Expected(id("evidenceId"), id("clientOrderId"), id("creatorDeviceId"), id("authorizationId"),
            id("warehouseId"), id("originalOperatorId"), id("snapshotId"), core().path("sourceRecoveryEpoch").longValue(), key);
    }
    LocalDraftStopProtocol.Expected wrongContext(int field) {
        var ids = new ArrayList<>(List.of(expected.evidenceId(), expected.clientOrderId(), expected.creatorDeviceId(),
            expected.authorizationId(), expected.warehouseId(), expected.originalOperatorId(), expected.snapshotId()));
        if (field < ids.size()) ids.set(field, UUID.randomUUID());
        return new LocalDraftStopProtocol.Expected(ids.get(0), ids.get(1), ids.get(2), ids.get(3), ids.get(4), ids.get(5), ids.get(6),
            expected.sourceRecoveryEpoch() + (field == 7 ? 1 : 0), originalKey);
    }
    LocalDraftStopProtocol.Expected mutableExpected() {
        return new LocalDraftStopProtocol.Expected(expected.evidenceId(), expected.clientOrderId(), expected.creatorDeviceId(), expected.authorizationId(),
            expected.warehouseId(), expected.originalOperatorId(), expected.snapshotId(), expected.sourceRecoveryEpoch(), mutableKey.getPublic());
    }
    void sign() throws Exception { sign(mutableKey, LocalDraftStopProtocol.DOMAIN); }
    void bindMetadata() { core().put("metadataSha256", ImportFiles.sha256(metadataBytes())); }
    void sign(KeyPair keys, String domain) throws Exception {
        proof.put("keyId", ImportFiles.sha256(keys.getPublic().getEncoded()));
        var signer = Signature.getInstance("SHA256withECDSA"); signer.initSign(keys.getPrivate());
        signer.update(domain.getBytes(StandardCharsets.UTF_8)); signer.update(bytes(core()));
        proof.put("signature", Base64.getEncoder().encodeToString(signer.sign()));
    }
    private UUID id(String name) { return UUID.fromString(core().path(name).textValue()); }
    static byte[] bytes(JsonNode node) { return SubmissionJson.canonical(node).getBytes(StandardCharsets.UTF_8); }
    static KeyPair keys(String curve) throws Exception {
        var generator = KeyPairGenerator.getInstance("EC"); generator.initialize(new ECGenParameterSpec(curve)); return generator.generateKeyPair();
    }
}
