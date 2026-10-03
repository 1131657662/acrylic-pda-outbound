package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.security.KeyPairGenerator;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class HandoverStopReceiptsTest {
    @TempDir static Path directory;
    private static OfflineSigner signer;
    @BeforeAll static void keys() throws Exception { signer = signer("first", ""); }

    @Test void exactBytesAndRetainedHistoricalKeysVerify() throws Exception {
        var f = new HandoverStopReceiptFixture(signer);
        var rotated = signer("next", directory.resolve("first-public.der").toString());
        var result = verifier(rotated).verify(f.stop, f.pack, f.context);
        assertThat(HandoverReceiptCodec.bytes(result)).isEqualTo((byte[]) f.stop.get("receipt_bytes"));
        result.path("capabilities").forEach(flag -> assertThat(flag.asBoolean()).isFalse());
    }

    @Test void unknownKeyAndMissingCounterpartFailClosed() throws Exception {
        var f = new HandoverStopReceiptFixture(signer);
        corrupt(() -> verifier(signer("unknown", "")).verify(f.stop, f.pack, f.context));
        corrupt(() -> verifier(signer).verify(f.stop, null, f.context));
    }

    @ParameterizedTest @ValueSource(strings={"id", "case_id", "device_id", "digest", "time", "watermark", "kind", "bytes"})
    void rejectsEitherEvidenceRowChanging(String field) {
        for (boolean recovery : List.of(false, true)) {
            var f = new HandoverStopReceiptFixture(signer); var row = recovery ? f.pack : f.stop;
            switch (field) {
                case "id", "case_id", "device_id" -> row.put(field, Ids.bytes(UUID.randomUUID()));
                case "digest" -> row.put("payload_sha256", new byte[32]);
                case "time" -> row.put("recorded_at", java.sql.Timestamp.from(f.now.plusMillis(1)));
                case "watermark" -> row.put("high_watermark", 3L);
                case "kind" -> row.put("kind", "UNKNOWN");
                case "bytes" -> row.put("receipt_bytes", "{}".getBytes());
                default -> throw new AssertionError(field);
            }
            corrupt(() -> verifier(signer).verify(f.stop, f.pack, f.context));
        }
    }

    @ParameterizedTest @ValueSource(strings={"capability", "actor", "source", "digest", "signature", "recall"})
    void rejectsSignedButUnboundOrTamperedReceipt(String damage) {
        var f = new HandoverStopReceiptFixture(signer);
        var receipt = (ObjectNode) SubmissionJson.parse((byte[]) f.stop.get("receipt_bytes"));
        receipt.remove("signedReceipt");
        switch (damage) {
            case "capability" -> receipt.withObject("/capabilities").put("localDeletionAllowed", true);
            case "actor" -> receipt.withObject("/actor").put("deviceId", UUID.randomUUID().toString());
            case "source" -> receipt.withObject("/source").put("workRound", "2");
            case "digest" -> receipt.put("commandSha256", "0".repeat(64));
            case "recall" -> receipt.withObject("/recallStop").put("stockReleased", true);
            case "signature" -> { }
            default -> throw new AssertionError(damage);
        }
        receipt = new HandoverStopSignature(signer).sign(receipt);
        if (damage.equals("signature")) receipt.put("signedReceipt", "bad.signature.parts");
        byte[] bytes = HandoverReceiptCodec.bytes(receipt);
        f.stop.put("receipt_bytes", bytes); f.pack.put("receipt_bytes", bytes);
        f.stop.put("payload_sha256", HexFormat.of().parseHex(HandoverCommand.sha(bytes)));
        corrupt(() -> verifier(signer).verify(f.stop, f.pack, f.context));
    }

    @Test void recallDomainAndBindingAreVerifiedBeforeSigningNewEvidence() {
        var f = new HandoverStopReceiptFixture(signer); f.signRecall(signer, "acrylic-pda-handover-stop");
        corrupt(() -> verifier(signer).sign(f.command, f.context, f.recall, f.receiptId, f.packageEvidenceId, f.now));
        f.recall.put("assignmentId", UUID.randomUUID().toString()); f.signRecall(signer, "acrylic-pda-recall");
        corrupt(() -> verifier(signer).sign(f.command, f.context, f.recall, f.receiptId, f.packageEvidenceId, f.now));
    }

    private HandoverStopReceipts verifier(OfflineSigner keys) { return new HandoverStopReceipts(new HandoverStopSignature(keys)); }
    private void corrupt(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
            error -> assertThat(error.code()).isEqualTo("HANDOVER_RECEIPT_INTEGRITY_FAILURE"));
    }
    private static OfflineSigner signer(String name, String history) throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); var pair = generator.generateKeyPair();
        Path secret = directory.resolve(name + "-private.der"), pub = directory.resolve(name + "-public.der");
        Files.write(secret, pair.getPrivate().getEncoded()); Files.write(pub, pair.getPublic().getEncoded());
        return new OfflineSigner(secret.toString(), pub.toString(), history, new JsonText(new ObjectMapper()));
    }
}
