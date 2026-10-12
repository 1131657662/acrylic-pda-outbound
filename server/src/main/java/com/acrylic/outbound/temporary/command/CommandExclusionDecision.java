package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.protocol.TemporaryExclusionShape;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;

/** Strict new decision shape, with the original request bound to the captured candidate. */
final class CommandExclusionDecision {
    private CommandExclusionDecision() { }
    static void validate(JsonNode receipt) {
        var decision=receipt.path("businessDecision");
        fields(decision,"kind candidates products shortShipmentReason shortApprovalReceiptId shortApprovalSha256 submissionReceipt exclusion");
        var exclusion=decision.path("exclusion");
        fields(exclusion,"orderId revision rowVersion basis evidence withdrawalRequestId candidate work withdrawalReceipt");
        var input=(ObjectNode)exclusion.deepCopy();input.remove(List.of("candidate","work","withdrawalReceipt"));
        TemporaryExclusionShape.validate(input);
        fields(exclusion.path("candidate"),"order products");
        require(exclusion.path("candidate").path("products").isArray() && !exclusion.path("candidate").path("products").isEmpty());
        fields(exclusion.path("work"),"activeAssignment unresolvedPc unresolvedSubmission stockConflict hasResult openSlot");
        exclusion.path("work").forEach(flag->require(flag.isBoolean() && !flag.asBoolean()));
        var order=exclusion.path("candidate").path("order");
        order(order);
        require(order.isObject() && order.path("id").equals(input.path("orderId")) && order.path("revision").isInt()
            && Integer.toString(order.path("revision").asInt()).equals(input.path("revision").asText())
            && order.path("rowVersion").equals(input.path("rowVersion")));
        require(decision.path("shortApprovalReceiptId").isNull() && decision.path("shortApprovalSha256").isNull()
            && decision.path("submissionReceipt").isNull() && decision.path("candidates").path("total").asLong()>0);
        require(input.path("basis").asText().equals("WITHDRAWN")?exclusion.path("withdrawalReceipt").isObject():exclusion.path("withdrawalReceipt").isNull());
    }
    static void bind(JsonNode body,JsonNode decision) {
        var exclusion=(ObjectNode)decision.path("exclusion").deepCopy();
        exclusion.remove(List.of("candidate","work","withdrawalReceipt"));
        require(exclusion.equals(body.path("exclusion")));
    }
    private static void order(JsonNode order) {
        fields(order,"id orderNo status revision rowVersion revisionId vehicleNo containerNo createdAt updatedAt inventorySnapshotId mappingRevision hasAssignment hasPcWork hasSubmission hasResult");
        for(String field:List.of("id","revisionId","inventorySnapshotId"))require(order.path(field).isTextual()
            && UUID.fromString(order.path(field).asText()).toString().equals(order.path(field).asText()));
        for(String field:List.of("orderNo","status","vehicleNo","containerNo"))require(order.path(field).isTextual());
        for(String field:List.of("createdAt","updatedAt"))require(order.path(field).isTextual()
            && java.time.Instant.parse(order.path(field).asText()).toString().equals(order.path(field).asText()));
        require(order.path("mappingRevision").isTextual() && order.path("mappingRevision").asText().matches("0|[1-9][0-9]{0,17}"));
        for(String field:List.of("hasAssignment","hasPcWork","hasSubmission","hasResult"))require(order.path(field).isBoolean());
    }
    private static void fields(JsonNode value,String expected) {
        require(value.isObject());var actual=new HashSet<String>();value.fieldNames().forEachRemaining(actual::add);
        require(actual.equals(Set.of(expected.split(" "))));
    }
    private static void require(boolean value) { CommandErrors.integrity(value); }
}
