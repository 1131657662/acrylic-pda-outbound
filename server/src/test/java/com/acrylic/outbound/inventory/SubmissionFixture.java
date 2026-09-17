package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.pda.AssignmentStore;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SubmissionFixture {
    final PublicationDriver web;final PdaTestDriver pda;final JdbcTemplate jdbc;final JsonNode tokens;final ObjectNode body;final JsonNode assignment;final String order;
    SubmissionFixture(PublicationDriver web,JdbcTemplate jdbc,boolean deduct,boolean shortPlan) throws Exception {
        this.web=web;this.jdbc=jdbc;pda=new PdaTestDriver(web);
        String serial=UUID.randomUUID().toString().replace("-","").substring(0,7).toUpperCase();
        web.publish(web.stage(SkuFixture.concat(SkuFixture.row("003X"+serial,"300.5"),SkuFixture.row("571X"+serial,"301"))));
        var orders=new OrderTestDriver(web);orders.policy("0",deduct);var plan=orders.plan();
        if(!shortPlan) plan.get("lines").forEach(line->((ObjectNode)line).put("plannedCount",1));
        order=orders.create(plan).get("orderId").asText();web.data(orders.command("/"+order+"/publish",orders.release("1","1"),UUID.randomUUID().toString()));
        tokens=pda.login(pda.register());assignment=pda.data(pda.claim(order,UUID.randomUUID().toString()),tokens);
        jdbc.update("UPDATE order_assignment SET download_ack_at=UTC_TIMESTAMP(3) WHERE id=?",Ids.bytes(UUID.fromString(assignment.get("id").asText())));
        body=build(shortPlan).put("autoDeductInventory",deduct);
    }
    SubmissionFixture(SubmissionFixture original) throws Exception {
        web=original.web;jdbc=original.jdbc;pda=original.pda;var orders=new OrderTestDriver(web);var plan=orders.plan();
        plan.get("lines").forEach(line->((ObjectNode)line).put("plannedCount",1));
        order=orders.create(plan).get("orderId").asText();web.data(orders.command("/"+order+"/publish",orders.release("1","1"),UUID.randomUUID().toString()));
        tokens=pda.login(pda.register());assignment=pda.data(pda.claim(order,UUID.randomUUID().toString()),tokens);
        jdbc.update("UPDATE order_assignment SET download_ack_at=UTC_TIMESTAMP(3) WHERE id=?",Ids.bytes(UUID.fromString(assignment.get("id").asText())));
        body=build(false).put("autoDeductInventory",original.body.get("autoDeductInventory").asBoolean());
    }
    private ObjectNode build(boolean shortPlan) {
        String time=Instant.parse(assignment.get("issuedAt").asText()).plusMillis(100).toString();
        var b=SubmissionJson.object().put("schemaVersion",2).put("submissionId",UUID.randomUUID().toString()).put("orderRevision",1)
            .put("assignmentId",assignment.get("id").asText()).put("assignmentEpoch","1").put("inventorySnapshotId",assignment.get("inventorySnapshotId").asText())
            .put("policyVersion","1").put("startedAt",time).put("closedAt",time).put("expectedCount","2").put("expectedWeightGrams","601500")
            .put("captureHighWatermark","2").put("freezeDeviceId",tokens.get("deviceId").asText()).put("workRound","1").put("sourceRecoveryEpoch","1");
        b.putNull("continuationId");if(shortPlan)b.put("shortShipmentReason","库存不足，申请短装");else b.putNull("shortShipmentReason");
        b.putObject("source").put("kind","FORMAL").put("sourceOrderId",order);
        b.putObject("captureSummary").put("received","2").put("saved","2").put("voided","0").put("pending","0").put("invalid","0").put("discarded","0").put("businessRejected","0");
        b.putArray("captures");b.putArray("scans");b.putArray("events");
        var rows=jdbc.query("SELECT BIN_TO_UUID(b.id),b.barcode,s.delivery_code,s.weight_g,s.raw_weight,s.model_snapshot,s.name_snapshot FROM inventory_snapshot_bale s INNER JOIN bale b ON b.id=s.bale_id WHERE s.snapshot_id=? ORDER BY b.barcode",
            (rs,n)->SubmissionJson.object().put("baleId",rs.getString(1)).put("barcode",rs.getString(2)).put("deliveryCode",rs.getString(3)).put("weightGrams",rs.getString(4))
                .put("rawWeight",rs.getString(5)).put("model",rs.getString(6)).put("name",rs.getString(7)),Ids.bytes(UUID.fromString(assignment.get("inventorySnapshotId").asText())));
        for(int i=0;i<rows.size();i++) addScan(b,rows.get(i),i+1,time);
        b.withArray("events").add(event(null,7,"FROZEN",b.get("submissionId").asText()));return b;
    }
    private void addScan(ObjectNode b,ObjectNode scan,int seq,String time) {
        String id=UUID.randomUUID().toString(),a=assignment.get("id").asText();
        var c=SubmissionJson.object().put("id",id).put("assignmentId",a).put("orderId",order).put("originDeviceId",tokens.get("deviceId").asText())
            .put("operatorId",tokens.get("userId").asText()).put("warehouseId",tokens.get("warehouseId").asText()).put("orderScanSeq",Integer.toString(seq))
            .put("deviceCaptureSeq",Integer.toString(seq)).put("rawInput",scan.get("barcode").asText()).put("barcode",scan.get("barcode").asText())
            .put("inputSource","BROADCAST").put("confirmationPolicy","AUTO_AFTER_VALIDATION").put("receivedWallMs","1000").put("receivedElapsedMs","1000").put("receivedBoot",1)
            .put("snapshotId",assignment.get("inventorySnapshotId").asText()).put("expectedDispatchCycle","1").put("sourceRecoveryEpoch","1")
            .put("authorizationId",assignment.get("offlineAuthorizationId").asText()).put("assignmentEpoch","1").put("orderRevision",1)
            .put("state","COMMITTED").put("error","").put("confirmedAt",time).putNull("failureAckAt");
        b.withArray("captures").add(c);scan.put("captureId",id).put("orderScanSeq",Integer.toString(seq)).put("expectedDispatchCycle","1")
            .put("savedAt",time).put("localSeq",Integer.toString(seq*3)).put("status","ACTIVE");b.withArray("scans").add(scan);
        b.withArray("events").add(event(id,seq*3-2,"RECEIVED","AUTO_AFTER_VALIDATION"));
        b.withArray("events").add(event(id,seq*3-1,"CONFIRM_REQUESTED","AUTO_AFTER_VALIDATION"));b.withArray("events").add(event(id,seq*3,"SAVED",""));
    }
    private ObjectNode event(String capture,int seq,String type,String detail) {
        return SubmissionJson.object().put("id",UUID.randomUUID().toString()).put("captureId",capture).put("assignmentId",assignment.get("id").asText())
            .put("originDeviceId",tokens.get("deviceId").asText()).put("operatorId",tokens.get("userId").asText()).put("localSeq",Integer.toString(seq))
            .put("type",type).put("detail",detail).put("wallMs","1000").put("elapsedMs","1000").put("boot",1);
    }
    String id() { return body.get("submissionId").asText(); }
    void addAcknowledgedFailures(int count) {
        var events=body.withArray("events");events.remove(events.size()-1);
        for(int i=0;i<count;i++) {
            String id=UUID.randomUUID().toString();int seq=i+3;
            var capture=((ObjectNode)body.withArray("captures").get(0)).deepCopy().put("id",id).put("orderScanSeq",Integer.toString(seq))
                .put("deviceCaptureSeq",Integer.toString(seq)).put("state","INVALID").put("rawInput","bad").putNull("barcode")
                .putNull("expectedDispatchCycle").putNull("confirmedAt").put("failureAckAt",body.get("closedAt").asText()).put("error","条码格式无效");
            body.withArray("captures").add(capture);events.add(event(id,seq*3-2,"RECEIVED","AUTO_AFTER_VALIDATION"));
            events.add(event(id,seq*3-1,"INVALID","条码格式无效"));events.add(event(id,seq*3,"FAILURE_ACKNOWLEDGED",""));
        }
        events.add(event(null,(count+2)*3+1,"FROZEN",id()));body.put("captureHighWatermark",Integer.toString(count+2));
        ((ObjectNode)body.get("captureSummary")).put("received",Integer.toString(count+2)).put("invalid",Integer.toString(count));
    }
    MockHttpServletRequestBuilder request(ObjectNode payload) { return post("/api/v1/pda/orders/"+order+"/submissions").header("X-Server-Recovery-Epoch","1").contentType("application/json").content(SubmissionJson.canonical(payload)); }
    ResultActions send(ObjectNode payload) throws Exception { return pda.send(request(payload),tokens); }
    JsonNode submit() throws Exception { return web.json().readTree(send(body).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data"); }
    ObjectNode abortBody() { return body.deepCopy().put("reason","保留实物，取消本次传输"); }
    JsonNode abort() throws Exception { return pda.data(post("/api/v1/pda/submissions/"+id()+"/abort").header("X-Server-Recovery-Epoch","1").contentType("application/json").content(SubmissionJson.canonical(abortBody())),tokens); }
    JsonNode review(String decision) throws Exception {
        var request=SubmissionJson.object().put("decision",decision).put("reason","主管已核对实物").put("payloadSha256",SubmissionJson.sha(body));
        return web.data(post("/api/v1/submissions/"+id()+"/review").contentType("application/json").content(request.toString()));
    }
    int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE "+(table.equals("inventory_ledger")?"dispatch_id IN (SELECT id FROM dispatch_record WHERE order_id=?)":"order_id=?"),Integer.class,Ids.bytes(UUID.fromString(order))); }
}
