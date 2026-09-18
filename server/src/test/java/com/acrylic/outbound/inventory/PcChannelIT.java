package com.acrylic.outbound.inventory;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class PcChannelIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    private SubmissionFixture fixture() throws Exception {
        return new SubmissionFixture(new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN")),jdbc,false,false);
    }
    private String start(SubmissionFixture f,String writer) throws Exception {
        var orders=new OrderTestDriver(f.web);var plan=orders.plan();plan.get("lines").forEach(p->((ObjectNode)p).put("plannedCount",1));
        String path="/api/v1/orders/"+orders.create(plan).get("orderId").asText()+"/pc";
        var body=json.createObjectNode().put("expectedVersion","1").put("writerId",writer).put("policyVersion","1").put("reason","跨渠道核对");
        f.web.data(post(path+"/start").header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json").content(body.toString()));return path;
    }
    private ObjectNode add(SubmissionFixture f,String writer) {
        return json.createObjectNode().put("writerId",writer).put("fence","1").put("expectedVersion","1").put("barcode",f.body.at("/scans/0/barcode").asText())
            .putNull("entryId").put("reason","").put("physicalConfirmed",true);
    }
    @Test void pdaSubmissionCannotOverwriteExistingPcReservation() throws Exception {
        var f=fixture();String writer=UUID.randomUUID().toString(),path=start(f,writer);
        f.web.data(post(path+"/entries").header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json").content(add(f,writer).toString()));
        var result=f.submit();assertThat(result.get("status").asText()).isEqualTo("REVIEW_REQUIRED");assertThat(result.toString()).contains("BALE_ALREADY_COMMITTED");
        assertThat(f.count("dispatch_record")).isZero();
        assertThat(f.web.data(get(path)).at("/products/0/actualCount").asInt()).isEqualTo(1);
    }
    @Test void pcCannotRecordPdaDispatchEvenWhenAutomaticDeductionIsDisabled() throws Exception {
        var f=fixture();String writer=UUID.randomUUID().toString(),path=start(f,writer);assertThat(f.submit().get("status").asText()).isEqualTo("SUCCEEDED");
        f.web.send(post(path+"/entries").header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json").content(add(f,writer).toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PC_BALE_UNAVAILABLE"));
        assertThat(f.web.data(get(path+"/candidates").param("query",f.body.at("/scans/0/barcode").asText())).at("/groups/0/items/0/availability").asText()).isEqualTo("DISPATCHED");
        assertThat(f.count("inventory_ledger")).isZero();assertThat(f.count("dispatch_record")).isEqualTo(2);
    }
    @Test void pdaSubmissionAndInventoryImportCannotOverwritePcQuarantine() throws Exception {
        var f=fixture();String writer=UUID.randomUUID().toString(),path=start(f,writer);
        pcAction(f,path+"/entries",add(f,writer));
        pcAction(f,path+"/exception/stop",json.createObjectNode().put("expectedVersion","2").put("reason","暂停核验隔离包").put("physicalConfirmed",true));
        var stopped=f.web.data(get(path+"/exception")).get("stopped");var entry=stopped.at("/snapshot/entries/0");
        var plan=json.createObjectNode().put("caseId",stopped.path("id").asText()).put("snapshotSha256",stopped.path("snapshotSha256").asText())
            .put("expectedVersion","3").put("targetUsername",f.web.identity().user().username()).put("outcome","CANCEL").put("reason","异常实物隔离").put("physicalConfirmed",true);
        plan.putArray("lines").addObject().put("entryId",entry.path("id").asText()).put("expectedCycle",entry.path("cycle").asLong()).put("action","QUARANTINE").put("physicalEvidence","已卸至隔离区Q1，逐包核验凭据");
        pcAction(f,path+"/exception/plan",plan);var saved=f.web.data(get(path+"/exception")).at("/plans/0");
        var decision=json.createObjectNode().put("planId",saved.path("id").asText()).put("payloadSha256",saved.path("payloadSha256").asText())
            .put("expectedVersion","4").put("decision","APPROVE").put("reason","主管现场核验").put("physicalConfirmed",true);
        pcAction(f,path+"/exception/review",decision);pcAction(f,path+"/exception/apply",decision.deepCopy().put("expectedVersion","5").put("physicalEvidence","本人确认隔离交接"));
        f.web.publish(f.web.stage(SkuFixture.concat(SkuFixture.row(f.body.at("/scans/0/barcode").asText(),"300.5"),SkuFixture.row(f.body.at("/scans/1/barcode").asText(),"301"))));
        var result=f.submit();assertThat(result.get("status").asText()).isEqualTo("REVIEW_REQUIRED");assertThat(result.toString()).contains("BALE_ALREADY_COMMITTED");
        assertThat(f.count("dispatch_record")).isZero();
        assertThat(jdbc.queryForObject("SELECT state FROM bale_commitment WHERE bale_id=UUID_TO_BIN(?)",String.class,entry.path("baleId").asText())).isEqualTo("QUARANTINED");
    }
    private JsonNode pcAction(SubmissionFixture f,String path,ObjectNode body) throws Exception {
        return f.web.data(post(path).header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json").content(body.toString()));
    }
}
