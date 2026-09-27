package com.acrylic.outbound.temporary.command;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** Explicit business semantics coexist with retained synthetic receipts; neither grants release capability. */
final class CommandDecision {
    private CommandDecision() { }
    static String kind(JsonNode receipt) {
        return receipt.path("businessDecision").isNull()?"INTERNAL_TEST_ONLY":receipt.path("businessDecision").path("kind").asText();
    }
    static String eventType(JsonNode receipt) {
        return kind(receipt).equals("INTERNAL_TEST_ONLY")?"INTERNAL_TEST_ONLY":"TEMPORARY_"+kind(receipt);
    }
    static void validate(JsonNode receipt) {
        var decision=receipt.get("businessDecision");
        if(decision.isNull()) { require(receipt.path("status").asText().equals("INTERNAL_TEST_ONLY")); return; }
        require(receipt.path("status").asText().equals("RECORDED") && decision.isObject());
        fields(decision,"kind candidates products shortShipmentReason shortApprovalReceiptId shortApprovalSha256 submissionReceipt");
        String kind=receipt.path("commandType").asText();
        require(Set.of("NORMAL_CONFIRM","SHORT_APPROVE","LINK").contains(kind) && decision.path("kind").asText().equals(kind));
        var candidates=decision.path("candidates");
        fields(candidates,"policyVersion fromDate throughDate total fingerprint contextSha256");
        require(candidates.path("policyVersion").asText().equals("REVIEW_DEFAULT_V1"));
        for(String key:List.of("fromDate","throughDate"))require(candidates.path(key).isTextual() && java.time.LocalDate.parse(candidates.path(key).asText()).toString().equals(candidates.path(key).asText()));
        require(candidates.path("total").isTextual() && candidates.path("total").asText().matches("0|[1-9][0-9]*"));
        for(String key:List.of("fingerprint","contextSha256"))hash(candidates.path(key));
        require(candidates.path("contextSha256").equals(receipt.path("expected").path("candidateContextSha256")));
        require(decision.path("products").isArray() && !decision.path("products").isEmpty() && decision.path("shortShipmentReason").isTextual());
        if(kind.equals("LINK")) {
            // A link is only ever a human decision over a complete, uniquely resolvable candidate set.
            require(decision.path("submissionReceipt").isObject() && candidates.path("total").asText().equals("1")
                && decision.path("shortApprovalReceiptId").isNull() && decision.path("shortApprovalSha256").isNull()
                && decision.path("shortShipmentReason").asText().isEmpty());
            link(decision.path("submissionReceipt"),candidates.path("contextSha256"));
            return;
        }
        if(kind.equals("SHORT_APPROVE"))require(decision.path("submissionReceipt").isNull() && decision.path("shortApprovalReceiptId").isNull() && decision.path("shortApprovalSha256").isNull());
        else {
            // A normal confirmation is recorded either from an untouched source (no duplicate candidate may
            // exist) or from a controlled-linked source, where the reviewed candidate set is exactly the bound
            // target. The snapshot carries no head list, so the identity binding is not proven here: it is
            // enforced at execution time by the candidate-context comparison, and at verification by
            // CommandBusinessIntegrity.success (the receipt orderId must equal the recorded canonical order and
            // the result row must hang off that same order). Any other total stays rejected.
            long total=candidates.path("total").asLong(-1);
            require(decision.path("submissionReceipt").isObject()
                && (total==0 || (total==1 && decision.path("submissionReceipt").path("orderId").isTextual())));
            if(decision.path("shortApprovalReceiptId").isNull())require(decision.path("shortApprovalSha256").isNull());
            else { require(UUID.fromString(decision.path("shortApprovalReceiptId").asText()).toString().equals(decision.path("shortApprovalReceiptId").asText())); hash(decision.path("shortApprovalSha256")); }
        }
    }
    private static void link(JsonNode link,JsonNode contextSha256) {
        String state=link.path("applyState").asText();
        require(state.equals("APPLIED") || state.equals("NOT_APPLIED"));
        fields(link,state.equals("APPLIED")
            ?"applyState targetOrderId targetOrderNo targetRevision targetRowVersion sourceCanonicalOrderId sourceMappingVersion candidateContextSha256 mappingId mappingVersion"
            :"applyState targetOrderId targetOrderNo targetRevision targetRowVersion sourceCanonicalOrderId sourceMappingVersion candidateContextSha256");
        uuid(link.path("targetOrderId"));text(link.path("targetOrderNo"));
        require(link.path("targetRevision").asText().matches("[1-9][0-9]*") && link.path("targetRowVersion").asText().matches("[1-9][0-9]*")
            && link.path("sourceMappingVersion").asText().matches("0|[1-9][0-9]*"));
        if(link.path("sourceCanonicalOrderId").isNull())require(link.path("sourceMappingVersion").asText().equals("0"));
        else uuid(link.path("sourceCanonicalOrderId"));
        require(link.path("candidateContextSha256").equals(contextSha256));
        if(state.equals("APPLIED")) {
            uuid(link.path("mappingId"));
            require(link.path("mappingVersion").asText().matches("[1-9][0-9]*")
                && link.path("mappingVersion").asText().equals(Long.toString(Math.addExact(Long.parseLong(link.path("sourceMappingVersion").asText()),1))));
        }
    }
    private static void uuid(JsonNode node) { require(node.isTextual() && UUID.fromString(node.asText()).toString().equals(node.asText())); }
    private static void text(JsonNode node) { require(node.isTextual()); }
    private static void fields(JsonNode node,String names) {
        require(node.isObject());var actual=new HashSet<String>();node.fieldNames().forEachRemaining(actual::add);
        require(actual.equals(Set.of(names.split(" "))));
    }
    private static void hash(JsonNode value) { require(value.isTextual() && value.asText().matches("[a-f0-9]{64}")); }
    private static void require(boolean value) { CommandErrors.integrity(value); }
}
