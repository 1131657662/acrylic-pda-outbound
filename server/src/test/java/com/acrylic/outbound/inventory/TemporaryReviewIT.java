package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class TemporaryReviewIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    private TemporaryReviewFixture f;
    @BeforeEach void setup() throws Exception { f=new TemporaryReviewFixture(jdbc,mvc,json,passwords); }
    @Test void readsOriginalEvidenceWithoutChangingReviewOrInventoryFacts() throws Exception {
        var t=f.temporary;var result=t.submit();String before=f.facts();
        var detail=f.detail();
        assertThat(detail.path("source")).isEqualTo(t.body.path("source"));
        assertThat(detail.path("payloadSha256").asText()).isEqualTo(SubmissionJson.sha(t.body));
        assertThat(detail.path("orderId")).isEqualTo(result.path("orderId"));
        assertThat(detail.path("writeConfirmationAvailable").asBoolean()).isFalse();
        assertThat(detail.path("releaseAllowed").asBoolean()).isFalse();
        assertThat(detail.path("current").asBoolean()).isTrue();
        assertThat(detail.path("products")).hasSize(2);
        assertThat(detail.at("/products/0/actualCount").asText()).isEqualTo("1");
        assertThat(detail.path("currentIssues").toString()).contains("TEMPORARY_ORDER_CONFIRMATION_REQUIRED").doesNotContain("INVENTORY_UNAVAILABLE");
        assertThat(f.list().path("items")).hasSize(1);assertThat(f.facts()).isEqualTo(before);
    }
    @Test void currentStockConflictAndShortPlanNeverImplyPermissionToDispatch() throws Exception {
        var t=f.temporary;((ObjectNode)t.body.at("/temporaryOrder/lines/0")).put("plannedCount","2");
        t.body.put("planSha256",SubmissionJson.sha(t.body.path("temporaryOrder"))).put("shortShipmentReason","现场少一包");t.submit();
        jdbc.update("UPDATE inventory_position SET upstream_present=0 WHERE bale_id=UUID_TO_BIN(?)",t.body.at("/scans/0/baleId").asText());
        String before=f.facts();var detail=f.detail();
        assertThat(detail.path("currentIssues").toString()).contains("SHORT_SHIPMENT_APPROVAL_REQUIRED","INVENTORY_UNAVAILABLE");
        assertThat(detail.at("/products/0/shortCount").asText()).isEqualTo("1");
        assertThat(detail.path("receivedIssues").toString()).doesNotContain("INVENTORY_UNAVAILABLE");
        assertThat(f.facts()).isEqualTo(before);
    }
    @Test void originalQualificationAndCurrentIdentityChangesAreVisibleToIndependentReviewer() throws Exception {
        var t=f.temporary;t.submit();
        jdbc.update("UPDATE temporary_authorization SET issued_at=issued_at-INTERVAL 3 DAY,expires_at=expires_at-INTERVAL 3 DAY WHERE id=UUID_TO_BIN(?)",t.body.path("authorizationId").asText());
        jdbc.update("UPDATE app_user SET status='DISABLED',auth_version=auth_version+1 WHERE id=UUID_TO_BIN(?)",t.f.tokens.path("userId").asText());
        jdbc.update("UPDATE device SET status='DISABLED',temporary_order_enabled=0,row_version=row_version+1 WHERE id=UUID_TO_BIN(?)",t.a.device());
        jdbc.update("DELETE FROM user_role WHERE user_id=UUID_TO_BIN(?)",t.f.tokens.path("userId").asText());
        jdbc.update("DELETE FROM user_warehouse WHERE user_id=UUID_TO_BIN(?)",t.f.tokens.path("userId").asText());
        String before=f.facts();var detail=f.detail();
        assertThat(detail.path("currentIssues").toString()).contains("TEMPORARY_AUTH_EXPIRED","TEMPORARY_AUTH_VERSION_CHANGED",
            "TEMPORARY_OPERATOR_DISABLED","TEMPORARY_DEVICE_DISABLED","TEMPORARY_ADMISSION_DISABLED",
            "TEMPORARY_OPERATOR_PERMISSION_CHANGED","TEMPORARY_OPERATOR_WAREHOUSE_CHANGED");
        assertThat(f.facts()).isEqualTo(before);
    }
    @Test void recoveryBarrierRemainsReadableAndNeverMutatesOldEvidence() throws Exception {
        f.temporary.submit();String before=f.facts();
        try {
            jdbc.update("UPDATE server_state SET recovery_epoch=2,service_mode='RECOVERY_PENDING' WHERE id=1");
            var detail=f.detail();assertThat(detail.path("currentIssues").toString()).contains("TEMPORARY_RECOVERY_REQUIRED","SERVER_RECOVERY_REQUIRED");
            assertThat(detail.path("sourceRecoveryEpoch").asText()).isEqualTo("1");assertThat(f.facts()).isEqualTo(before);
        } finally { jdbc.update("UPDATE server_state SET recovery_epoch=1,service_mode='NORMAL' WHERE id=1"); }
    }
    @Test void abortedBatchLeavesInboxButItsOriginalEvidenceCanBeRead() throws Exception {
        f.temporary.submit();f.temporary.abort();
        assertThat(f.list().path("items")).isEmpty();var detail=f.detail();
        assertThat(detail.path("status").asText()).isEqualTo("ABORTED");
        assertThat(detail.path("currentIssues").toString()).contains("SUBMISSION_NOT_PENDING");
        assertThat(detail.path("releaseAllowed").asBoolean()).isFalse();
    }
    @Test void abortBeforeBodyDoesNotCreateReviewableOrder() throws Exception {
        f.temporary.abort();assertThat(f.list().path("items")).isEmpty();
        f.reviewer.send(get(f.path())).andExpect(status().isNotFound());
    }
    @Test void scanCursorIsBoundToTheOriginalBatchAndReadsActualSequence() throws Exception {
        f.temporary.submit();var first=f.reviewer.data(get(f.path()+"/scans").param("limit","1"));
        assertThat(first.at("/items/0/orderScanSeq").asText()).isEqualTo("1");
        var next=f.reviewer.data(get(f.path()+"/scans").param("limit","1").param("cursor",first.path("nextCursor").asText()));
        assertThat(next.at("/items/0/orderScanSeq").asText()).isEqualTo("2");assertThat(next.path("hasMore").asBoolean()).isFalse();
        f.reviewer.send(get(f.path()+"/scans").param("cursor","bad")).andExpect(status().isBadRequest());
    }
    @ParameterizedTest @ValueSource(ints={-1,0,201}) void invalidPageSizesAreRejected(int limit) throws Exception {
        f.reviewer.send(get("/api/v1/temporary-reviews").param("limit",Integer.toString(limit))).andExpect(status().isBadRequest());
        f.reviewer.send(get(f.path()+"/scans").param("limit",Integer.toString(limit))).andExpect(status().isBadRequest());
    }
}
