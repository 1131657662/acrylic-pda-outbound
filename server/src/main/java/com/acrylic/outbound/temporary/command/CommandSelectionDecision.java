package com.acrylic.outbound.temporary.command;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;

/** Optional selection proof extends historical decisions without changing the full candidate digest. */
final class CommandSelectionDecision {
    private CommandSelectionDecision() { }
    static long total(JsonNode receipt) {
        var decision=receipt.path("businessDecision");long total=decision.path("candidates").path("total").asLong(-1);
        if(!decision.has("selection"))return total;
        CommandErrors.integrity(Set.of("LINK","NORMAL_CONFIRM","SHORT_APPROVE").contains(receipt.path("commandType").asText()));
        var selection=decision.path("selection");fields(selection,"effectiveTotal exclusions");
        var references=selection.path("exclusions");
        CommandErrors.integrity(references.isArray() && !references.isEmpty() && references.size()<=1000);
        String previous="";
        for(var ref:references) {
            fields(ref,"orderId receiptId originalResponseSha256 resultingDecisionVersion");uuid(ref.path("orderId"));uuid(ref.path("receiptId"));
            CommandErrors.integrity(ref.path("orderId").asText().compareTo(previous)>0);previous=ref.path("orderId").asText();
            CommandErrors.integrity(ref.path("originalResponseSha256").asText().matches("[a-f0-9]{64}"));
            positive(ref.path("resultingDecisionVersion"));
            var outcome=decision.path("submissionReceipt");
            CommandErrors.integrity(!ref.path("orderId").equals(outcome.path("targetOrderId")) && !ref.path("orderId").equals(outcome.path("orderId")));
        }
        CommandErrors.integrity(selection.path("effectiveTotal").isTextual()
            && selection.path("effectiveTotal").asText().matches("0|[1-9][0-9]{0,17}")
            && selection.path("effectiveTotal").asLong(-1)==total-references.size());
        return selection.path("effectiveTotal").asLong();
    }
    static void bind(JsonNode context,JsonNode candidates,JsonNode exclusion) {
        CommandErrors.integrity(exclusion.path("commandType").asText().equals("DUPLICATE_EXCLUDE")
            && candidates.equals(exclusion.at("/businessDecision/candidates")));
        for(String field:List.of("scopeId","warehouseId","submissionId","workRound","receivedPayloadSha256","planSha256",
            "creatorDeviceId","clientOrderId","authorizationId","freezeDeviceId","sourceRecoveryEpoch"))
            CommandErrors.integrity(context.path(field).equals(exclusion.path("originalContext").path(field)));
        CommandErrors.integrity(exclusion.path("resultingDecisionVersion").asLong(-1)<=context.path("decisionVersion").asLong(-2));
    }
    static void references(JsonNode receipt,CommandStore store,Function<CommandRecord,JsonNode> verify) {
        var decision=receipt.path("businessDecision");if(!decision.has("selection"))return;
        for(var reference:decision.path("selection").path("exclusions")) {
            var row=store.byReceipt(UUID.fromString(reference.path("receiptId").asText()));
            CommandErrors.integrity(row.text("command_type").equals("DUPLICATE_EXCLUDE"));
            var exclusion=verify.apply(row);bind(receipt.path("originalContext"),decision.path("candidates"),exclusion);
            CommandErrors.integrity(reference.path("orderId").equals(exclusion.at("/businessDecision/exclusion/orderId"))
                && reference.path("resultingDecisionVersion").equals(exclusion.path("resultingDecisionVersion"))
                && reference.path("originalResponseSha256").asText().equals(row.text("original_response_sha256"))
                && !Instant.parse(exclusion.path("recordedAt").asText()).isAfter(Instant.parse(receipt.path("recordedAt").asText())));
        }
    }
    private static void fields(JsonNode value,String names) {
        CommandErrors.integrity(value.isObject());var actual=new HashSet<String>();value.fieldNames().forEachRemaining(actual::add);
        CommandErrors.integrity(actual.equals(Set.of(names.split(" "))));
    }
    private static void uuid(JsonNode value) { CommandErrors.integrity(value.isTextual() && UUID.fromString(value.asText()).toString().equals(value.asText())); }
    private static void positive(JsonNode value) { CommandErrors.integrity(value.isTextual() && value.asText().matches("[1-9][0-9]{0,17}")); }
}
