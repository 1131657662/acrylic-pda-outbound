package com.acrylic.outbound.recovery;

import com.acrylic.outbound.devices.DeviceCrypto;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.*;
import java.util.*;

/** Generated development keys stay in memory; these receipts do not establish original device facts. */
final class RecoveryReceiptFixture {
    final KeyPair pair;
    final String kid;
    final RecoveryReceiptVerifier verifier;

    RecoveryReceiptFixture() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); pair = generator.generateKeyPair();
        kid = "offline-" + DeviceCrypto.sha(Base64.getEncoder().encodeToString(pair.getPublic().getEncoded())).substring(0, 32);
        verifier = new RecoveryReceiptVerifier(new RecoveryReceiptKeys(List.of(pair.getPublic().getEncoded())));
    }

    ObjectNode receipt(RecoverySourceFixture source, String status) {
        var id = source.identity;
        var value = SubmissionJson.object().put("submissionId", id.submissionId().toString())
            .put("freezeDeviceId", id.deviceId().toString()).put("workRound", Long.toString(id.workRound()))
            .put("issuedRecoveryEpoch", id.sourceRecoveryEpoch()).put("status", status).put("payloadPresent", true)
            .put("captureHighWatermark", Long.toString(id.captureHighWatermark()))
            .put("mayResumeScanning", false).put("stockReleased", false).put("localDeletionAllowed", false)
            .put("payloadSha256", SubmissionJson.sha(SubmissionJson.object().put("original", "body")));
        var origin = value.putObject("source").put("kind", id.sourceKind());
        if (id.sourceKind().equals("FORMAL")) origin.put("sourceOrderId", id.canonicalOrderId().toString());
        else origin.put("creatorDeviceId", id.creatorDeviceId().toString()).put("clientOrderId", id.clientOrderId().toString());
        return value;
    }

    ObjectNode signed(ObjectNode original) throws Exception {
        var unsigned = original.deepCopy(); unsigned.remove("signedReceipt");
        var claims = SubmissionJson.object().put("iss", "acrylic-outbound").put("aud", "acrylic-pda-receipt");
        claims.set("receipt", unsigned);
        var header = SubmissionJson.object().put("alg", "RS256").put("typ", "JWT").put("kid", kid);
        return unsigned.deepCopy().put("signedReceipt", token(header, claims));
    }

    String token(ObjectNode header, ObjectNode claims) throws Exception {
        var encoder = Base64.getUrlEncoder().withoutPadding();
        String input = encoder.encodeToString(bytes(header)) + "." + encoder.encodeToString(bytes(claims));
        var signer = Signature.getInstance("SHA256withRSA"); signer.initSign(pair.getPrivate());
        signer.update(input.getBytes(StandardCharsets.US_ASCII));
        return input + "." + encoder.encodeToString(signer.sign());
    }

    void publish(RecoverySourceFixture source, ObjectNode receipt) throws Exception {
        object(source, "payload", bytes(SubmissionJson.object().put("original", "body")));
        object(source, "result", bytes(signed(receipt))); source.publish();
    }

    void object(RecoverySourceFixture source, String kind, byte[] bytes) throws Exception {
        String hash = RecoveryControlFormat.sha256(bytes); Files.write(source.materials.resolve(hash + ".bin"), bytes);
        ((ObjectNode)source.manifest.at("/objects/" + kind)).put("sha256", hash).put("byteCount", Integer.toString(bytes.length));
    }

    static byte[] bytes(ObjectNode value) { return SubmissionJson.canonical(value).getBytes(StandardCharsets.UTF_8); }
}
