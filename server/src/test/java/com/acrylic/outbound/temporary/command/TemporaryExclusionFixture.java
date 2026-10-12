package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;

/** Synthetic protocol facts only; database and HTTP consumers supply their own retained sources. */
final class TemporaryExclusionFixture {
    private TemporaryExclusionFixture() { }
    static ObjectNode product() {
        return SubmissionJson.object().put("deliveryCode","001").put("productId",UUID.randomUUID().toString())
            .put("model","合成型号").put("name","合成产品").put("plannedCount","1");
    }
    static ObjectNode body() {
        var body=SubmissionJson.object();
        body.putObject("temporaryOrder").put("vehicleNo","原车辆").putArray("lines").add(product());
        return body;
    }
    static ObjectNode input() {
        return SubmissionJson.object().put("orderId",UUID.randomUUID().toString()).put("revision","1").put("rowVersion","2")
            .put("basis","UNRELATED").put("evidence","主管核对为另一责任区车辆，原计划不同").putNull("withdrawalRequestId");
    }
    static ObjectNode exclusion(ObjectNode body) {
        var value=input();var candidate=value.putObject("candidate");
        candidate.set("order",order(value.path("orderId").asText()));
        candidate.putArray("products").add(((ObjectNode)body.at("/temporaryOrder/lines/0")).deepCopy().put("plannedCount","2"));
        var work=value.putObject("work");
        for(String field:List.of("activeAssignment","unresolvedPc","unresolvedSubmission","stockConflict","hasResult","openSlot"))work.put(field,false);
        value.putNull("withdrawalReceipt");return value;
    }
    private static ObjectNode order(String id) {
        var order=SubmissionJson.object().put("id",id).put("orderNo","FH-SYNTHETIC").put("status","READY").put("revision",1)
            .put("rowVersion","2").put("revisionId",UUID.randomUUID().toString()).put("vehicleNo","另一车辆").put("containerNo","")
            .put("createdAt","2026-10-12T00:00:00Z").put("updatedAt","2026-10-12T00:00:00Z")
            .put("inventorySnapshotId",UUID.randomUUID().toString()).put("mappingRevision","1");
        for(String field:List.of("hasAssignment","hasPcWork","hasSubmission","hasResult"))order.put(field,false);
        return order;
    }
    static ObjectNode receipt() {
        var body=body();var receipt=SubmissionJson.object().put("commandType","DUPLICATE_EXCLUDE").put("status","RECORDED");
        receipt.putObject("expected").put("candidateContextSha256","a".repeat(64));
        var decision=receipt.putObject("businessDecision").put("kind","DUPLICATE_EXCLUDE").put("shortShipmentReason","")
            .putNull("shortApprovalReceiptId").putNull("shortApprovalSha256").putNull("submissionReceipt");
        decision.putArray("products").add(product());decision.set("exclusion",exclusion(body));
        decision.putObject("candidates").put("policyVersion","REVIEW_DEFAULT_V1").put("fromDate","2026-10-01")
            .put("throughDate","2026-10-15").put("total","3").put("fingerprint","b".repeat(64)).put("contextSha256","a".repeat(64));
        return receipt;
    }
}
