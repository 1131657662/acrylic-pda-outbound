package e2a.review;

import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.protocol.E2aProtocol;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** Lightweight bridge invoking the frozen production E2aProtocol class. */
public final class ProductionBridge {
    private ProductionBridge() { }

    public static void main(String[] args) throws Exception {
        stopNegativeMatrix();
        rsaNegativeMatrix();
        shapeNegativeMatrix();
        if (args.length == 1) frozenVectorMatrix(Path.of(args[0]));
    }

    private static void stopNegativeMatrix() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        KeyPair pair = generator.generateKeyPair();
        ObjectNode validCore = stopCore(1, 1);
        String keyId = hex(pair.getPublic().getEncoded());
        ObjectNode valid = E2aProtocol.signStop(validCore, pair.getPrivate(), keyId);
        report("stop_valid_p256_keyid", E2aProtocol.verifyStop(valid, pair.getPublic()));

        ObjectNode missingSchema = valid.deepCopy();
        missingSchema.remove("schemaVersion");
        report("stop_missing_schema_version_rejected", !E2aProtocol.verifyStop(missingSchema, pair.getPublic()));

        ObjectNode numericSchema = valid.deepCopy();
        numericSchema.put("schemaVersion", "1");
        report("stop_string_schema_version_rejected", !E2aProtocol.verifyStop(numericSchema, pair.getPublic()));

        ObjectNode wrongKeyId = valid.deepCopy();
        wrongKeyId.put("keyId", "0".repeat(64));
        report("stop_wrong_p256_keyid_rejected", !E2aProtocol.verifyStop(wrongKeyId, pair.getPublic()));

        ObjectNode emptyCore = valid.deepCopy();
        emptyCore.set("core", SubmissionJson.object());
        report("stop_empty_core_rejected", !E2aProtocol.verifyStop(emptyCore, pair.getPublic()));

        ObjectNode overflowSchema = valid.deepCopy();
        overflowSchema.put("schemaVersion", 4294967297L);
        report("stop_schema_overflow_rejected", !E2aProtocol.verifyStop(overflowSchema, pair.getPublic()));

        report("stop_zero_epoch_accepted_by_contract", signs(stopCore(0, 1), pair, keyId));
        report("stop_zero_high_watermark_accepted_by_contract", signs(stopCore(1, 0), pair, keyId));

