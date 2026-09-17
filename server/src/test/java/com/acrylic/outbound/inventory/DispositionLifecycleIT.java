package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
class DispositionLifecycleIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    private DispositionFixture fixture(String action) throws Exception {
        var web=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));
        return new DispositionFixture(new ContinuationFixture(new SubmissionFixture(web,jdbc,true,false)),action);
    }
    @ParameterizedTest @ValueSource(strings={"RELEASE","QUARANTINE","PENDING"})
    void nextRecallAndThirdRoundPreserveCumulativeDispositions(String mode) throws Exception {
        var d=fixture(mode.equals("QUARANTINE")?mode:"RELEASE");d.activate();var f=d.c.r.f;var second=d.second();
        f.body.removeAll().setAll(second);
        if(mode.equals("PENDING"))f.abort();else { f.submit();f.review("REJECT"); }
        var recall=new RecallFixture(f);recall.start(false);assertThat(recall.upload().get("actualCount").asText()).isEqualTo("1");recall.stop();
        var request=SubmissionJson.object().put("type","REOPEN_WORK").put("previousSubmissionId",f.id()).put("expectedWorkRound","2")
            .put("expectedRevision",1).put("recoveryPackageId",recall.body.get("recoveryPackageId").asText()).put("requestedDeviceId",f.tokens.get("deviceId").asText()).put("reason","再次召回后续作");
        var scan=recall.body.at("/scans/1");request.putArray("dispositions").add(SubmissionJson.object().put("barcode",scan.get("barcode").asText()).put("action","RETAIN").put("expectedDispatchCycle","1"));
        var ticket=f.web.data(d.c.request(request,UUID.randomUUID().toString()));
        var approval=SubmissionJson.object().put("action","REOPEN_WORK").put("expectedVersion",recall.read().get("rowVersion").asText())
            .put("retainedManifestSha256",ticket.get("retainedManifestSha256").asText()).put("physicalEvidence","再次核验原车仅剩本次保留包");
        f.web.data(post("/api/v1/change-requests/"+ticket.get("continuationId").asText()+"/resolve").header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json").content(approval.toString()));
        var activation=activate(f,ticket,recall);var third=third(second,recall,activation);
        assertThat(f.pda.data(f.request(third),f.tokens).get("status").asText()).isEqualTo("REVIEW_REQUIRED");
        assertThat(d.review(third).get("actualCount").asText()).isEqualTo("1");
        assertThat(third.get("dispositions")).isEqualTo(second.get("dispositions"));assertThat(f.count("dispatch_record")).isEqualTo(1);
    }
    private JsonNode activate(SubmissionFixture f,JsonNode ticket,RecallFixture r) throws Exception {
        var command=SubmissionJson.object().put("payloadSha256",SubmissionJson.sha(r.body)).put("retainedManifestSha256",ticket.get("retainedManifestSha256").asText())
            .put("nextOrderScanSeq",ticket.get("nextOrderScanSeq").asText()).put("planSha256",f.assignment.get("planSha256").asText());
        return f.pda.data(post("/api/v1/pda/continuations/"+ticket.get("continuationId").asText()+"/activate").header("X-Server-Recovery-Epoch","1")
            .contentType("application/json").content(command.toString()),f.tokens);
    }
    private ObjectNode third(ObjectNode previous,RecallFixture recall,JsonNode activation) {
        var a=activation.get("assignment");var body=previous.deepCopy();String id=UUID.randomUUID().toString();
        body.put("submissionId",id).put("assignmentId",a.get("id").asText()).put("assignmentEpoch",a.get("epoch").asText()).put("workRound","3")
            .put("continuationId",activation.get("continuationId").asText()).put("closedAt",java.time.Instant.parse(a.get("issuedAt").asText()).plusMillis(200).toString());
        var events=recall.body.withArray("events").deepCopy();var last=(ObjectNode)events.get(events.size()-1);
        events.add(last.deepCopy().put("id",UUID.randomUUID().toString()).put("assignmentId",a.get("id").asText()).put("localSeq",Long.toString(Long.parseLong(last.get("localSeq").asText())+1))
            .put("type","FROZEN").put("detail",id));body.set("events",events);return body;
    }
    @Test void concurrentApprovalsProduceOnlyOneImmutableEvidence() throws Exception {
        var d=fixture("RELEASE");var body=d.evidenceBody();var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->{start.await();return d.c.r.f.web.send(d.approveEvidence(body,UUID.randomUUID().toString())).andReturn().getResponse().getStatus();});
            var b=pool.submit(()->{start.await();return d.c.r.f.web.send(d.approveEvidence(body,UUID.randomUUID().toString())).andReturn().getResponse().getStatus();});start.countDown();
            assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        }
        assertThat(d.c.r.f.count("physical_disposition_evidence")).isEqualTo(1);assertThat(d.c.r.f.count("bale_commitment")).isEqualTo(1);
    }
    @Test void completedPhysicalRemovalCannotBeRequestedAsRetained() throws Exception {
        var d=fixture("RELEASE");d.approveEvidence();
        d.c.r.f.web.send(d.c.request(d.c.requestBody(),UUID.randomUUID().toString())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DISPOSITION_EVIDENCE_REQUIRED"));
    }
    @Test void zeroBaleReplacementDoesNotReleasePendingRemoval() throws Exception {
        var d=fixture("RELEASE");d.activate();var body=d.second();body.set("captures",d.c.r.body.get("captures"));body.set("scans",d.c.r.body.get("scans"));
        var events=d.c.r.body.withArray("events").deepCopy();var freeze=(ObjectNode)body.withArray("events").get(body.withArray("events").size()-1);
        events.add(freeze.deepCopy().put("localSeq","6"));body.set("events",events);
        body.put("expectedCount","0").put("expectedWeightGrams","0").put("captureHighWatermark","1");((ObjectNode)body.get("captureSummary")).put("received","1").put("saved","0");
        d.c.r.f.send(body).andExpect(status().isBadRequest());assertThat(d.c.r.f.count("bale_commitment")).isEqualTo(1);
    }
    @Test void physicalEvidenceNeedsDedicatedPermission() throws Exception {
        var d=fixture("RELEASE");UUID user=d.c.r.f.web.identity().user().id();var role=Ids.next();
        jdbc.update("INSERT INTO role VALUES(?,?,?)",Ids.bytes(role),"DISP_"+role.toString().substring(20),"无实物处置权限");
        jdbc.update("INSERT INTO role_permission SELECT ?,p.id FROM permission p WHERE p.code IN ('order:read','order:recall','order:resume')",Ids.bytes(role));
        jdbc.update("DELETE FROM user_role WHERE user_id=?",Ids.bytes(user));jdbc.update("INSERT INTO user_role VALUES(?,?)",Ids.bytes(user),Ids.bytes(role));
        d.c.r.f.web.send(d.approveEvidence(d.evidenceBody(),UUID.randomUUID().toString())).andExpect(status().isForbidden());
    }
}
