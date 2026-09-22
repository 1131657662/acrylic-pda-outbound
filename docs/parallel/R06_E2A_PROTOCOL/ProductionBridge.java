package e2a.review;

import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.protocol.E2aProtocol;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;

/** Lightweight bridge invoking the frozen production E2aProtocol class. */
public final class ProductionBridge {
    private ProductionBridge() { }

    public static void main(String[] args) throws Exception {
        stopNegativeMatrix();
        rsaNegativeMatrix();
        shapeNegativeMatrix();
    }

    private static void stopNegativeMatrix() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        KeyPair pair = generator.generateKeyPair();
        ObjectNode validCore = stopCore();
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
        report("rsa_legal_resign_changed_facts_not_trusted", !E2aProtocol.trustedFacts(changedFacts, receipt));

        ObjectNode unknownKid = receipt.deepCopy();
        unknownKid.remove("signedReceipt");
        unknownKid.put("signedReceipt", signReceipt(unknownKid, pair, "unknown-kid"));
        report("rsa_unknown_key_rejected", !"VERIFIED".equals(E2aProtocol.verifyRsaReceipt(unknownKid, view)));

        ObjectNode emptyFacts = receipt.deepCopy();
        emptyFacts.remove("actorId");
        emptyFacts.remove("sourceId");
        emptyFacts.remove("sourceRecoveryEpoch");
        emptyFacts.put("signedReceipt", signReceipt(emptyFacts, pair, keyId));
        report("rsa_empty_facts_signature_valid", "VERIFIED".equals(E2aProtocol.verifyRsaReceipt(emptyFacts, view)));
        report("rsa_empty_facts_not_trusted", !E2aProtocol.trustedFacts(emptyFacts, receipt));

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
            .put("workRound", 1).put("pageCount", 1).put("entryCount", 0).put("byteCount", 0)
            .put("endPageNo", 1);
        manifest.putArray("pages").add(SubmissionJson.object().put("pageNo", 1).put("pageHash", E2aProtocol.pageHash(page)));
        report("manifest_valid_shape", hashesSucceed(manifest));
        report("manifest_derived_field_rejected", !hashesSucceed(manifest.deepCopy().put("setHash", "0".repeat(64))));
    }

    private static ObjectNode stopCore() {
        return SubmissionJson.object().put("manifestId", "manifest-1").put("manifestVersion", 1)
            .put("snapshotId", "snapshot-1").put("source", "TEMP").put("workRound", 1)
            .put("stopEventId", "event-1").put("auditThroughSeq", 4)
            .put("manifestBytesHash", "a".repeat(64)).put("setHash", "b".repeat(64));
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
