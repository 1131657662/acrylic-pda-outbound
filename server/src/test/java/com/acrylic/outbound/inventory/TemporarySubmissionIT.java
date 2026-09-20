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
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class TemporarySubmissionIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    private TemporarySubmissionFixture t;
    @BeforeEach void setup() throws Exception {
        t=new TemporarySubmissionFixture(new SubmissionFixture(new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN")),jdbc,false,false));
    }
    @Test void retryUsesOneCanonicalOrderAndNeverDispatches() throws Exception {
        var result=t.submit();assertThat(result.get("status").asText()).isEqualTo("REVIEW_REQUIRED");assertThat(t.submit()).isEqualTo(result);assertThat(t.read()).isEqualTo(result);
        assertThat(t.orders()).isEqualTo(1);assertThat(t.submissions()).isEqualTo(1);
        String order=result.get("orderId").asText();
        assertThat(jdbc.queryForObject("SELECT order_no FROM outbound_order WHERE id=UUID_TO_BIN(?)",String.class,order)).matches("FH[0-9]{12,}");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_record WHERE order_id=UUID_TO_BIN(?)",Integer.class,order)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE submission_id=UUID_TO_BIN(?)",Integer.class,t.id())).isEqualTo(2);
    }
    @Test void abortBeforePayloadCreatesOnlyControlAndCannotResurrect() throws Exception {
        var result=t.abort();assertThat(t.abort()).isEqualTo(result);assertThat(t.read()).isEqualTo(result);
        assertThat(result.get("orderId").isNull()).isTrue();assertThat(result.get("payloadSha256").isNull()).isTrue();
        assertThat(result.get("stockReleased").asBoolean()).isFalse();assertThat(t.orders()).isZero();assertThat(t.submissions()).isEqualTo(1);
        t.f.pda.send(t.request("/api/v1/pda/temporary-orders/submissions",t.body),t.f.tokens).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_event WHERE entity_id=UUID_TO_BIN(?) AND event_type='SUBMISSION_ABORTED'",Integer.class,t.id())).isEqualTo(1);
    }
    @Test void abortAfterUploadPreservesReservation() throws Exception {
        t.submit();var result=t.abort();assertThat(result.get("payloadPresent").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE submission_id=UUID_TO_BIN(?)",Integer.class,t.id())).isEqualTo(2);
    }
    @Test void newIdCannotReplaceFrozenSource() throws Exception {
        t.submit();t.body.put("submissionId",UUID.randomUUID().toString());
        t.f.pda.send(t.request("/api/v1/pda/temporary-orders/submissions",t.body),t.f.tokens).andExpect(status().isConflict());assertThat(t.orders()).isEqualTo(1);
    }
    @Test void sameIdCannotRewriteBody() throws Exception {
        t.submit();t.body.put("temporaryNo","changed");
        t.f.pda.send(t.request("/api/v1/pda/temporary-orders/submissions",t.body),t.f.tokens).andExpect(status().isConflict());
    }
    @Test void originalSourceCannotChangeQualificationOrRecoveryEpoch() throws Exception {
        t.abort();var next=t.a.prepare();t.a.acknowledge(next);
        var changed=t.control().put("authorizationId",next.get("authorizationId").asText()).put("reason","不得换资格");
        t.f.pda.send(t.request(t.base()+"/abort",changed),t.f.tokens).andExpect(status().isBadRequest());
        changed=t.control().put("sourceRecoveryEpoch","2").put("reason","不得换代次");
        t.f.pda.send(t.request(t.base()+"/abort",changed),t.f.tokens).andExpect(status().isConflict());
        assertThat(t.orders()).isZero();assertThat(t.submissions()).isEqualTo(1);
    }
    @Test void staleAdmissionPreservesEvidenceWithExplicitReviewIssue() throws Exception {
        jdbc.update("UPDATE device SET temporary_order_enabled=0 WHERE id=UUID_TO_BIN(?)",t.a.device());
        var result=t.submit();assertThat(result.get("status").asText()).isEqualTo("REVIEW_REQUIRED");
        assertThat(result.get("issues").toString()).contains("TEMPORARY_ADMISSION_DISABLED");
    }
    @Test void shortPlanRequiresReasonAndRemainsPendingIndependentConfirmation() throws Exception {
        ((ObjectNode)t.body.at("/temporaryOrder/lines/0")).put("plannedCount","2");t.body.put("planSha256",SubmissionJson.sha(t.body.get("temporaryOrder")));
        t.f.pda.send(t.request("/api/v1/pda/temporary-orders/submissions",t.body),t.f.tokens).andExpect(status().isBadRequest());
        t.body.put("shortShipmentReason","已核实短装");assertThat(t.submit().get("issues").toString()).contains("SHORT_SHIPMENT_APPROVAL_REQUIRED");
    }
    @ParameterizedTest @ValueSource(strings={"APPROVE","REJECT"}) void formalReviewCannotBypassIndependentPermission(String decision) throws Exception {
        t.submit();var body=SubmissionJson.object().put("decision",decision).put("reason","不能走正式审批").put("payloadSha256",SubmissionJson.sha(t.body));
        t.f.web.send(t.request("/api/v1/submissions/"+t.id()+"/review",body)).andExpect(status().isConflict());
        assertThat(t.read().get("status").asText()).isEqualTo("REVIEW_REQUIRED");
    }
    @ParameterizedTest @ValueSource(strings={"plan","cycle","event","time"}) void forgedEvidenceRollsBackControlAndOrder(String field) throws Exception {
        switch(field) {
            case "plan" -> ((ObjectNode)t.body.at("/temporaryOrder/lines/0")).put("plannedCount","2");
            case "cycle" -> ((ObjectNode)t.body.at("/scans/0")).put("expectedDispatchCycle","2");
            case "event" -> ((ObjectNode)t.body.at("/events/0")).put("authorizationId",UUID.randomUUID().toString());
            case "time" -> ((ObjectNode)t.body.at("/scans/0")).put("savedAt","2100-01-01T00:00:00Z");
        }
        t.f.pda.send(t.request("/api/v1/pda/temporary-orders/submissions",t.body),t.f.tokens).andExpect(status().isBadRequest());
        assertThat(t.orders()).isZero();assertThat(t.submissions()).isZero();
    }
}
