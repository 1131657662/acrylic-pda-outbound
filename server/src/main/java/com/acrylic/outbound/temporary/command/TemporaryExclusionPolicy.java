package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.review.TemporaryDecisionCandidates;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** A narrative never makes live work or a stock conflict disappear. */
public final class TemporaryExclusionPolicy {
    private TemporaryExclusionPolicy() { }
    public static void validate(JsonNode body,JsonNode exclusion) {
        var order=exclusion.path("candidate").path("order");
        var work=exclusion.path("work");
        for(String field:List.of("activeAssignment","unresolvedPc","unresolvedSubmission","stockConflict","hasResult","openSlot"))
            if(!work.path(field).isBoolean() || work.path(field).asBoolean())
                throw reject("TEMPORARY_EXCLUSION_WORK_CONFLICT","候选仍有作业、结果或库存冲突，不能靠排除绕开");
        switch(exclusion.path("basis").asText()) {
            case "CANCELLED" -> require(order.path("status").asText().equals("CANCELLED"));
            case "WITHDRAWN" -> withdrawn(order,exclusion);
            case "UNRELATED" -> unrelated(body,exclusion);
            default -> throw reject("TEMPORARY_EXCLUSION_BASIS_INVALID","排除依据不受支持");
        }
    }
    private static void withdrawn(JsonNode order,JsonNode exclusion) {
        var proof=exclusion.path("withdrawalReceipt");
        require(order.path("status").asText().equals("DRAFT") && proof.isObject()
            && proof.path("orderId").equals(order.path("id")) && proof.path("status").asText().equals("WITHDRAWN")
            && proof.path("orderStatus").asText().equals("DRAFT")
            && proof.path("changeRequestId").equals(exclusion.path("withdrawalRequestId"))
            && proof.path("restartRevision").asInt(-1)==order.path("revision").asInt(-2));
    }
    private static void unrelated(JsonNode body,JsonNode exclusion) {
        var candidate=exclusion.path("candidate");var order=candidate.path("order");
        require(Set.of("DRAFT","READY").contains(order.path("status").asText())
            && !order.path("vehicleNo").asText().equals(body.at("/temporaryOrder/vehicleNo").asText()));
        for(String field:List.of("hasAssignment","hasPcWork","hasSubmission","hasResult"))
            require(order.path(field).isBoolean() && !order.path(field).asBoolean());
        var actual=new HashSet<String>();var expected=new HashSet<String>();
        candidate.path("products").forEach(p->actual.add(product(p)));
        TemporaryDecisionCandidates.products(body).forEach(p->expected.add(product(p)));
        require(!actual.equals(expected));
    }
    private static String product(JsonNode value) {
        var selected=SubmissionJson.object();
        for(String field:List.of("deliveryCode","productId","model","name","plannedCount"))selected.set(field,value.path(field));
        return SubmissionJson.canonical(selected);
    }
    private static void require(boolean value) {
        if(!value)throw reject("TEMPORARY_EXCLUSION_BASIS_INVALID","原候选事实不能证明该排除依据，请保全原件逐单核对");
    }
    private static BusinessException reject(String code,String message) { return new BusinessException(409,code,message); }
}
