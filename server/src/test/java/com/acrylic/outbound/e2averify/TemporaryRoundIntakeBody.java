package com.acrylic.outbound.e2averify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Cumulative original input, an invalid attempt, and a genuinely new scan in the new credential window. */
final class TemporaryRoundIntakeBody {
    private final RetentionDatabaseFixture f;
    TemporaryRoundIntakeBody(RetentionDatabaseFixture f) { this.f=f; }
    ObjectNode original() {
        var b=object().put("schemaVersion",2).put("submissionId",f.seed.get("mappedSubmissionId"))
            .put("authorizationId",f.seed.get("authorizationId")).put("authorizationEpoch","1").put("workRound","1")
            .put("freezeDeviceId",f.seed.get("deviceId")).put("sourceRecoveryEpoch","1").putNull("assignmentId").putNull("continuationId")
            .put("inventorySnapshotId",f.seed.get("intakeSnapshot")).put("policyVersion","1").put("orderRevision","1")
            .put("temporaryNo","SYNTHETIC-INTAKE").put("autoDeductInventory",false).put("closedAt",f.seed.get("intakeTime"))
            .put("shortShipmentReason","synthetic retained only");
        b.putObject("source").put("kind","TEMP").put("creatorDeviceId",f.seed.get("deviceId")).put("clientOrderId",f.seed.get("mappedClientOrderId"));
        var p=b.putObject("temporaryOrder").put("clientOrderId",f.seed.get("mappedClientOrderId")).put("creatorDeviceId",f.seed.get("deviceId"))
            .put("authorizationId",f.seed.get("authorizationId")).put("revision","1").put("vehicleNo","SYNTHETIC").put("containerNo","").put("reason","synthetic intake");
        p.putArray("lines").addObject().put("deliveryCode","001").put("productId",f.seed.get("intakeProduct"))
            .put("model","E2A-MODEL").put("name","E2a Product").put("plannedCount","4");b.put("planSha256",sha(p));
        b.putArray("dispositions");b.putArray("scans");b.putArray("captures");b.putArray("events");
        var invalid=capture(b,UUID.randomUUID().toString(),1,f.seed.get("intakeTime")).put("state","INVALID");array(b,"captures").add(invalid);
        event(b,invalid.path("id").asText(),"RECEIVED");event(b,invalid.path("id").asText(),"INVALID");event(b,invalid.path("id").asText(),"FAILURE_ACKNOWLEDGED");
        var rows=f.jdbc.queryForList("SELECT BIN_TO_UUID(capture_id) capture,BIN_TO_UUID(bale_id) bale,original_device_seq seq FROM source_evidence_item WHERE set_id=UUID_TO_BIN(?) AND item_kind='SCAN' ORDER BY original_device_seq",f.seed.get("mappedSetId"));
        for(var row:rows)add(b,(String)row.get("capture"),(String)row.get("bale"),((Number)row.get("seq")).intValue(),f.seed.get("intakeTime"));
        event(b,null,"FROZEN");summary(b,4,3);return b;
    }
    ObjectNode next(JsonNode original,JsonNode decision) {
        var b=original.deepCopy();var result=(ObjectNode)b;
        result.put("submissionId",UUID.randomUUID().toString()).put("authorizationId",text(decision,"newAuthorizationId"))
            .put("authorizationEpoch",f.jdbc.queryForObject("SELECT CAST(authorization_epoch AS CHAR) FROM temporary_authorization WHERE id=UUID_TO_BIN(?)",String.class,text(decision,"newAuthorizationId")))
            .put("workRound","2").putNull("shortShipmentReason");
        ((ObjectNode)b.get("source")).put("clientOrderId",text(decision,"newClientOrderId"));
        ((ObjectNode)b.get("temporaryOrder")).put("clientOrderId",text(decision,"newClientOrderId")).put("authorizationId",text(decision,"newAuthorizationId"));
        result.put("planSha256",sha(b.get("temporaryOrder")));
        result.putObject("temporaryContinuation").put("version",1).put("ticketId",text(decision,"ticketId"))
            .put("predecessorSubmissionId",text(original,"submissionId")).put("predecessorPayloadSha256",sha(original))
            .put("retainedManifestSha256",text(decision,"retainedManifestSha256"));
        String at=java.time.Instant.now().toString();add(result,UUID.randomUUID().toString(),f.seed.get("intakeNewBale"),5,at);
        event(result,null,"FROZEN");result.put("closedAt",at);summary(result,5,4);return result;
    }
    private void add(ObjectNode b,String capture,String bale,int seq,String at) {
        array(b,"captures").add(capture(b,capture,seq,at));event(b,capture,"RECEIVED");event(b,capture,"CONFIRM_REQUESTED");event(b,capture,"SAVED");
        array(b,"scans").addObject().put("captureId",capture).put("baleId",bale).put("orderScanSeq",Integer.toString(seq))
            .put("expectedDispatchCycle","1").put("barcode",String.format("%011d",seq)).put("status","ACTIVE").put("savedAt",at)
            .put("localSeq",Integer.toString(array(b,"events").size())).put("weightGrams","1000").put("rawWeight","1000")
            .put("deliveryCode","001").put("model","E2A-MODEL").put("name","E2a Product");
    }
    private ObjectNode capture(JsonNode b,String id,int seq,String at) {
        return object().put("id",id).put("clientOrderId",text(b.path("source"),"clientOrderId")).put("originDeviceId",f.seed.get("deviceId"))
            .put("operatorId",f.seed.get("operatorId")).put("authorizationId",text(b,"authorizationId")).put("authorizationEpoch",text(b,"authorizationEpoch"))
            .put("snapshotId",f.seed.get("intakeSnapshot")).put("sourceRecoveryEpoch","1").put("orderRevision","1").put("orderScanSeq",Integer.toString(seq))
            .put("deviceCaptureSeq",Integer.toString(seq)).put("inputSource","MANUAL").put("confirmationPolicy","MANUAL").put("state","COMMITTED")
            .put("rawInput",String.format("%011d",seq)).put("barcode",String.format("%011d",seq)).put("expectedDispatchCycle","1")
            .put("error","").put("receivedWallMs","1").put("receivedElapsedMs","1").put("receivedBoot",1).put("confirmedAt",at).put("failureAckAt",at);
    }
    private void event(ObjectNode b,String capture,String type) {
        var events=array(b,"events");var e=events.addObject().put("id",UUID.randomUUID().toString()).put("captureId",capture)
            .put("originDeviceId",f.seed.get("deviceId")).put("operatorId",f.seed.get("operatorId")).put("localSeq",Integer.toString(events.size()))
            .put("type",type).put("detail",type.equals("FROZEN")?text(b,"submissionId"):"").put("wallMs","1").put("elapsedMs","1").put("boot",1)
            .put("authorizationId",text(b,"authorizationId"));e.set("source",b.path("source").deepCopy());
    }
    private void summary(ObjectNode b,int captures,int saved) {
        b.put("captureHighWatermark",Integer.toString(captures)).put("expectedCount",Integer.toString(saved)).put("expectedWeightGrams",Integer.toString(saved*1000));
        b.putObject("captureSummary").put("received",Integer.toString(captures)).put("saved",Integer.toString(saved)).put("invalid","1")
            .put("voided","0").put("pending","0").put("businessRejected","0").put("discarded","0");
    }
}
