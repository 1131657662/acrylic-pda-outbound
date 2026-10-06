package com.acrylic.outbound.temporary.continuation;

import com.acrylic.outbound.submissions.*;
import com.acrylic.outbound.temporary.*;
import com.acrylic.outbound.temporary.command.TemporaryRetainedManifest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Two original captures (one invalid), one later saved capture, distinct qualification windows. */
final class TemporaryRoundEvidenceFixture {
    final UUID device=UUID.randomUUID(),user=UUID.randomUUID(),warehouse=UUID.randomUUID(),snapshot=UUID.randomUUID();
    final UUID oldSubmission=UUID.randomUUID(),newSubmission=UUID.randomUUID(),oldClient=UUID.randomUUID(),newClient=UUID.randomUUID();
    final UUID oldCapture=UUID.randomUUID(),invalidCapture=UUID.randomUUID(),newCapture=UUID.randomUUID();
    final TemporaryStore.Stored oldAuthorization=authorization(1,"2026-10-05"),newAuthorization=authorization(2,"2026-10-06");
    final ObjectNode original=original(),body=next();
    final TemporarySubmissionEvidence ordinary=new TemporarySubmissionEvidence();
    final TemporaryRoundEvidence validator=new TemporaryRoundEvidence(ordinary);

    TemporaryRoundEvidence.Input input() {
        var before=SubmissionControl.read(oldSubmission,original);var after=TemporaryRoundControl.read(body).control();
        var oldHead=new SubmissionStore.Head(before,user,warehouse,"ABORTED",null,null,null);
        var newHead=new SubmissionStore.Head(after,user,warehouse,"REGISTERED",null,null,null);
        var ref=body.path("temporaryContinuation");
        var source=new TemporaryRoundFacts.Source(UUID.randomUUID(),warehouse,oldSubmission,oldAuthorization.id(),1,2,"1",
            UUID.randomUUID(),3,text(ref,"retainedManifestSha256"),"ACTIVATED",newAuthorization.id(),newClient,device,user);
        var scan=original.path("scans").get(0);
        return new TemporaryRoundEvidence.Input(newHead,oldHead,plan(newAuthorization,body),plan(oldAuthorization,original),
            original,source,List.of(new TemporaryRetainedManifest.Expected(uuid(scan,"baleId"),1,sha(scan))));
    }
    void validate() { validator.validate(input(),body); }
    private TemporaryStore.Stored authorization(int epoch,String day) {
        return new TemporaryStore.Stored(UUID.randomUUID(),warehouse,device,user,Integer.toString(epoch),"1","1","1",
            Instant.parse(day+"T00:00:00Z"),Instant.parse(day+"T01:00:00Z"),null);
    }
    private TemporarySubmissionPlan.Plan plan(TemporaryStore.Stored a,JsonNode value) {
        return new TemporarySubmissionPlan.Plan(a,object(),value.get("temporaryOrder"),List.of());
    }
    private ObjectNode original() {
        var value=header(oldSubmission,oldClient,oldAuthorization,"1","2026-10-05T00:05:00Z");
        var captures=value.putArray("captures");captures.add(capture(oldCapture,1,oldClient,oldAuthorization,"COMMITTED"));
        captures.add(capture(invalidCapture,2,oldClient,oldAuthorization,"INVALID"));
        value.putArray("scans").add(scan(oldCapture,1,3,"2026-10-05T00:01:00Z"));
        var events=value.putArray("events");
        events.add(event(oldCapture,1,"RECEIVED",value)).add(event(oldCapture,2,"CONFIRM_REQUESTED",value))
            .add(event(oldCapture,3,"SAVED",value)).add(event(invalidCapture,4,"RECEIVED",value))
            .add(event(invalidCapture,5,"INVALID",value)).add(event(invalidCapture,6,"FAILURE_ACKNOWLEDGED",value))
            .add(event(null,7,"FROZEN",value));
        value.put("captureHighWatermark","2").put("expectedCount","1");summary(value,2,1);return value;
    }
    private ObjectNode next() {
        var value=original.deepCopy().put("submissionId",newSubmission.toString()).put("authorizationId",newAuthorization.id().toString())
            .put("authorizationEpoch","2").put("workRound","2").put("closedAt","2026-10-06T00:05:00Z");
        ((ObjectNode)value.get("source")).put("clientOrderId",newClient.toString());
        ((ObjectNode)value.get("temporaryOrder")).put("clientOrderId",newClient.toString()).put("authorizationId",newAuthorization.id().toString());
        value.put("planSha256",sha(value.get("temporaryOrder")));
        value.putObject("temporaryContinuation").put("version",1).put("ticketId",UUID.randomUUID().toString())
            .put("predecessorSubmissionId",oldSubmission.toString()).put("predecessorPayloadSha256",sha(original))
            .put("retainedManifestSha256","b".repeat(64));
        array(value,"captures").add(capture(newCapture,3,newClient,newAuthorization,"COMMITTED"));
        array(value,"scans").add(scan(newCapture,3,10,"2026-10-06T00:01:00Z"));
        array(value,"events").add(event(newCapture,8,"RECEIVED",value)).add(event(newCapture,9,"CONFIRM_REQUESTED",value))
            .add(event(newCapture,10,"SAVED",value)).add(event(null,11,"FROZEN",value));
        value.put("captureHighWatermark","3").put("expectedCount","2");summary(value,3,2);return value;
    }
    private ObjectNode header(UUID submission,UUID client,TemporaryStore.Stored a,String round,String closed) {
        var value=object().put("schemaVersion",2).put("submissionId",submission.toString()).put("authorizationId",a.id().toString())
            .put("authorizationEpoch",a.authorizationEpoch()).put("freezeDeviceId",device.toString()).put("sourceRecoveryEpoch","1")
            .put("workRound",round).putNull("assignmentId").putNull("continuationId").put("closedAt",closed)
            .put("inventorySnapshotId",snapshot.toString()).put("orderRevision","1").put("policyVersion","1").put("autoDeductInventory",true);
        value.putArray("dispositions");
        value.putObject("source").put("kind","TEMP").put("creatorDeviceId",device.toString()).put("clientOrderId",client.toString());
        var plan=value.putObject("temporaryOrder").put("clientOrderId",client.toString()).put("creatorDeviceId",device.toString())
            .put("authorizationId",a.id().toString()).put("revision","1").put("vehicleNo","SYNTHETIC").put("containerNo","").put("reason","fixture");
        plan.putArray("lines").addObject().put("deliveryCode","001").put("plannedCount","2");
        value.put("planSha256",sha(plan));return value;
    }
    private ObjectNode capture(UUID id,int seq,UUID client,TemporaryStore.Stored a,String state) {
        return object().put("id",id.toString()).put("clientOrderId",client.toString()).put("originDeviceId",device.toString())
            .put("operatorId",user.toString()).put("authorizationId",a.id().toString()).put("authorizationEpoch",a.authorizationEpoch())
            .put("snapshotId",snapshot.toString()).put("sourceRecoveryEpoch","1").put("orderRevision","1")
            .put("orderScanSeq",Integer.toString(seq)).put("deviceCaptureSeq",Integer.toString(seq)).put("inputSource","MANUAL")
            .put("confirmationPolicy","MANUAL").put("state",state).put("rawInput",String.format("%011d",seq)).put("error","")
            .put("barcode",String.format("%011d",seq)).put("expectedDispatchCycle","1").put("receivedWallMs","1")
            .put("receivedElapsedMs","1").put("receivedBoot",1).put("confirmedAt",a.issuedAt().plusSeconds(30).toString())
            .put("failureAckAt",a.issuedAt().plusSeconds(60).toString());
    }
    private ObjectNode scan(UUID id,int sequence,int event,String saved) {
        return object().put("captureId",id.toString()).put("orderScanSeq",Integer.toString(sequence)).put("expectedDispatchCycle","1")
            .put("barcode",String.format("%011d",sequence)).put("baleId",UUID.randomUUID().toString())
            .put("localSeq",Integer.toString(event)).put("status","ACTIVE").put("savedAt",saved);
    }
    private ObjectNode event(UUID capture,int sequence,String type,JsonNode value) {
        var row=object().put("id",UUID.randomUUID().toString()).put("captureId",capture==null?null:capture.toString())
            .put("originDeviceId",device.toString()).put("operatorId",user.toString()).put("localSeq",Integer.toString(sequence))
            .put("type",type).put("detail",type.equals("FROZEN")?text(value,"submissionId"):"")
            .put("wallMs","1").put("elapsedMs","1").put("boot",1).put("authorizationId",text(value,"authorizationId"));
        row.set("source",value.get("source").deepCopy());return row;
    }
    private void summary(ObjectNode value,int received,int saved) {
        value.putObject("captureSummary").put("received",Integer.toString(received)).put("saved",Integer.toString(saved))
            .put("voided","0").put("pending","0").put("invalid","1").put("businessRejected","0").put("discarded","0");
    }
}
