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
        String kind=receipt.path("commandType").asText();
        require(Set.of("NORMAL_CONFIRM","SHORT_APPROVE","LINK","REJECT","PHYSICAL_DISPOSITION","CLOSE_DISPOSITION","REOPEN_WORK","ACTIVATE_CONTINUATION").contains(kind) && decision.path("kind").asText().equals(kind));
        if (kind.equals("REOPEN_WORK")) {
            fields(decision,"kind status ticketId fromWorkRound toWorkRound evidenceSetId verificationId retainedManifestSha256 retainedManifestVersion nextOrderScanSeq products");
            require(decision.path("status").asText().equals("TICKET_ISSUED") && decision.path("products").isArray()
                && !decision.path("products").isEmpty());
            uuid(decision.path("ticketId")); uuid(decision.path("evidenceSetId")); uuid(decision.path("verificationId"));
            hash(decision.path("retainedManifestSha256")); positive(decision.path("retainedManifestVersion"));
            positive(decision.path("fromWorkRound")); positive(decision.path("toWorkRound"));
            require(decision.path("toWorkRound").asLong()==Math.addExact(decision.path("fromWorkRound").asLong(),1));
            positive(decision.path("nextOrderScanSeq"));
            for (var item : decision.path("products")) {
                fields(item,"baleId expectedDispatchCycle recordSha256 disposition"); uuid(item.path("baleId"));
                positive(item.path("expectedDispatchCycle")); hash(item.path("recordSha256"));
                require(item.path("disposition").asText().equals("RETAIN"));
            }
            return;
        }
        if (kind.equals("ACTIVATE_CONTINUATION")) {
            fields(decision,"kind status ticketId fromWorkRound toWorkRound nextOrderScanSeq newAuthorizationId newBundleId newClientOrderId targetDeviceId targetOperatorId scopeId retainedManifestSha256 retainedManifestVersion authorization retained");
            require(decision.path("status").asText().equals("ACTIVATED") && decision.path("retained").isArray() && !decision.path("retained").isEmpty());
            for (String key : List.of("ticketId","newAuthorizationId","newBundleId","newClientOrderId","targetDeviceId","targetOperatorId","scopeId")) uuid(decision.path(key));
            hash(decision.path("retainedManifestSha256")); positive(decision.path("retainedManifestVersion"));
            positive(decision.path("fromWorkRound")); positive(decision.path("toWorkRound")); positive(decision.path("nextOrderScanSeq"));
            require(decision.path("toWorkRound").asLong()==Math.addExact(decision.path("fromWorkRound").asLong(),1));
            fields(decision.path("authorization"),"authorizationId bundleId offlineToken manifest manifestSha256 signedManifest");
            require(decision.path("authorization").path("authorizationId").asText().equals(decision.path("newAuthorizationId").asText())
                && decision.path("authorization").path("bundleId").asText().equals(decision.path("newBundleId").asText())
                && decision.path("authorization").path("manifest").isTextual()
                && decision.path("authorization").path("offlineToken").isTextual()
                && decision.path("authorization").path("signedManifest").isTextual());
            hash(decision.path("authorization").path("manifestSha256"));
            for (var item : decision.path("retained")) {
                fields(item,"baleId expectedDispatchCycle recordSha256"); uuid(item.path("baleId"));
                positive(item.path("expectedDispatchCycle")); hash(item.path("recordSha256"));
            }
            return;
        }
        if (kind.equals("PHYSICAL_DISPOSITION")) {
            fields(decision,"kind products shortShipmentReason shortApprovalReceiptId shortApprovalSha256 submissionReceipt dispositionId evidenceSetId verificationId manifestSha256 manifestVersion");
            require(decision.path("products").isArray() && decision.path("shortShipmentReason").asText().isEmpty()
                && decision.path("shortApprovalReceiptId").isNull() && decision.path("shortApprovalSha256").isNull()
                && decision.path("submissionReceipt").isNull());
            uuid(decision.path("dispositionId")); uuid(decision.path("evidenceSetId")); uuid(decision.path("verificationId"));
            hash(decision.path("manifestSha256")); require(decision.path("manifestVersion").asText().matches("[1-9][0-9]*"));
            return;
        }
        if (kind.equals("CLOSE_DISPOSITION")) {
            fields(decision,"kind status scopeClosed slotClosed products dispositionId evidenceSetId verificationId closeReason manifestSha256 manifestVersion");
            require(decision.path("status").asText().equals("CANCELLED") && decision.path("scopeClosed").asBoolean()
                && decision.path("products").isArray() && decision.path("closeReason").isTextual()
                && !decision.path("closeReason").asText().isBlank() && decision.path("closeReason").asText().length() <= 1000);
            uuid(decision.path("dispositionId")); uuid(decision.path("evidenceSetId")); uuid(decision.path("verificationId"));
            hash(decision.path("manifestSha256")); require(decision.path("manifestVersion").asText().matches("[1-9][0-9]*"));
            return;
        }
        fields(decision,"kind candidates products shortShipmentReason shortApprovalReceiptId shortApprovalSha256 submissionReceipt");
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
            var link=decision.path("submissionReceipt");
            boolean relink=link.path("applyState").asText().equals("APPLIED") && link.path("sourceMappingVersion").asLong()>1;
            String expectedTotal=relink?"2":"1";
            require(decision.path("submissionReceipt").isObject() && candidates.path("total").asText().equals(expectedTotal)
                && decision.path("shortApprovalReceiptId").isNull() && decision.path("shortApprovalSha256").isNull()
                && decision.path("shortShipmentReason").asText().isEmpty());
            link(decision.path("submissionReceipt"),candidates.path("contextSha256"));
            return;
        }
        if(kind.equals("REJECT")) {
            require(decision.path("submissionReceipt").isNull()
                && decision.path("shortApprovalReceiptId").isNull()
                && decision.path("shortApprovalSha256").isNull());
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
    private static void positive(JsonNode value) { require(value.isTextual() && value.asText().matches("[1-9][0-9]{0,17}")); }
    private static void require(boolean value) { CommandErrors.integrity(value); }
}
