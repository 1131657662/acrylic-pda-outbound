package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.pda.*;
import java.nio.file.*;
import java.security.KeyPairGenerator;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

class HandoverTemporaryStopReceiptsTest {
    @TempDir Path directory;
    private final UUID kase = UUID.randomUUID(), device = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-07T00:00:00Z");

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void originalLegacyOrSplitWatermarkReceiptVerifiesWithoutRewriting(boolean split) throws Exception {
        var command = command(split); var origin = origin(); var receipts = receipts();
        var context = new HandoverStopAccess.Context(origin,
            new PdaSessions.Principal(UUID.randomUUID(), origin.operator(), "fixture", device, origin.warehouse(), "1", "1"),
            object().put("caseId", kase.toString()).put("caseVersion", "1"));
        var stopId = UUID.randomUUID(); var packId = UUID.randomUUID();
        byte[] original = receipts.sign(command, context, HandoverTemporaryStopEvidence.descriptor(command, origin), stopId, packId, now);
        var stop = row("STOP_PROOF", stopId, HandoverCommand.sha(original), command.inputHigh(), original);
        var pack = row("RECOVERY_PACKAGE", packId, command.payloadHash(), command.inputHigh(), original);
        var verified = receipts.verify(stop, pack, context);
        assertThat(HandoverReceiptCodec.bytes(verified)).isEqualTo(original);
        assertThat(verified.path("temporaryStop").has("orderScanHighWatermark")).isEqualTo(split);
        stop.put("high_watermark", 7L);
        assertThatThrownBy(() -> receipts.verify(stop, pack, context)).isInstanceOfSatisfying(BusinessException.class,
            error -> assertThat(error.code()).isEqualTo("HANDOVER_RECEIPT_INTEGRITY_FAILURE"));
    }

    private HandoverStopCommand command(boolean split) {
        var body = object().put("commandId", UUID.randomUUID().toString()).put("expectedCaseVersion", "1")
            .put("sourceKind", "TEMP").put("sourceEvidenceSetId", UUID.randomUUID().toString())
            .put("verificationId", UUID.randomUUID().toString()).put("setSha256", "a".repeat(64))
            .put("captureHighWatermark", split ? "7" : "2").put("auditThroughSeq", "8");
        if (split) body.put("orderScanHighWatermark", "2");
        return HandoverStopCommand.parse(kase, "1", HandoverReceiptCodec.bytes(body));
    }

    private HandoverStopStore.Origin origin() {
        return new HandoverStopStore.Origin(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            null, device, UUID.randomUUID(), "1", 1,
            new HandoverStopStore.Temporary(UUID.randomUUID(), device, UUID.randomUUID()));
    }

    private Map<String, Object> row(String kind, UUID id, String hash, long high, byte[] bytes) {
        var row = new HashMap<String, Object>(); row.put("kind", kind); row.put("id", Ids.bytes(id));
        row.put("case_id", Ids.bytes(kase)); row.put("device_id", Ids.bytes(device)); row.put("high_watermark", high);
        row.put("payload_sha256", HexFormat.of().parseHex(hash)); row.put("recorded_at", Timestamp.from(now));
        row.put("receipt_bytes", bytes); return row;
    }

    private HandoverStopReceipts receipts() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); var pair = generator.generateKeyPair();
        var secret = directory.resolve("private.der"); var pub = directory.resolve("public.der");
        Files.write(secret, pair.getPrivate().getEncoded()); Files.write(pub, pair.getPublic().getEncoded());
        var signer = new OfflineSigner(secret.toString(), pub.toString(), "", new JsonText(new com.fasterxml.jackson.databind.ObjectMapper()));
        return new HandoverStopReceipts(new HandoverStopSignature(signer));
    }
}
