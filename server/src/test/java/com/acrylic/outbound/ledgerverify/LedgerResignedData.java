package com.acrylic.outbound.ledgerverify;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.*;
import java.security.spec.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Deliberately malformed synthetic records: genuine RSA signature plus matching BLOB/event/audit hashes. */
final class LedgerResignedData {
    private final JdbcTemplate jdbc;
    LedgerResignedData(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    void replace(ObjectNode receipt) throws Exception {
        LedgerVerifyEnvironment.verifyConnected(jdbc);
        String priorToken=receipt.remove("signedReceipt").asText();String header=priorToken.split("\\.")[0];
        byte[] unsigned=canonical(receipt);
        var claims=SubmissionJson.object().put("iss","acrylic-outbound").put("aud","acrylic-r06-command-receipt-v1");
        claims.set("receipt",receipt);
        String token=sign(header,canonical(claims));
        byte[] response=canonical(receipt.deepCopy().put("signedReceipt",token));String responseHash=LedgerDatabaseFacts.hash(response);
        var digest=MessageDigest.getInstance("SHA-256");digest.update("ACRYLIC-R06/RECEIPT/1\n".getBytes(StandardCharsets.UTF_8));
        String receiptHash=HexFormat.of().formatHex(digest.digest(unsigned));String id=receipt.path("receiptId").asText();
        assertThat(jdbc.update("UPDATE temporary_command_receipt SET original_response_bytes=?,original_response_sha256=?,unsigned_receipt_sha256=?,signed_receipt=? WHERE id=UUID_TO_BIN(?)",
            response,responseHash,receiptHash,token,id)).isEqualTo(1);
        assertThat(jdbc.update("UPDATE temporary_decision_event SET context_bytes=? WHERE receipt_id=UUID_TO_BIN(?)",unsigned,id)).isEqualTo(1);
        assertThat(jdbc.update("UPDATE operation_event SET details=JSON_SET(details,'$.originalResponseSha256',?) WHERE entity_type='TEMPORARY_COMMAND' AND entity_id=UUID_TO_BIN(?)",
            responseHash,id)).isEqualTo(1);
    }
    private String sign(String header,byte[] claims) throws Exception {
        var encoding=Base64.getUrlEncoder().withoutPadding();String input=header+"."+encoding.encodeToString(claims);
        var factory=KeyFactory.getInstance("RSA");
        var privateKey=factory.generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(LedgerVerifyEnvironment.ROOT.resolve("signing-private-r2.der"))));
        var publicKey=factory.generatePublic(new X509EncodedKeySpec(Files.readAllBytes(LedgerVerifyEnvironment.ROOT.resolve("signing-public-r2.der"))));
        var signature=Signature.getInstance("SHA256withRSA");signature.initSign(privateKey);signature.update(input.getBytes(StandardCharsets.US_ASCII));
        byte[] signed=signature.sign();signature.initVerify(publicKey);signature.update(input.getBytes(StandardCharsets.US_ASCII));
        assertThat(signature.verify(signed)).as("The negative is validly signed, not a cryptographic failure").isTrue();
        return input+"."+encoding.encodeToString(signed);
    }
    private byte[] canonical(ObjectNode value) {
        return com.acrylic.outbound.temporary.protocol.TemporaryProtocol.canonical(SubmissionJson.canonical(value).getBytes(StandardCharsets.UTF_8));
    }
}
