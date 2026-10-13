package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Real return and stock-restoration HTTP precede a new PC cycle's real isolation decisions. */
public final class QuarantineHistoryHttpSource {
    final ReturnedStockHttpFixture returned;
    final Path evidence;
    final String warehouse,bale,order,makerUser,checkerUser;
    final CorrectionAttachmentHttp maker,checker;
    public QuarantineHistoryHttpSource(WebApplicationContext app,Path attempt) throws Exception {
        returned=new ReturnedStockHttpFixture(app,attempt,"quarantine-history");returned.discover();returned.submit();
        returned.signed(returned.checker,returned.command("APPROVE",true));
        evidence=attempt.resolve("quarantine-history-http");Files.createDirectory(evidence);
        warehouse=returned.original.warehouse;bale=returned.bale;makerUser=returned.original.makerIdentity.user().username();
        checkerUser=returned.original.jdbc.queryForObject("SELECT username FROM app_user u JOIN user_role ur ON ur.user_id=u.id "
            +"JOIN role r ON r.id=ur.role_id JOIN user_warehouse w ON w.user_id=u.id WHERE r.code='SUPERVISOR' "
            +"AND w.warehouse_id=UUID_TO_BIN(?) ORDER BY u.id DESC LIMIT 1",String.class,warehouse);
        returned.original.jdbc.update("INSERT INTO user_role SELECT u.id,r.id FROM app_user u CROSS JOIN role r WHERE u.username=? AND r.code='WAREHOUSE_ADMIN'",checkerUser);
        maker=new CorrectionAttachmentHttp(makerUser,evidence.resolve("maker-http.log"));checker=new CorrectionAttachmentHttp(checkerUser,evidence.resolve("checker-http.log"));
        var orders=new OrderTestDriver(returned.original.formal.web);var plan=orders.plan();plan.withArray("lines").removeAll();
        plan.withArray("lines").addObject().put("deliveryCode",returned.original.facts(returned.original.dispatch).path("deliveryCode").asText()).put("plannedCount",1);
        order=orders.create(plan).path("orderId").asText();isolate();
        assertThat(returned.original.jdbc.queryForObject("SELECT dispatch_cycle FROM bale WHERE id=UUID_TO_BIN(?)",Integer.class,bale)).isEqualTo(2);
        assertThat(returned.original.jdbc.queryForObject("SELECT state FROM bale_commitment WHERE bale_id=UUID_TO_BIN(?)",String.class,bale)).isEqualTo("QUARANTINED");
        Files.writeString(evidence.resolve("browser-input.json"),SubmissionJson.canonical(SubmissionJson.object().put("warehouseId",warehouse)
            .put("baleId",bale).put("makerUsername",makerUser).put("checkerUsername",checkerUser).put("password","Test-password-123")));
    }
    private String path(String action) { return "/orders/"+order+"/pc"+(action.isEmpty()?"":"/"+action)+"?warehouseId="+warehouse; }
    private JsonNode view(String action) throws Exception { return SubmissionJson.parse(maker.get(path(action),200)).path("data"); }
    private void isolate() throws Exception {
        String writer=UUID.randomUUID().toString();var start=SubmissionJson.object().put("expectedVersion","1").put("writerId",writer)
            .put("policyVersion","1").put("reason","ISOLATED new cycle original PC work");
        maker.post(path("start"),UUID.randomUUID().toString(),start,200);var work=view("").path("work");
        String barcode=returned.original.jdbc.queryForObject("SELECT barcode FROM bale WHERE id=UUID_TO_BIN(?)",String.class,bale);
        var entry=SubmissionJson.object().put("writerId",writer).put("fence",work.path("fence").asText()).put("expectedVersion",work.path("version").asText())
            .put("barcode",barcode).putNull("entryId").put("reason","").put("physicalConfirmed",true);
        maker.post(path("entries"),UUID.randomUUID().toString(),entry,200);
        maker.post(path("exception/stop"),UUID.randomUUID().toString(),base().put("reason","ISOLATED stop before physical isolation"),200);
        var stopped=view("exception").path("stopped");var plan=base().put("caseId",stopped.path("id").asText())
            .put("snapshotSha256",stopped.path("snapshotSha256").asText()).put("targetUsername",makerUser).put("outcome","CANCEL").put("reason","ISOLATED quarantine old returned barcode");
        var row=stopped.at("/snapshot/entries/0");
        plan.putArray("lines").addObject().put("entryId",row.path("id").asText()).put("expectedCycle",row.path("cycle").asLong())
            .put("action","QUARANTINE").put("physicalEvidence","ISOLATED returned bale separated in warehouse bay");
        checker.post(path("exception/plan"),UUID.randomUUID().toString(),plan,200);var selected=view("exception").at("/plans/0");
        var decision=base().put("planId",selected.path("id").asText()).put("payloadSha256",selected.path("payloadSha256").asText())
            .put("decision","APPROVE").put("reason","ISOLATED independent supervisor inspection");
        checker.post(path("exception/review"),UUID.randomUUID().toString(),decision,200);
        maker.post(path("exception/apply"),UUID.randomUUID().toString(),base().put("planId",selected.path("id").asText())
            .put("payloadSha256",selected.path("payloadSha256").asText()).put("physicalEvidence","ISOLATED approved separation applied"),200);
    }
    private ObjectNode base() throws Exception { return SubmissionJson.object().put("expectedVersion",view("").at("/work/version").asText()).put("physicalConfirmed",true); }
}
