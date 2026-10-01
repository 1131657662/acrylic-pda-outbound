package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.ByteBuffer;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.handover.HandoverCommand.corruptUnless;

/** Binds exact stored bytes and all read-relevant columns to the existing signed receipt. */
@Component
class HandoverReadReceipt {
    private final HandoverReceiptCodec codec;
    HandoverReadReceipt(HandoverReceiptCodec codec) { this.codec = codec; }

    Verified verify(Map<String, Object> row) {
        try {
            byte[] bytes = (byte[]) row.get("response_bytes");
            byte[] commandBytes = (byte[]) row.get("canonical_command_bytes");
            corruptUnless(bytes != null && commandBytes != null);
            var receipt = codec.verify(row);
            var command = HandoverCommand.Input.parse(commandBytes);
            corruptUnless(Arrays.equals(bytes, HandoverReceiptCodec.bytes(SubmissionJson.parse(bytes)))
                && Arrays.equals(commandBytes, command.bytes())
                && command.digest().equals(value(row, "command_sha256")));
            bind(row, receipt, command);
            return new Verified(command.body(), receipt, bytes);
        } catch (BusinessException failure) {
            if ("SIGNING_KEY_UNAVAILABLE".equals(failure.code())) throw failure;
            throw HandoverCommand.corrupt();
        } catch (RuntimeException failure) { throw HandoverCommand.corrupt(); }
    }

    private void bind(Map<String, Object> row, JsonNode receipt, HandoverCommand.Input command) {
        corruptUnless(receipt.path("receiptVersion").asInt() == 1
            && "RECORDED".equals(receipt.path("status").asText())
            && HandoverCommand.ROUTE_SCOPE.equals(receipt.path("routeScope").asText())
            && command.key().equals(receipt.path("commandId").asText())
            && command.kind().equals(receipt.path("commandType").asText()));
        corruptUnless(command.expected().equals(receipt.path("expected"))
            && command.expected().equals(SubmissionJson.parse(row.get("expected_json").toString()))
            && value(row, "event_id").equals(receipt.path("eventId").asText())
            && value(row, "actor_id").equals(receipt.path("actor").path("principalId").asText())
            && "RECORDED".equals(value(row, "status"))
            && recorded(row.get("recorded_at")).equals(Instant.parse(receipt.path("recordedAt").asText())));
        String warehouse = command.text("warehouseId"), caseId = value(row, "case_id");
        corruptUnless(warehouse.equals(receipt.path("actor").path("warehouseId").asText())
            && warehouse.equals(receipt.path("caseContext").path("warehouseId").asText())
            && caseId.equals(receipt.path("businessDecision").path("caseId").asText()));
        if (command.kind().equals("REQUEST")) {
            corruptUnless(command.text("scopeId").equals(receipt.path("caseContext").path("scopeId").asText()));
        } else {
            corruptUnless(caseId.equals(command.text("caseId"))
                && caseId.equals(receipt.path("caseContext").path("caseId").asText()));
        }
    }

    private Instant recorded(Object value) {
        if (value instanceof java.sql.Timestamp stamp) return stamp.toInstant();
        if (value instanceof LocalDateTime time) return time.toInstant(ZoneOffset.UTC);
        throw HandoverCommand.corrupt();
    }

    private String value(Map<String, Object> row, String field) {
        Object value = row.get(field);
        if (value == null) return null;
        if (!(value instanceof byte[] bytes)) return value.toString();
        if (bytes.length != 16) return HexFormat.of().formatHex(bytes);
        var buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong()).toString();
    }

    record Verified(JsonNode command, JsonNode receipt, byte[] bytes) {
        Verified { command = command.deepCopy(); receipt = receipt.deepCopy(); bytes = bytes.clone(); }
        @Override public byte[] bytes() { return bytes.clone(); }
    }
}
