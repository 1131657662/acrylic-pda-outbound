package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.pda.*;
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

/** Protocol-only synthetic signatures; the HTTP integration test separately consumes real stop-handler receipts. */
class HandoverStopConsumerTest {
    @TempDir static Path directory;
    private static OfflineSigner signer;
    @BeforeAll static void keys() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); var pair = generator.generateKeyPair();
        var secret = directory.resolve("private.der"); var pub = directory.resolve("public.der");
        Files.write(secret, pair.getPrivate().getEncoded()); Files.write(pub, pair.getPublic().getEncoded());
        signer = new OfflineSigner(secret.toString(), pub.toString(), "", new JsonText(new ObjectMapper()));
    }

    @Test void evidenceConsumerNeedsNoPdaPrincipalAndKeepsOriginalIdentity() {
        var f = new HandoverStopReceiptFixture(signer);
        var result = verifier().verifyEvidence(f.stop, f.pack, f.context.origin(), f.context.facts());
        assertThat(result.path("actor").path("principalId").asText()).isEqualTo(f.context.origin().operator().toString());
        assertThat(HandoverReceiptCodec.bytes(result)).isEqualTo((byte[]) f.stop.get("receipt_bytes"));
    }

    @Test void replayAndSigningStillRejectAnotherCallingActor() {
        var f = new HandoverStopReceiptFixture(signer); var o = f.context.origin();
        var other = new PdaSessions.Principal(UUID.randomUUID(), UUID.randomUUID(), "other", o.device(), o.warehouse(), "1", "1");
        var context = new HandoverStopAccess.Context(o, other, f.context.facts());
        corrupt(() -> verifier().verify(f.stop, f.pack, context));
        corrupt(() -> verifier().sign(f.command, context, f.recall, f.receiptId, f.packageEvidenceId, f.now));
    }

    @ParameterizedTest @ValueSource(strings={"principalId", "deviceId", "source", "case", "epoch", "recall"})
    void supervisorConsumerRejectsEvenResignedUnboundFacts(String field) {
        var f = new HandoverStopReceiptFixture(signer);
        var receipt = (ObjectNode) SubmissionJson.parse((byte[]) f.stop.get("receipt_bytes"));
        switch (field) {
            case "principalId", "deviceId" -> receipt.withObject("/actor").put(field, UUID.randomUUID().toString());
            case "source" -> receipt.withObject("/source").put("submissionId", UUID.randomUUID().toString());
            case "case" -> receipt.withObject("/caseContext").put("caseId", UUID.randomUUID().toString());
            case "epoch" -> receipt.withObject("/originalCommand").put("transportRecoveryEpoch", "2");
            case "recall" -> receipt.withObject("/recallStop").put("actualCount", "0");
            default -> throw new AssertionError(field);
        }
        receipt.remove("signedReceipt");
        byte[] bytes = HandoverReceiptCodec.bytes(new HandoverStopSignature(signer).sign(receipt));
        f.stop.put("receipt_bytes", bytes); f.pack.put("receipt_bytes", bytes);
        f.stop.put("payload_sha256", HexFormat.of().parseHex(HandoverCommand.sha(bytes)));
        corrupt(() -> verifier().verifyEvidence(f.stop, f.pack, f.context.origin(), f.context.facts()));
    }

    private HandoverStopReceipts verifier() { return new HandoverStopReceipts(new HandoverStopSignature(signer)); }
    private void corrupt(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
            error -> assertThat(error.code()).isEqualTo("HANDOVER_RECEIPT_INTEGRITY_FAILURE"));
    }
}
