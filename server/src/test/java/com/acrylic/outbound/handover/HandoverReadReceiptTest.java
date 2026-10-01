package com.acrylic.outbound.handover;

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

class HandoverReadReceiptTest {
    @TempDir static Path directory;
    private static OfflineSigner signer;
    private final UUID kase = UUID.randomUUID(), scope = UUID.randomUUID(), actor = UUID.randomUUID();
    private final UUID warehouse = UUID.randomUUID(), key = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-01T00:00:00.123Z");

    @BeforeAll static void keys() throws Exception { signer = keys("original", ""); }

    @ParameterizedTest @ValueSource(strings={"REQUEST", "APPROVE", "PREPARE", "ACTIVATE"})
    void returnsExactOriginalBytesForEachKind(String kind) {
        var row = row(kind);
        assertThat(verifier(signer).verify(row).bytes()).isEqualTo((byte[]) row.get("response_bytes"));
    }

    @Test void retainedHistoricalKeyVerifiesWithoutResigning() throws Exception {
        var rotated = keys("rotated", directory.resolve("original-public.der").toString());
        assertThat(verifier(rotated).verify(row("REQUEST")).command().path("commandId").asText()).isEqualTo(key.toString());
    }

    @Test void unknownKeyFailsClosed() throws Exception { corrupt(verifier(keys("other", "")), row("REQUEST")); }

    @ParameterizedTest @ValueSource(strings={"id", "original_key", "actor_id", "event_id", "case_id"})
    void rejectsRowIdentityMismatch(String field) {
        var row = row("ACTIVATE"); row.put(field, Ids.bytes(UUID.randomUUID())); corrupt(verifier(signer), row);
    }

    @ParameterizedTest
    @ValueSource(strings={"digest", "commandDigest", "command", "expected", "time", "status", "forged", "signature", "noncanonical"})
    void rejectsDamagedOriginal(String damage) {
        var row = row("REQUEST"); var receipt = (ObjectNode) SubmissionJson.parse((byte[]) row.get("response_bytes"));
        switch (damage) {
            case "digest" -> row.put("response_sha256", new byte[32]);
            case "commandDigest" -> row.put("command_sha256", new byte[32]);
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
        corrupt(verifier(signer), row);
    }

    private Map<String, Object> row(String kind) {
        var command = SubmissionJson.object().put("commandVersion", 1).put("commandType", kind)
            .put("commandId", key.toString()).put("warehouseId", warehouse.toString());
        command.put(kind.equals("REQUEST") ? "scopeId" : "caseId", kind.equals("REQUEST") ? scope.toString() : kase.toString());
        command.putObject("expected").put("userAuthVersion", "1");
        var input = HandoverCommand.Input.parse(HandoverReceiptCodec.bytes(command));
        var identity = SubmissionJson.object().put("principalId", actor.toString()).put("warehouseId", warehouse.toString());
        var context = SubmissionJson.object().put("caseId", kase.toString()).put("scopeId", scope.toString())
            .put("warehouseId", warehouse.toString());
        var write = new HandoverCommand.Write(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), kase,
            now, input, context, identity);
        byte[] response = new HandoverReceiptCodec(signer).sign(write, SubmissionJson.object().put("caseId", kase.toString())).original();
        var row = new HashMap<String, Object>();
        row.put("id", Ids.bytes(write.receiptId())); row.put("original_key", Ids.bytes(key));
        row.put("actor_id", Ids.bytes(actor)); row.put("event_id", Ids.bytes(write.eventId())); row.put("case_id", Ids.bytes(kase));
        row.put("command_type", kind); row.put("command_sha256", hash(input.bytes()));
        row.put("canonical_command_bytes", input.bytes()); row.put("response_bytes", response);
        row.put("response_sha256", hash(response)); row.put("expected_json", SubmissionJson.canonical(input.expected()));
        row.put("recorded_at", Timestamp.from(now)); row.put("status", "RECORDED"); return row;
    }

    private HandoverReadReceipt verifier(OfflineSigner keys) { return new HandoverReadReceipt(new HandoverReceiptCodec(keys)); }
    private void replace(Map<String, Object> row, ObjectNode receipt) {
        byte[] bytes = HandoverReceiptCodec.bytes(receipt); row.put("response_bytes", bytes); row.put("response_sha256", hash(bytes));
    }
    private byte[] hash(byte[] value) { return HexFormat.of().parseHex(HandoverCommand.sha(value)); }
    private void corrupt(HandoverReadReceipt verifier, Map<String, Object> row) {
        assertThatThrownBy(() -> verifier.verify(row)).isInstanceOfSatisfying(BusinessException.class,
            error -> assertThat(error.code()).isEqualTo("HANDOVER_RECEIPT_INTEGRITY_FAILURE"));
    }
    private static OfflineSigner keys(String name, String history) throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); var pair = generator.generateKeyPair();
        Path privateFile = directory.resolve(name + "-private.der"), publicFile = directory.resolve(name + "-public.der");
        Files.write(privateFile, pair.getPrivate().getEncoded()); Files.write(publicFile, pair.getPublic().getEncoded());
        return new OfflineSigner(privateFile.toString(), publicFile.toString(), history, new JsonText(new ObjectMapper()));
    }
}
