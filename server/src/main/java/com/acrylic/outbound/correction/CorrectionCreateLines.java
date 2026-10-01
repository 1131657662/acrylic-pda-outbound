package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.*;
import java.util.*;

/** Freezes authoritative before/target values and validates existing evidence limits. */
final class CorrectionCreateLines {
    private final CorrectionStore store;
    CorrectionCreateLines(CorrectionStore store) { this.store = store; }

    List<ObjectNode> freeze(CorrectionCommand.Write write, UUID document, JsonNode lines) {
        var result = new ArrayList<ObjectNode>(); long attachmentBytes = 0;
        Instant shipment = store.shipmentCreatedAt(write.orderId());
        for (var line : lines) {
            attachmentBytes = Math.addExact(attachmentBytes, attachments(line));
            if (attachmentBytes > 50L * 1024 * 1024) throw limit("单次提交附件总量不得超过 50MB");
            result.add(freezeLine(write, new Position(document, result.size() + 1, shipment), line));
        }
        return result;
    }

    private ObjectNode freezeLine(CorrectionCommand.Write write, Position p, JsonNode line) {
        UUID dispatch = uuid(line, "dispatchId");
        var facts = dispatch == null ? null : store.dispatchFacts(write.orderId(), dispatch);
        long beforeCount = facts == null ? 0 : facts.path("count").asLong();
        long beforeWeight = facts == null ? 0 : facts.path("weightG").asLong();
        long targetCount = number(line, "targetCount", beforeCount), targetWeight = number(line, "targetWeightG", beforeWeight);
        if (targetCount < 0 || targetWeight < 0) throw invalid("目标值不能为负");
        if (targetCount == beforeCount && targetWeight == beforeWeight) throw CorrectionCommand.targetUnchanged();
        String time = text(line, "businessOccurredAt", null);
        Instant occurred = time == null || time.isBlank() ? write.recordedAt() : Instant.parse(time);
        if (p.shipment() != null && (occurred.isBefore(p.shipment().minus(Duration.ofDays(3)))
            || occurred.isAfter(write.recordedAt().plus(Duration.ofMinutes(5))))) throw CorrectionCommand.windowExceeded();
        UUID evidence = uuid(line, "evidenceId"); String sha = text(line, "evidenceSha256", null);
        if (dispatch != null && Set.of("PHYSICAL_RETURN", "DISPATCH_CANCELLATION").contains(write.command().body().path("type").asText())
            && (evidence == null || sha == null)) throw CorrectionCommand.evidenceRequired();
        UUID id = Ids.next();
        store.insertLine(id, p.document(), p.number(), dispatch, facts == null ? null : UUID.fromString(facts.path("baleId").asText()),
            facts == null ? null : facts.path("deliveryCode").asText(), beforeCount, beforeWeight, targetCount, targetWeight, evidence, sha, occurred);
        return SubmissionJson.object().put("lineNo", p.number()).put("lineId", id.toString())
            .put("dispatchId", dispatch == null ? null : dispatch.toString()).put("beforeCount", Long.toString(beforeCount))
            .put("beforeWeightG", Long.toString(beforeWeight)).put("targetCount", Long.toString(targetCount)).put("targetWeightG", Long.toString(targetWeight))
            .put("countDelta", Long.toString(targetCount - beforeCount)).put("weightDeltaG", Long.toString(targetWeight - beforeWeight))
            .put("businessOccurredAt", occurred.toString());
    }

    private long attachments(JsonNode line) {
        var attachments = line.path("attachments");
        if (attachments.isMissingNode() || attachments.isNull()) return 0;
        if (!attachments.isArray() || attachments.isEmpty() || attachments.size() > 5) throw limit("单条凭据最多 5 个附件");
        long bytes = 0; boolean referenced = false; String evidence = text(line, "evidenceSha256", null);
        for (var attachment : attachments) {
            long size = number(attachment, "sizeBytes", -1); String sha = text(attachment, "evidenceSha256", null);
            if (size < 0 || sha == null) throw invalid("附件项必须给出 evidenceSha256 与 sizeBytes");
            if (size > 10L * 1024 * 1024) throw limit("单个附件不得超过 10MB");
            bytes = Math.addExact(bytes, size); if (sha.equals(evidence)) referenced = true;
        }
        if (evidence != null && !referenced) throw invalid("明细引用的凭据不在附件清单中");
        return bytes;
    }

    private record Position(UUID document, int number, Instant shipment) { }
    private static BusinessException invalid(String message) { return new BusinessException(400, "INVALID_CORRECTION_COMMAND", message); }
    private static BusinessException limit(String message) { return new BusinessException(400, "ATTACHMENT_LIMIT_EXCEEDED", message); }
    private static String text(JsonNode node, String field, String fallback) {
        var value = node.get(field); return value == null || value.isNull() ? fallback : value.asText();
    }
    private static UUID uuid(JsonNode node, String field) {
        String value = text(node, field, null); return value == null || value.isBlank() ? null : UUID.fromString(value);
    }
    private static long number(JsonNode node, String field, long fallback) {
        String value = text(node, field, null); return value == null || value.isBlank() ? fallback : Long.parseLong(value);
    }
}
