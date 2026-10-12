package com.acrylic.outbound.correction;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.TreeSet;

/** Restores only actual scalar contributions; every intervening snapshot protects against ABA. */
final class CorrectionHeaderCompensation {
    private CorrectionHeaderCompensation() { }

    static ObjectNode restore(JsonNode before, JsonNode applied, List<JsonNode> history) {
        if (!before.isObject() || !applied.isObject() || history.isEmpty()) throw CorrectionCommand.corrupt();
        var changed = new TreeSet<String>();
        applied.fields().forEachRemaining(field -> {
            var previous = before.get(field.getKey());
            if (previous == null || !previous.isValueNode() || !field.getValue().isValueNode()) return;
            if (!previous.equals(field.getValue())) changed.add(field.getKey());
        });
        if (changed.isEmpty()) throw CorrectionCommand.corrupt();
        for (var snapshot : history) {
            if (!snapshot.isObject()) throw CorrectionCommand.corrupt();
            for (String field : changed)
                if (!applied.get(field).equals(snapshot.get(field))) throw CorrectionCommand.ledgerConflict();
        }
        ObjectNode restored = history.getLast().deepCopy();
        changed.forEach(field -> restored.set(field, before.get(field)));
        return restored;
    }

    static ObjectNode proof(ProofInput input) {
        var proof = SubmissionJson.object().put("createReceiptId", input.createReceipt())
            .put("approveReceiptId", input.approveReceipt()).put("originalResultVersion", Long.toString(input.originalVersion()))
            .put("effectiveResultVersion", Long.toString(input.effectiveVersion()))
            .put("previousResultVersion", Long.toString(input.previousVersion()))
            .put("originalHeaderSha256", hash(input.before())).put("effectiveHeaderSha256", hash(input.applied()))
            .put("previousHeaderSha256", hash(input.previous())).put("compensatedHeaderSha256", hash(input.restored()));
        var fields = proof.putArray("restoredFields");
        var keys = new TreeSet<String>(); input.restored().fieldNames().forEachRemaining(keys::add);
        keys.stream().filter(key -> !input.previous().get(key).equals(input.restored().get(key))).forEach(fields::add);
        return proof;
    }

    static String hash(JsonNode snapshot) {
        return CorrectionCommand.sha(SubmissionJson.canonical(snapshot).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    record ProofInput(String createReceipt, String approveReceipt, long originalVersion, long effectiveVersion,
        long previousVersion, JsonNode before, JsonNode applied, JsonNode previous, JsonNode restored) { }
}
