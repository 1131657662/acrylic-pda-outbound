package com.acrylic.outbound.inventory;

import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
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
class TemporaryReviewPaginationIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    private TemporaryReviewFixture f;
    @BeforeEach void setup() throws Exception { f=new TemporaryReviewFixture(jdbc,mvc,json,passwords);f.temporary.submit(); }
    @Test void equalReceptionTimesUseUniqueIdsAndSurviveRemovedBoundary() throws Exception {
        var second=new TemporarySubmissionFixture(new SubmissionFixture(f.temporary.f));second.submit();
        jdbc.update("UPDATE submission_payload SET received_at='2026-09-20 01:00:00.000' WHERE submission_id IN (UUID_TO_BIN(?),UUID_TO_BIN(?))",f.temporary.id(),second.id());
        var first=page("");String boundary=first.at("/items/0/submissionId").asText();
        assertThat(first.path("hasMore").asBoolean()).isTrue();
        if(boundary.equals(second.id())) second.abort(); else f.temporary.abort();
        var next=page(first.path("nextCursor").asText());
        assertThat(next.path("items")).hasSize(1);assertThat(next.at("/items/0/submissionId").asText()).isNotEqualTo(boundary);
        assertThat(next.path("hasMore").asBoolean()).isFalse();
    }
    @Test void scanCursorCannotBeReusedForAnotherSubmission() throws Exception {
        var first=f.reviewer.data(get(f.path()+"/scans").param("limit","1"));
        var second=new TemporarySubmissionFixture(new SubmissionFixture(f.temporary.f));second.submit();
        f.reviewer.send(get("/api/v1/temporary-reviews/"+second.id()+"/scans").param("cursor",first.path("nextCursor").asText()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
    }
    @Test void historicalPendingBatchIsHiddenAndDetailReportsItsActualCurrentPointer() throws Exception {
        jdbc.update("UPDATE submission_scope SET current_submission_id=NULL WHERE current_submission_id=UUID_TO_BIN(?)",f.temporary.id());
        assertThat(f.list().path("items")).isEmpty();var detail=f.detail();
        assertThat(detail.path("current").asBoolean()).isFalse();assertThat(detail.path("currentSubmissionId").isNull()).isTrue();
        assertThat(detail.path("currentIssues").toString()).contains("SUBMISSION_NOT_CURRENT");
    }
    @ParameterizedTest @ValueSource(strings={"invalid","other-warehouse","invalid-time","invalid-id","too-long"})
    void malformedListCursorsAreRejected(String kind) throws Exception {
        String warehouse=f.reviewer.identity().warehouse().toString();String id=f.temporary.id();
        String raw=switch(kind) {
            case "other-warehouse"->UUID.randomUUID()+"|2026-09-20T01:00:00Z|"+id;
            case "invalid-time"->warehouse+"|+10000-01-01T00:00:00Z|"+id;
            case "invalid-id"->warehouse+"|2026-09-20T01:00:00Z|1-1-1-1-1";
            default->"invalid";
        };
        String cursor=kind.equals("too-long")?"x".repeat(201):Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.US_ASCII));
        f.reviewer.send(get("/api/v1/temporary-reviews").param("cursor",cursor)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
    }
    @ParameterizedTest @ValueSource(strings={"CYCLE","BASELINE","COMMITMENT"})
    void dynamicConflictsAreReportedWithoutReservingOrChangingFacts(String kind) throws Exception {
        String bale=f.temporary.body.at("/scans/0/baleId").asText();String expected;
        switch(kind) {
            case "CYCLE"->{ jdbc.update("UPDATE bale SET dispatch_cycle=dispatch_cycle+1 WHERE id=UUID_TO_BIN(?)",bale);expected="DISPATCH_CYCLE_CONFLICT"; }
            case "BASELINE"->{ jdbc.update("UPDATE inventory_snapshot_bale SET weight_g=weight_g+1 WHERE bale_id=UUID_TO_BIN(?)",bale);expected="INVENTORY_BASELINE_CHANGED"; }
            default->{ jdbc.update("DELETE FROM bale_commitment WHERE bale_id=UUID_TO_BIN(?)",bale);expected="TEMPORARY_RESERVATION_RECHECK_REQUIRED"; }
        }
        String before=f.facts();assertThat(f.detail().path("currentIssues").toString()).contains(expected);
        var scans=f.reviewer.data(get(f.path()+"/scans"));assertThat(scans.at("/items/0/currentIssue").asText()).isEqualTo(expected);
        assertThat(f.facts()).isEqualTo(before);
    }
    private JsonNode page(String cursor) throws Exception {
        return f.reviewer.data(get("/api/v1/temporary-reviews").param("limit","1").param("cursor",cursor));
    }
}
