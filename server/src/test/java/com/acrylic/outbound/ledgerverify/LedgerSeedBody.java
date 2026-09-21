package com.acrylic.outbound.ledgerverify;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.*;

/** Synthetic original TEMP facts, created independently of the new ledger implementation. */
public final class LedgerSeedBody {
    private final Material input;
    private final ObjectNode body=SubmissionJson.object();
    private final String client=UUID.randomUUID().toString();
    private final String time=Instant.now().toString();
    public LedgerSeedBody(Material input) { this.input=input; }

    public ObjectNode build(int failedInputs) {
        header();plan();
        for(int i=0;i<input.stock().size();i++)scan(input.stock().get(i).deepCopy(),i+1);
        for(int i=0;i<failedInputs;i++)failure(input.stock().size()+i+1);
        int total=input.stock().size()+failedInputs;
        body.withArray("events").add(event(null,total*3+1,"FROZEN",body.path("submissionId").asText()));
        body.put("captureHighWatermark",Integer.toString(total));
        body.putObject("captureSummary").put("received",Integer.toString(total)).put("saved",Integer.toString(input.stock().size()))
            .put("voided","0").put("pending","0").put("invalid",Integer.toString(failedInputs)).put("discarded","0").put("businessRejected","0");
        return body;
    }

    private void header() {
        var m=input.manifest();
        body.put("schemaVersion",2).put("submissionId",UUID.randomUUID().toString()).put("orderRevision","1")
            .putNull("assignmentId").putNull("continuationId").put("authorizationId",authorization()).put("authorizationEpoch","1")
            .put("inventorySnapshotId",m.path("inventorySnapshotId").asText()).put("policyVersion",m.path("policyVersion").asText())
            .put("startedAt",time).put("closedAt",time).put("expectedCount",Integer.toString(input.stock().size()))
            .put("expectedWeightGrams",Long.toString(input.stock().stream().mapToLong(s->s.path("weightGrams").asLong()).sum()))
            .put("freezeDeviceId",device()).put("workRound","1").put("sourceRecoveryEpoch","1")
            .put("temporaryNo","TEMP-LEDGER-VERIFY").put("autoDeductInventory",false).putNull("shortShipmentReason");
        body.putObject("source").put("kind","TEMP").put("creatorDeviceId",device()).put("clientOrderId",client);
        for(String field:List.of("captures","scans","events","dispositions"))body.putArray(field);
    }

    private void plan() {
        var plan=body.putObject("temporaryOrder").put("clientOrderId",client).put("creatorDeviceId",device())
            .put("authorizationId",authorization()).put("revision","1").put("vehicleNo","台账合成车")
            .put("containerNo","").put("reason","独立台账验收");
        var lines=plan.putArray("lines");var sorted=new TreeMap<String,JsonNode>();
        input.mapping().forEach(row->sorted.put(row.path("deliveryCode").asText(),row));
        sorted.forEach((code,row)->{
            var line=lines.addObject().put("plannedCount","1");
            for(String field:List.of("deliveryCode","productId","model","name"))line.set(field,row.get(field));
        });
        body.put("planSha256",SubmissionJson.sha(plan));
    }

    private ObjectNode capture(int sequence,String id) {
        return SubmissionJson.object().put("id",id).put("clientOrderId",client).put("originDeviceId",device()).put("operatorId",user())
            .put("orderScanSeq",Integer.toString(sequence)).put("deviceCaptureSeq",Integer.toString(sequence))
            .put("inputSource","BROADCAST").put("confirmationPolicy","AUTO_AFTER_VALIDATION").put("receivedWallMs","1000")
            .put("receivedElapsedMs","1000").put("receivedBoot",1).put("snapshotId",body.path("inventorySnapshotId").asText())
            .put("sourceRecoveryEpoch","1").put("authorizationId",authorization()).put("authorizationEpoch","1").put("orderRevision","1");
    }

    private void scan(ObjectNode row,int sequence) {
        String id=UUID.randomUUID().toString();
        body.withArray("captures").add(capture(sequence,id).put("rawInput",row.path("barcode").asText()).put("barcode",row.path("barcode").asText())
            .put("expectedDispatchCycle","1").put("state","COMMITTED").put("error","").put("confirmedAt",time).putNull("failureAckAt"));
        row.put("captureId",id).put("orderScanSeq",Integer.toString(sequence)).put("expectedDispatchCycle","1")
            .put("savedAt",time).put("localSeq",Integer.toString(sequence*3)).put("status","ACTIVE");
        body.withArray("scans").add(row);
        body.withArray("events").add(event(id,sequence*3-2,"RECEIVED","AUTO_AFTER_VALIDATION"));
        body.withArray("events").add(event(id,sequence*3-1,"CONFIRM_REQUESTED","AUTO_AFTER_VALIDATION"));
        body.withArray("events").add(event(id,sequence*3,"SAVED",""));
    }

    private void failure(int sequence) {
        String id=UUID.randomUUID().toString();
        body.withArray("captures").add(capture(sequence,id).put("rawInput","bad").putNull("barcode").putNull("expectedDispatchCycle")
            .put("state","INVALID").put("error","合成失败输入").putNull("confirmedAt").put("failureAckAt",time));
        body.withArray("events").add(event(id,sequence*3-2,"RECEIVED","AUTO_AFTER_VALIDATION"));
        body.withArray("events").add(event(id,sequence*3-1,"INVALID","合成失败输入"));
        body.withArray("events").add(event(id,sequence*3,"FAILURE_ACKNOWLEDGED",""));
    }

    private ObjectNode event(String capture,int sequence,String type,String detail) {
        var row=SubmissionJson.object().put("id",UUID.randomUUID().toString()).put("captureId",capture).put("originDeviceId",device())
            .put("operatorId",user()).put("localSeq",Integer.toString(sequence)).put("type",type).put("detail",detail)
            .put("wallMs","1000").put("elapsedMs","1000").put("boot",1).put("authorizationId",authorization());
        row.set("source",body.get("source"));return row;
    }
    private String authorization() { return input.authorization().path("authorizationId").asText(); }
    private String device() { return input.tokens().path("deviceId").asText(); }
    private String user() { return input.tokens().path("userId").asText(); }
    public record Material(JsonNode tokens,JsonNode authorization,JsonNode manifest,JsonNode mapping,List<ObjectNode> stock) { }
}
