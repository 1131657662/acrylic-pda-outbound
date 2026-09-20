package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class PdaAuditIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    private SubmissionFixture f;
    @BeforeEach void fixture() throws Exception {
        f=new SubmissionFixture(new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN")),jdbc,false,false);
    }
    @Test void outOfOrderDoesNotAcknowledgeGapAndReplayKeepsOneOriginalEvent() throws Exception {
        var late=body(3,4);assertThat(upload(late).get("acknowledgedThrough").asText()).isEqualTo("0");
        var early=body(0,3);var result=upload(early);assertThat(result.get("acknowledgedThrough").asText()).isEqualTo("4");
        assertThat(result.get("batchSha256").asText()).isEqualTo(SubmissionJson.sha(early));
        assertThat(upload(late).get("acknowledgedThrough").asText()).isEqualTo("4");
        assertThat(count()).isEqualTo(4);
    }
    @Test void changedUuidOrSequenceContentIsRejectedWithoutPartialBatchWrite() throws Exception {
        upload(body(0,1));var changed=body(1,2);
        changed.withArray("events").add(((ObjectNode)f.body.at("/events/0")).deepCopy().put("detail","篡改"));
        f.pda.send(request(changed),f.tokens).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AUDIT_CONTENT_CONFLICT"));
        assertThat(count()).isEqualTo(1);
    }
    @Test void auditUploadBeforeSubmissionRequiresSameOriginalFacts() throws Exception {
        upload(body(0,7));assertThat(f.submit().get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(upload(body(0,7)).get("acknowledgedThrough").asText()).isEqualTo("7");assertThat(count()).isEqualTo(7);
    }
    @Test void priorSubmittedFactCannotBeRewrittenThroughGenericUpload() throws Exception {
        f.submit();var changed=body(0,1);((ObjectNode)changed.at("/events/0")).put("id",UUID.randomUUID().toString());
        f.pda.send(request(changed),f.tokens).andExpect(status().isConflict());assertThat(count()).isZero();
    }
    @Test void otherDeviceOperatorAndWarehouseAreRejected() throws Exception {
        for(String field:List.of("originDeviceId","operatorId","assignmentId")) {
            var changed=body(0,1);((ObjectNode)changed.at("/events/0")).put(field,UUID.randomUUID().toString());
            f.pda.send(request(changed),f.tokens).andExpect(status().isBadRequest());
        }
        var other=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));
        var pda=new PdaTestDriver(other);var tokens=pda.login(pda.register());
        pda.send(request(body(0,1)),tokens).andExpect(status().isBadRequest());assertThat(count()).isZero();
    }
    @Test void oversizedUnknownFieldsAndDuplicateSequenceAreRejected() throws Exception {
        var extra=body(0,1);((ObjectNode)extra.at("/events/0")).put("secret","forbidden");
        f.pda.send(request(extra),f.tokens).andExpect(status().isBadRequest());
        var duplicate=body(0,1);duplicate.withArray("events").add(duplicate.at("/events/0"));
        f.pda.send(request(duplicate),f.tokens).andExpect(status().isBadRequest());
        var large=body(0,0);for(int i=0;i<201;i++) large.withArray("events").add(f.body.at("/events/0"));
        f.pda.send(request(large),f.tokens).andExpect(status().isBadRequest());assertThat(count()).isZero();
    }
    @Test void concurrentDevicesCannotSilentlyShareAnEventUuid() throws Exception {
        var other=new SubmissionFixture(new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN")),jdbc,false,false);
        var first=body(0,1);var second=json.createObjectNode().put("originDeviceId",other.tokens.get("deviceId").asText());
        second.putArray("events").add(((ObjectNode)other.body.at("/events/0")).deepCopy().put("id",first.at("/events/0/id").asText()));
        var start=new java.util.concurrent.CountDownLatch(1);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->{ start.await();return f.pda.send(request(first),f.tokens).andReturn().getResponse().getStatus(); });
            var b=pool.submit(()->{ start.await();return other.pda.send(request(second),other.tokens).andReturn().getResponse().getStatus(); });
            start.countDown();assertThat(java.util.List.of(a.get(15,java.util.concurrent.TimeUnit.SECONDS),b.get(15,java.util.concurrent.TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(200,409);
        }
    }
    @Test @Transactional void recoveryEpochChangeCreatesSeparateConfirmationWithoutChangingSource() throws Exception {
        upload(body(0,7));jdbc.update("UPDATE server_state SET recovery_epoch=2 WHERE id=1");
        f.pda.send(request(body(0,7)),f.tokens).andExpect(status().isConflict());
        var result=f.pda.data(post("/api/v1/pda/audit/events").header("X-Server-Recovery-Epoch","2")
            .contentType("application/json").content(body(0,7).toString()),f.tokens);
        assertThat(result.get("recoveryEpoch").asText()).isEqualTo("2");assertThat(result.get("acknowledgedThrough").asText()).isEqualTo("7");
        assertThat(jdbc.queryForList("SELECT source_recovery_epoch FROM pda_audit_event WHERE origin_device_id=UUID_TO_BIN(?)",Long.class,f.tokens.get("deviceId").asText())).containsOnly(1L);
    }
    private ObjectNode body(int from,int to) {
        var body=json.createObjectNode().put("originDeviceId",f.tokens.get("deviceId").asText());var rows=body.putArray("events");
        for(int i=from;i<to;i++) rows.add(f.body.at("/events/"+i).deepCopy());return body;
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(ObjectNode body) {
        return post("/api/v1/pda/audit/events").header("X-Server-Recovery-Epoch","1").contentType("application/json").content(body.toString());
    }
    private JsonNode upload(ObjectNode body) throws Exception { return f.pda.data(request(body),f.tokens); }
    private int count() { return jdbc.queryForObject("SELECT COUNT(*) FROM pda_audit_event WHERE origin_device_id=UUID_TO_BIN(?)",Integer.class,f.tokens.get("deviceId").asText()); }
}
