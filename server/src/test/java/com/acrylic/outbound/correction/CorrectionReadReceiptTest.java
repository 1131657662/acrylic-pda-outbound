package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyPairGenerator;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class CorrectionReadReceiptTest {
    @TempDir static Path directory;
    private static OfflineSigner signer;
    private final UUID order = UUID.randomUUID(), document = UUID.randomUUID(), actor = UUID.randomUUID();
    private final UUID warehouse = UUID.randomUUID(), key = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-01T00:00:00.123Z");

    @BeforeAll static void keys() throws Exception { signer = keys("original", ""); }

    @Test void returnsExactOriginalBytes() {
        var row = row();
        assertThat(new CorrectionReadReceipt(signer).verify(row).bytes()).isEqualTo((byte[]) row.get("response_bytes"));
    }

    @Test void retainsHistoricalKeyVerificationAfterRotation() throws Exception {
        var rotated = keys("rotated", directory.resolve("original-public.der").toString());
        assertThat(new CorrectionReadReceipt(rotated).verify(row()).command().path("commandId").asText()).isEqualTo(key.toString());
    }

    @Test void unknownSigningKeyFailsClosed() throws Exception {
        var other = keys("unknown", "");
        corrupt(new CorrectionReadReceipt(other), row());
    }

    @ParameterizedTest
    @ValueSource(strings={"id", "original_key", "actor_id", "event_id", "order_id", "document_id"})
    void rejectsRowIdentityMismatch(String field) {
        var row = row(); row.put(field, Ids.bytes(UUID.randomUUID()));
        corrupt(new CorrectionReadReceipt(signer), row);
    }

    @ParameterizedTest
    @ValueSource(strings={"digest", "command", "expected", "time", "status", "forged", "signature", "noncanonical"})
    void rejectsDamagedOriginal(String damage) {
        var row = row(); var receipt = (ObjectNode) SubmissionJson.parse((byte[]) row.get("response_bytes"));
        switch (damage) {
            case "digest" -> row.put("response_sha256", new byte[32]);
            case "command" -> row.put("canonical_command_bytes", "{}".getBytes(StandardCharsets.UTF_8));
            case "expected" -> row.put("expected_json", "{\"userAuthVersion\":\"9\"}");
            case "time" -> row.put("recorded_at", Timestamp.from(now.plusMillis(1)));
            case "status" -> row.put("status", "OTHER");
            case "forged" -> { receipt.withObject("/capabilities").put("localDeletionAllowed", true); replace(row, receipt); }
            case "signature" -> { receipt.put("signedReceipt", "bad.signature.parts"); replace(row, receipt); }
            case "noncanonical" -> {
                byte[] bytes = (" " + new String((byte[]) row.get("response_bytes"), StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
                row.put("response_bytes", bytes); row.put("response_sha256", hash(bytes));
            }
            default -> throw new AssertionError(damage);
        }
        corrupt(new CorrectionReadReceipt(signer), row);
    }

    private Map<String, Object> row() {
        var command = SubmissionJson.object().put("commandVersion", 1).put("commandType", "CREATE")
            .put("commandId", key.toString()).put("warehouseId", warehouse.toString()).put("orderId", order.toString());
        command.putObject("expected").put("userAuthVersion", "1");
        var input = CorrectionCommand.Input.parse(SubmissionJson.canonical(command).getBytes(StandardCharsets.UTF_8));
        var identity = SubmissionJson.object().put("principalId", actor.toString()).put("warehouseId", warehouse.toString());
        var write = new CorrectionCommand.Write(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), document, order,
            now, input, SubmissionJson.object(), identity);
        byte[] response = new CorrectionReceiptCodec(signer).sign(write, SubmissionJson.object()).original();
        var row = new HashMap<String, Object>();
        row.put("id", Ids.bytes(write.receiptId())); row.put("original_key", Ids.bytes(key));
        row.put("actor_id", Ids.bytes(actor)); row.put("event_id", Ids.bytes(write.eventId()));
        row.put("document_id", Ids.bytes(document)); row.put("order_id", Ids.bytes(order));
        row.put("command_type", "CREATE"); row.put("command_sha256", hash(input.bytes()));
        row.put("canonical_command_bytes", input.bytes()); row.put("response_bytes", response);
        row.put("response_sha256", hash(response)); row.put("expected_json", SubmissionJson.canonical(input.expected()));
        row.put("recorded_at", Timestamp.from(now)); row.put("status", "RECORDED"); return row;
    }

    private void replace(Map<String, Object> row, ObjectNode receipt) {
        byte[] bytes = SubmissionJson.canonical(receipt).getBytes(StandardCharsets.UTF_8);
        row.put("response_bytes", bytes); row.put("response_sha256", hash(bytes));
    }

    private byte[] hash(byte[] value) { return HexFormat.of().parseHex(CorrectionCommand.sha(value)); }

    private void corrupt(CorrectionReadReceipt verifier, Map<String, Object> row) {
        assertThatThrownBy(() -> verifier.verify(row)).isInstanceOfSatisfying(BusinessException.class,
            error -> assertThat(error.code()).isEqualTo("CORRECTION_RECEIPT_INTEGRITY_FAILURE"));
    }

    private static OfflineSigner keys(String name, String history) throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); var pair = generator.generateKeyPair();
        Path privateFile = directory.resolve(name + "-private.der"), publicFile = directory.resolve(name + "-public.der");
        Files.write(privateFile, pair.getPrivate().getEncoded()); Files.write(publicFile, pair.getPublic().getEncoded());
        return new OfflineSigner(privateFile.toString(), publicFile.toString(), history, new JsonText(new ObjectMapper()));
    }
}