        KeyPairGenerator p384 = KeyPairGenerator.getInstance("EC");
        p384.initialize(384);
        KeyPair nonP256 = p384.generateKeyPair();
        report("stop_non_p256_key_rejected", !E2aProtocol.verifyStop(valid, nonP256.getPublic()));
    }

    private static void rsaNegativeMatrix() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        String keyId = "bridge-rsa";
        ObjectNode receipt = SubmissionJson.object().put("receiptVersion", 1)
            .put("status", "RECEIVED").put("actorId", "actor-1").put("sourceId", "source-1")
            .put("sourceRecoveryEpoch", 3);
        String jwt = signReceipt(receipt, pair, keyId);
        receipt.put("signedReceipt", jwt);
        OfflineSigner.PublicKeyView view = new OfflineSigner.PublicKeyView(
            keyId, "RS256", Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
        report("rsa_valid_receipt", "VERIFIED".equals(E2aProtocol.verifyRsaReceipt(receipt, view)));

        ObjectNode changedFacts = receipt.deepCopy();
        changedFacts.put("actorId", "actor-2");
        changedFacts.remove("signedReceipt");
        changedFacts.put("signedReceipt", signReceipt(changedFacts, pair, keyId));
        report("rsa_legal_resign_changed_facts_signature_valid", "VERIFIED".equals(E2aProtocol.verifyRsaReceipt(changedFacts, view)));
        report("rsa_legal_resign_changed_facts_not_trusted", "UNTRUSTED_FACTS".equals(E2aProtocol.verifyTrustedRsaReceipt(changedFacts, view, receipt)));

        ObjectNode unknownKid = receipt.deepCopy();
        unknownKid.remove("signedReceipt");
        unknownKid.put("signedReceipt", signReceipt(unknownKid, pair, "unknown-kid"));
        report("rsa_unknown_key_rejected", !"VERIFIED".equals(E2aProtocol.verifyRsaReceipt(unknownKid, view)));

        ObjectNode emptyFacts = receipt.deepCopy();
        emptyFacts.remove("actorId");
        emptyFacts.remove("sourceId");
        emptyFacts.remove("sourceRecoveryEpoch");
        emptyFacts.remove("signedReceipt");
        emptyFacts.put("signedReceipt", signReceipt(emptyFacts, pair, keyId));
        report("rsa_empty_facts_signature_valid", "VERIFIED".equals(E2aProtocol.verifyRsaReceipt(emptyFacts, view)));
        report("rsa_empty_facts_not_trusted", "UNTRUSTED_FACTS".equals(E2aProtocol.verifyTrustedRsaReceipt(emptyFacts, view, receipt)));

        ObjectNode unsafeKey = receipt.deepCopy();
        unsafeKey.put("signedReceipt", signReceipt(receipt, pair, keyId));
        report("rsa_facts_must_be_compared_separately", !E2aProtocol.trustedFacts(unsafeKey, emptyFacts));
    }

    private static void shapeNegativeMatrix() {
        ObjectNode page = SubmissionJson.object().put("manifestId", "m").put("manifestVersion", 1)
            .put("snapshotId", "s").put("pageNo", 1).put("pageCount", 1).put("isFinal", true)
            .put("itemCount", 0).put("byteCount", 0).putNull("prevPageHash");
        page.putArray("items");
        report("page_valid_shape", hashSucceeds(page));
        ObjectNode derived = page.deepCopy().put("pageHash", "0".repeat(64));
        report("page_derived_field_rejected", !hashSucceeds(derived));
        ObjectNode numericPage = page.deepCopy().put("pageNo", "1");
        report("page_string_number_rejected", !hashSucceeds(numericPage));

        ObjectNode manifest = SubmissionJson.object().put("schemaVersion", 1).put("manifestId", "m")
            .put("manifestVersion", 1).put("snapshotId", "s").put("source", "TEMP")
            .put("sourceId", "source-1").put("submissionId", "submission-1")
            .put("workRound", 1).put("pageCount", 1).put("entryCount", 0).put("byteCount", 0)
            .put("endPageNo", 1);
        manifest.putArray("pages").add(SubmissionJson.object().put("pageNo", 1).put("pageHash", E2aProtocol.pageHash(page)));
        report("manifest_valid_shape", hashesSucceed(manifest));
        report("manifest_derived_field_rejected", !hashesSucceed(manifest.deepCopy().put("setHash", "0".repeat(64))));
    }

    private static ObjectNode stopCore(long epoch, long highWatermark) {
        return SubmissionJson.object().put("warehouseId", "warehouse-1").put("sourceId", "source-1")
            .put("submissionId", "submission-1").put("workRound", 1).put("originalOperatorId", "operator-1")
            .put("freezeDeviceId", "device-1").put("sourceRecoveryEpoch", epoch).put("stopEventId", "event-1")
            .put("captureHighWatermark", highWatermark).put("auditThroughSeq", 1).put("stopTransactionVersion", 1)
            .put("manifestId", "manifest-1").put("manifestVersion", 1)
            .put("manifestBytesHash", "a".repeat(64)).put("setHash", "b".repeat(64));
    }

    private static boolean signs(ObjectNode core, KeyPair pair, String keyId) {
        try { E2aProtocol.signStop(core, pair.getPrivate(), keyId); return true; }
        catch (RuntimeException error) { return false; }
    }

    private static void frozenVectorMatrix(Path vectorPath) throws Exception {
        JsonNode vector = SubmissionJson.parse(Files.readAllBytes(vectorPath));
        ObjectNode page = (ObjectNode) SubmissionJson.parse(vector.path("pageCoreUtf8").asText());
        ObjectNode manifest = (ObjectNode) SubmissionJson.parse(vector.path("manifestCoreUtf8").asText());
        report("vector_page_hash_matches", E2aProtocol.pageHash(page).equals(vector.path("pageHash").asText()));
        try {
            E2aProtocol.Hashes hashes = E2aProtocol.manifestHashes(manifest);
            report("vector_manifest_hash_matches", hashes.manifestBytesHash().equals(vector.path("manifestBytesHash").asText()));
            report("vector_set_hash_matches", hashes.setHash().equals(vector.path("setHash").asText()));
        } catch (RuntimeException error) {
            report("vector_manifest_hash_matches", false);
            report("vector_set_hash_matches", false);
            System.out.println("case=vector_manifest_error type=" + error.getClass().getSimpleName() + " message=" + error.getMessage());
        }
        ObjectNode stop = SubmissionJson.object().put("schemaVersion", 1)
            .put("algorithm", vector.path("stopAlgorithm").asText()).put("keyId", vector.path("ecKeyId").asText())
            .put("signature", vector.path("stopSignatureDerBase64").asText());
        stop.set("core", SubmissionJson.parse(vector.path("stopCoreUtf8").asText()));
        PublicKey key = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(
            Base64.getDecoder().decode(vector.path("ecPublicKeySpkiBase64").asText())));
        report("vector_complete_stop_accepted", E2aProtocol.verifyStop(stop, key));
    }

    private static String signReceipt(ObjectNode receipt, KeyPair pair, String keyId) throws Exception {
        ObjectNode header = SubmissionJson.object().put("alg", "RS256").put("typ", "JWT").put("kid", keyId);
        ObjectNode claims = SubmissionJson.object().put("iss", E2aProtocol.ISSUER).put("aud", E2aProtocol.AUDIENCE)
            .set("receipt", receipt.deepCopy());
        String input = encode(header) + "." + encode(claims);
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(pair.getPrivate());
        signer.update(input.getBytes(StandardCharsets.US_ASCII));
        return input + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign());
    }

    private static boolean hashSucceeds(ObjectNode value) { try { E2aProtocol.pageHash(value); return true; } catch (RuntimeException e) { return false; } }
    private static boolean hashesSucceed(ObjectNode value) { try { E2aProtocol.manifestHashes(value); return true; } catch (RuntimeException e) { return false; } }
    private static void report(String name, boolean result) { System.out.println("case=" + name + " result=" + result); }

    private static String encode(ObjectNode node) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
            SubmissionJson.canonical(node).getBytes(StandardCharsets.UTF_8));
    }

    private static String hex(byte[] bytes) throws Exception {
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder result = new StringBuilder(64);
        for (byte value : digest) result.append(String.format("%02x", value));
        return result.toString();
    }
}
