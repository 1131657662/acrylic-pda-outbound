package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class TemporaryAuditIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    private TemporaryAuditFixture a;private SubmissionFixture f;
    @BeforeEach void setup() throws Exception {
        f=new SubmissionFixture(new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN")),jdbc,false,false);
        a=new TemporaryAuditFixture(f);
    }
    @Test void mixedOutOfOrderReplayArchivesWithoutChangingOriginalFormalDigest() throws Exception {
        var temp=a.event(8);var mixed=a.mixed(temp);String original=SubmissionJson.sha(f.body);
        assertThat(a.send(a.batch(temp)).get("acknowledgedThrough").asText()).isEqualTo("0");
        assertThat(a.send(mixed).get("acknowledgedThrough").asText()).isEqualTo("8");
        assertThat(a.send(mixed).get("acknowledgedThrough").asText()).isEqualTo("8");
        var proof=a.archive(mixed);assertThat(a.archive(mixed)).isEqualTo(proof);
        assertThat(proof.get("localDeletionAllowed").asBoolean()).isFalse();
        assertThat(a.count("pda_audit_event")).isEqualTo(8);assertThat(a.count("temporary_work_origin")).isEqualTo(1);
        assertThat(f.submit().get("status").asText()).isEqualTo("SUCCEEDED");assertThat(SubmissionJson.sha(f.body)).isEqualTo(original);
    }
    @ParameterizedTest @ValueSource(strings={"operatorId","authorizationId","creatorDeviceId"})
    void forgedOriginRollsBackEntireMixedBatch(String field) throws Exception {
        var row=a.event(8);
        if(field.equals("creatorDeviceId"))((ObjectNode)row.get("source")).put(field,UUID.randomUUID().toString());
        else row.put(field,UUID.randomUUID().toString());
        f.pda.send(a.upload(a.mixed(row)),f.tokens).andExpect(status().isBadRequest());
        assertThat(a.count("pda_audit_event")).isZero();assertThat(a.count("temporary_work_origin")).isZero();
    }
    @Test void sourceCannotSwitchToNewAuthorization() throws Exception {
        a.send(a.batch(a.event(1)));var next=a.prepare();a.acknowledge(next);
        var row=a.event(2).put("authorizationId",next.get("authorizationId").asText());
        f.pda.send(a.upload(a.batch(row)),f.tokens).andExpect(status().isBadRequest());
        assertThat(a.count("pda_audit_event")).isEqualTo(1);assertThat(a.count("temporary_work_origin")).isEqualTo(1);
    }
    @Test void unacknowledgedPreparationCanPreserveEvidenceWithoutAcknowledgingActivation() throws Exception {
        var next=a.prepare();var row=a.event(1).put("authorizationId",next.get("authorizationId").asText());
        assertThat(a.send(a.batch(row)).get("acknowledgedThrough").asText()).isEqualTo("1");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM temporary_preparation_ack WHERE authorization_id=UUID_TO_BIN(?)",
            Integer.class,next.get("authorizationId").asText())).isZero();
    }
    @Test void oldEvidenceStillUploadsAfterTemporaryCreationIsDisabled() throws Exception {
        jdbc.update("UPDATE device SET temporary_order_enabled=0 WHERE id=UUID_TO_BIN(?)",a.device());
        jdbc.update("DELETE FROM user_role WHERE user_id=UUID_TO_BIN(?) AND role_id=(SELECT id FROM role WHERE code='TEMPORARY_ORDER_CREATOR')",f.tokens.get("userId").asText());
        assertThat(a.send(a.batch(a.event(1))).get("acknowledgedThrough").asText()).isEqualTo("1");
        assertThat(a.count("temporary_work_origin")).isEqualTo(1);
    }
    @Test void crossSourceSequenceCollisionRollsBackNewOrigin() throws Exception {
        var body=a.batch(f.body.at("/events/0"));a.send(body);
        f.pda.send(a.upload(a.batch(a.event(1))),f.tokens).andExpect(status().isConflict());
        assertThat(a.count("pda_audit_event")).isEqualTo(1);assertThat(a.count("temporary_work_origin")).isZero();
    }
    @Test void expiredQualificationStillPreservesOriginalEvidence() throws Exception {
        jdbc.update("UPDATE temporary_authorization SET issued_at=DATE_SUB(issued_at,INTERVAL 3 DAY),expires_at=DATE_SUB(expires_at,INTERVAL 3 DAY) WHERE id=UUID_TO_BIN(?)",
            a.authorization.get("authorizationId").asText());
        assertThat(a.send(a.batch(a.event(1))).get("acknowledgedThrough").asText()).isEqualTo("1");
    }
    @Test void concurrentFirstEventsCannotBindOneClientOrderToTwoQualifications() throws Exception {
        var next=a.prepare();a.acknowledge(next);
        var one=a.batch(a.event(1));var two=a.batch(a.event(2).put("authorizationId",next.get("authorizationId").asText()));
        var start=new java.util.concurrent.CountDownLatch(1);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->{start.await();return f.pda.send(a.upload(one),f.tokens).andReturn().getResponse().getStatus();});
            var second=pool.submit(()->{start.await();return f.pda.send(a.upload(two),f.tokens).andReturn().getResponse().getStatus();});
            start.countDown();assertThat(java.util.List.of(first.get(15,java.util.concurrent.TimeUnit.SECONDS),second.get(15,java.util.concurrent.TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(200,400);
        }
        assertThat(a.count("pda_audit_event")).isEqualTo(1);assertThat(a.count("temporary_work_origin")).isEqualTo(1);
    }
    @Test @Transactional void signedMixedArchiveRestoresInNewEpochKeepingOriginalQualification() throws Exception {
        var mixed=a.mixed(a.event(8));a.send(mixed);var proof=a.archive(mixed);
        jdbc.update("DELETE FROM pda_audit_event WHERE origin_device_id=UUID_TO_BIN(?)",a.device());
        jdbc.update("DELETE FROM temporary_work_origin WHERE creator_device_id=UUID_TO_BIN(?)",a.device());
        jdbc.update("UPDATE server_state SET recovery_epoch=2 WHERE id=1");
        var body=SubmissionJson.object();body.set("archive",proof);
        var request=org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/pda/audit/archives/restore")
            .header("X-Server-Recovery-Epoch","2").contentType("application/json").content(body.toString());
        var restored=f.pda.data(request,f.tokens);
        assertThat(restored.get("payloadSha256")).isEqualTo(proof.get("payloadSha256"));
        assertThat(restored.get("recoveryEpoch").asText()).isEqualTo("2");assertThat(a.count("pda_audit_event")).isEqualTo(8);
        assertThat(jdbc.queryForList("SELECT source_recovery_epoch FROM pda_audit_event WHERE origin_device_id=UUID_TO_BIN(?)",Long.class,a.device())).containsOnly(1L);
    }
    @Test void formalRecallKeepsTemporaryEventsInsideCompleteDeviceCoverage() throws Exception {
        var temp=a.event(8);a.send(a.mixed(temp));var recall=new RecallFixture(f);recall.start(false);
        var stop=(ObjectNode)recall.body.withArray("events").get(7);stop.put("localSeq","9");
        var all=recall.body.putArray("auditEvents");all.addAll(f.body.withArray("events"));all.add(temp);all.add(stop);
        ((ObjectNode)recall.body.at("/auditCoverage/0")).put("throughSeq","9");
        recall.upload();recall.stop();
        assertThat(a.count("temporary_work_origin")).isEqualTo(1);
        var changed=temp.deepCopy().put("detail","rewritten after recovery");
        f.pda.send(a.upload(a.batch(changed)),f.tokens).andExpect(status().isBadRequest());
    }
}
