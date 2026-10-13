package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.assertj.core.api.Assertions.*;

/** Only inventory/order/registration bootstrap uses MockMvc; the original stopped/approved/applied chain is actual HTTP. */
public final class FormalQuarantineHttpSource {
    public final String warehouse,order,makerUser,checkerUser,bale;
    public final Path evidence;
    public final JdbcTemplate jdbc;
    private final SubmissionFixture fixture;
    public ObjectNode appliedBody;
    public FormalQuarantineHttpSource(WebApplicationContext context,Path attempt,JdbcTemplate jdbc) throws Exception {
        this.jdbc=jdbc; evidence=attempt.resolve("formal-quarantine-http");Files.createDirectory(evidence);
        var passwords=context.getBean(PasswordEncoder.class);var identity=IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN");
        var web=new PublicationDriver(MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(com.acrylic.outbound.common.RequestIdFilter.class))
            .apply(springSecurity()).build(),context.getBean(ObjectMapper.class),identity);
        fixture=new SubmissionFixture(web,jdbc,true,true);warehouse=identity.warehouse().toString();order=fixture.order;makerUser=identity.user().username();
        var checker=IdentityFixture.create(jdbc,passwords,"SUPERVISOR");checkerUser=checker.user().username();
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)",Ids.bytes(checker.user().id()),Ids.bytes(identity.warehouse()));
        jdbc.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code='WAREHOUSE_ADMIN'",Ids.bytes(checker.user().id()));
        bale=fixture.body.at("/scans/0/baleId").asText();
    }
    public void originalsThroughHttp() throws Exception {
        originalsThroughHttp(false);
    }
    public void originalsThroughHttp(boolean open) throws Exception {
        var maker=new CorrectionAttachmentHttp(makerUser,evidence.resolve("maker-original.log"));var checker=new CorrectionAttachmentHttp(checkerUser,evidence.resolve("checker-original.log"));
        var pda=new FormalQuarantinePdaHttp(fixture.tokens,evidence.resolve("pda-original.log"));
        fixture.body.put("shortShipmentReason","ISOLATED original review");
        pda.post("/orders/"+order+"/submissions",fixture.body);
        checker.post("/submissions/"+fixture.id()+"/review",UUID.randomUUID().toString(),review(fixture.body,"REJECT"),200);
        var recall=new RecallFixture(fixture);recall.recall=maker.post("/orders/"+order+"/change-requests?warehouseId="+warehouse,recall.key,recall.command,200);
        recall.body=recall.recovery(false);pda.post("/recovery-packages",recall.body);
        pda.post("/change-requests/"+recall.recall.path("changeRequestId").asText()+"/stop-ack",recall.ackBody());
        var physical=physical(recall,version(maker));var approval=approvePhysical(checker,recall,physical);
        var request=continuation(recall,approval);var ticket=checker.post("/orders/"+order+"/change-requests?warehouseId="+warehouse,UUID.randomUUID().toString(),request,200);
        var grant=SubmissionJson.object().put("action","REOPEN_WORK").put("expectedVersion",version(maker)).put("retainedManifestSha256",ticket.path("retainedManifestSha256").asText())
            .put("physicalEvidence","ISOLATED original same-device retained manifest");
        checker.post("/change-requests/"+ticket.path("continuationId").asText()+"/resolve?warehouseId="+warehouse,UUID.randomUUID().toString(),grant,200);
        var activate=SubmissionJson.object().put("payloadSha256",SubmissionJson.sha(recall.body)).put("retainedManifestSha256",ticket.path("retainedManifestSha256").asText())
            .put("nextOrderScanSeq",ticket.path("nextOrderScanSeq").asText()).put("planSha256",fixture.assignment.path("planSha256").asText());
        var activation=pda.post("/continuations/"+ticket.path("continuationId").asText()+"/activate",activate);var applied=applied(recall,activation);
        appliedBody=applied;
        if(open) applicationRollback(pda,applied);
        pda.post("/orders/"+order+"/submissions",applied);
        if(!open) checker.post("/submissions/"+applied.path("submissionId").asText()+"/review",UUID.randomUUID().toString(),review(applied,"APPROVE"),200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE bale_id=UUID_TO_BIN(?) AND state='QUARANTINED'",Integer.class,bale)).isEqualTo(1);
        Files.writeString(evidence.resolve("browser-input.json"),SubmissionJson.canonical(SubmissionJson.object().put("warehouseId",warehouse).put("orderId",order).put("baleId",bale)
            .put("makerUsername",makerUser).put("checkerUsername",checkerUser).put("password","Test-password-123")));
    }
    private void applicationRollback(FormalQuarantinePdaHttp pda,ObjectNode body) throws Exception {
        jdbc.execute("CREATE TRIGGER application_tail_fail BEFORE INSERT ON formal_disposition_application FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ISOLATED application receipt tail failure'; END");
        pda.post("/orders/"+order+"/submissions",body,500);jdbc.execute("DROP TRIGGER application_tail_fail");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM physical_disposition_evidence WHERE applied_submission_id IS NOT NULL",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM formal_disposition_application",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submission_payload WHERE submission_id=UUID_TO_BIN(?)",Integer.class,body.path("submissionId").asText())).isZero();
    }
    private String version(CorrectionAttachmentHttp actor) throws Exception {
        return SubmissionJson.parse(actor.get("/orders/"+order+"/change-requests?warehouseId="+warehouse,200)).at("/data/request/rowVersion").asText();
    }
    private ObjectNode review(JsonNode body,String decision) { return SubmissionJson.object().put("decision",decision).put("reason","ISOLATED independent review").put("payloadSha256",SubmissionJson.sha(body)); }
    private ObjectNode physical(RecallFixture recall,String version) {
        var body=SubmissionJson.object().put("payloadSha256",SubmissionJson.sha(recall.body)).put("expectedVersion",version);
        body.putArray("dispositions").addObject().put("captureId",recall.body.at("/scans/0/captureId").asText()).put("expectedDispatchCycle","1")
            .put("action","QUARANTINE").put("physicalEvidence","ISOLATED original marked bay F-13");return body;
    }
    private JsonNode approvePhysical(CorrectionAttachmentHttp checker,RecallFixture recall,ObjectNode body) throws Exception {
        String path="/recovery-packages/"+recall.body.path("recoveryPackageId").asText()+"/disposition-evidence?warehouseId="+warehouse;
        String key=UUID.randomUUID().toString();int original=jdbc.queryForObject("SELECT COUNT(*) FROM physical_disposition_evidence",Integer.class);
        jdbc.execute("CREATE TRIGGER formal_original_tail_fail BEFORE INSERT ON formal_disposition_receipt FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ISOLATED original approval tail failure'; END");
        checker.post(path,key,body,500);jdbc.execute("DROP TRIGGER formal_original_tail_fail");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM physical_disposition_evidence",Integer.class)).isEqualTo(original);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM api_idempotency_request WHERE route_scope='order.disposition.approve' AND idempotency_key=?",Integer.class,key)).isZero();
        var receipt=checker.post(path,key,body,200);assertThat(checker.post(path,key,body,200)).isEqualTo(receipt);
        checker.post(path,key,body.deepCopy().put("expectedVersion","999"),409);
        assertThatThrownBy(()->jdbc.update("UPDATE formal_disposition_receipt SET created_at=UTC_TIMESTAMP(3)")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        return receipt;
    }
    private ObjectNode continuation(RecallFixture recall,JsonNode approval) {
        var body=SubmissionJson.object().put("type","REOPEN_WORK").put("previousSubmissionId",fixture.id()).put("expectedWorkRound","1").put("expectedRevision",1)
            .put("recoveryPackageId",recall.body.path("recoveryPackageId").asText()).put("requestedDeviceId",fixture.tokens.path("deviceId").asText()).put("reason","ISOLATED original same-device continuation");
        var lines=body.putArray("dispositions");for(int i=0;i<2;i++) {
            var scan=recall.body.at("/scans/"+i);var line=lines.addObject().put("barcode",scan.path("barcode").asText()).put("expectedDispatchCycle","1").put("action",i==0?"QUARANTINE":"RETAIN");
            if(i==0) line.put("approvedEvidenceId",approval.at("/evidence/0/approvedEvidenceId").asText());
        }return body;
    }
    private ObjectNode applied(RecallFixture recall,JsonNode activation) {
        var body=fixture.body.deepCopy();var assignment=activation.path("assignment");String id=UUID.randomUUID().toString();
        body.put("submissionId",id).put("assignmentId",assignment.path("id").asText()).put("assignmentEpoch",assignment.path("epoch").asText()).put("workRound","2")
            .put("continuationId",activation.path("continuationId").asText()).put("closedAt",java.time.Instant.parse(assignment.path("issuedAt").asText()).plusMillis(200).toString())
            .put("expectedCount","1").put("expectedWeightGrams","301000").put("shortShipmentReason","ISOLATED approved quarantine, retained bale only");
        ((ObjectNode)body.path("captureSummary")).put("saved","1");body.set("events",recall.body.path("events").deepCopy());body.set("dispositions",activation.path("dispositions"));
        var last=(ObjectNode)body.withArray("events").get(body.withArray("events").size()-1);
        body.withArray("events").add(last.deepCopy().put("id",UUID.randomUUID().toString()).put("assignmentId",assignment.path("id").asText()).put("localSeq","9").put("type","FROZEN").put("detail",id));return body;
    }
    public JsonNode originals() {
        var result=SubmissionJson.object();for(String table:java.util.List.of("physical_disposition_evidence","formal_disposition_receipt","formal_disposition_application","order_continuation","recovery_package","submission","submission_payload","order_result_version","dispatch_record","inventory_ledger")) {
            var rows=result.putArray(table);jdbc.queryForList("SELECT * FROM "+table).forEach(row->{
                var encoded=SubmissionJson.object();new java.util.TreeMap<>(row).forEach((key,value)->{
                    if(value==null)encoded.putNull(key);else encoded.put(key,value instanceof byte[] bytes?java.util.HexFormat.of().formatHex(bytes):value.toString());
                });rows.add(encoded);
            });
        }return result;
    }
}
