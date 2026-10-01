package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.correction.CorrectionReadSignature.require;

/** Binds stored command bytes, receipt columns and signed response before any read exposes them. */
@Component
final class CorrectionReadReceipt {
    private final OfflineSigner signer;

    CorrectionReadReceipt(OfflineSigner signer) { this.signer = signer; }

    Verified verify(Map<String, Object> row) {
        try {
            byte[] bytes = (byte[]) row.get("response_bytes");
            byte[] commandBytes = (byte[]) row.get("canonical_command_bytes");
            require(bytes != null && commandBytes != null);
            var receipt = SubmissionJson.parse(bytes);
            var command = CorrectionCommand.Input.parse(commandBytes);
            require(Arrays.equals(commandBytes, command.bytes()) && Arrays.equals(bytes, canonical(receipt)));
            require(CorrectionCommand.sha(bytes).equals(value(row, "response_sha256"))
                && command.digest().equals(value(row, "command_sha256")));
            bind(row, receipt, command);
            var unsigned = (ObjectNode) receipt.deepCopy();
            unsigned.remove("signedReceipt");
            validateShape(unsigned);
            CorrectionReadSignature.verify(receipt.path("signedReceipt").asText(), unsigned, signer);
            return new Verified(command.body(), receipt, bytes);
        } catch (BusinessException failure) {
            if ("SIGNING_KEY_UNAVAILABLE".equals(failure.code())) throw failure;
            throw CorrectionCommand.corrupt();
        } catch (RuntimeException failure) {
            throw CorrectionCommand.corrupt();
        }
    }

    private void validateShape(JsonNode unsigned) {
        var required = Set.of("receiptVersion", "receiptId", "commandId", "routeScope", "commandType", "commandSha256",
            "status", "recordedAt", "eventId", "documentId", "orderId", "context", "actor", "businessDecision", "expected", "capabilities");
        var actual = new HashSet<String>();
        unsigned.fieldNames().forEachRemaining(actual::add);
        require(actual.equals(required) && unsigned.path("receiptVersion").asInt() == 1
            && "RECORDED".equals(unsigned.path("status").asText())
            && CorrectionCommand.ROUTE_SCOPE.equals(unsigned.path("routeScope").asText())
            && unsigned.path("capabilities").equals(CorrectionReceiptCodec.capabilities()));
    }

    private void bind(Map<String, Object> row, JsonNode receipt, CorrectionCommand.Input command) {
        for (var pair : Map.of("receiptId", "id", "commandId", "original_key", "commandType", "command_type",
                "commandSha256", "command_sha256", "eventId", "event_id", "documentId", "document_id", "orderId", "order_id").entrySet())
            require(Objects.equals(receipt.path(pair.getKey()).asText(null), value(row, pair.getValue())));
        require(command.key().equals(receipt.path("commandId").asText())
            && command.kind().equals(receipt.path("commandType").asText())
            && command.expected().equals(receipt.path("expected"))
            && command.expected().equals(SubmissionJson.parse(row.get("expected_json").toString())));
        require(value(row, "actor_id").equals(receipt.path("actor").path("principalId").asText())
            && command.text("warehouseId").equals(receipt.path("actor").path("warehouseId").asText())
            && "RECORDED".equals(value(row, "status")));
        require(recorded(row.get("recorded_at")).equals(Instant.parse(receipt.path("recordedAt").asText())));
        for (String field : List.of("documentId", "orderId"))
            if (command.body().hasNonNull(field)) require(command.text(field).equals(receipt.path(field).asText()));
    }

    private Instant recorded(Object value) {
        if (value instanceof java.sql.Timestamp stamp) return stamp.toInstant();
        if (value instanceof LocalDateTime time) return time.toInstant(ZoneOffset.UTC);
        throw CorrectionCommand.corrupt();
    }

    private byte[] canonical(JsonNode node) {
        return SubmissionJson.canonical(node).getBytes(StandardCharsets.UTF_8);
    }

    private static String value(Map<String, Object> row, String field) {
        Object value = row.get(field);
        if (value == null) return null;
        if (!(value instanceof byte[] bytes)) return value.toString();
        if (bytes.length != 16) return HexFormat.of().formatHex(bytes);
        var buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong()).toString();
    }

    record Verified(JsonNode command, JsonNode receipt, byte[] bytes) {
        Verified {
            command = command.deepCopy();
            receipt = receipt.deepCopy();
            bytes = bytes.clone();
        }

        @Override public byte[] bytes() { return bytes.clone(); }
    }
}
