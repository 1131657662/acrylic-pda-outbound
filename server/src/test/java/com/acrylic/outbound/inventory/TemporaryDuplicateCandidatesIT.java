package com.acrylic.outbound.inventory;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.time.*;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class TemporaryDuplicateCandidatesIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    private TemporaryDuplicateFixture f;
    @BeforeEach void setup() throws Exception { f=new TemporaryDuplicateFixture(jdbc,mvc,json,passwords); }
    @Test void readyComparisonPreservesEveryOriginalFactAndProducesWebSample() throws Exception {
        String id=f.create(true);String before=f.facts();var response=f.page();var row=f.candidate(response,id);
        assertThat(row.path("signals").toString()).contains("SAME_VEHICLE","SHARED_PRODUCT");
        assertThat(row.path("blockers")).isEmpty();assertThat(row.path("linkAvailable").asBoolean()).isFalse();
        assertThat(response.path("total").asInt()).isEqualTo(1);assertThat(response.path("writeAvailable").asBoolean()).isFalse();
        assertThat(response.path("releaseAllowed").asBoolean()).isFalse();
        assertThat(response.at("/context/source")).isEqualTo(f.review.temporary.body.path("source"));
        assertThat(response.at("/context/canonicalOrderId").asText()).isEqualTo(f.review.detail().path("orderId").asText());
        assertThat(response.at("/context/payloadSha256")).isEqualTo(f.review.detail().path("payloadSha256"));
        assertThat(f.facts()).isEqualTo(before);
        var sample=Path.of("build/temporary-duplicate-contract.json");Files.createDirectories(sample.getParent());Files.writeString(sample,json.writeValueAsString(response));
    }
    @Test void differentVehicleWithSharedProductIsOnlyAPossibleMatch() throws Exception {
        var plan=f.plan().put("vehicleNo","不同车同产品");String id=f.orders.create(plan).path("orderId").asText();
        var row=f.candidate(f.page(),id);
        assertThat(row.path("signals").toString()).doesNotContain("SAME_VEHICLE").contains("SHARED_PRODUCT");
        assertThat(row.path("blockers").toString()).contains("TARGET_NOT_READY");
    }
    @Test void mismatchedPlanAndExistingPdaWorkAreReported() throws Exception {
        var plan=f.plan();((ObjectNode)plan.at("/lines/0")).put("plannedCount",2);
        String id=f.orders.create(plan).path("orderId").asText();f.publish(id);
        var pda=f.review.temporary.f.pda;var tokens=pda.login(pda.register());pda.data(pda.claim(id,UUID.randomUUID().toString()),tokens);
        var row=f.candidate(f.page(),id);
        assertThat(row.path("blockers").toString()).contains("PLAN_DIFFERS","PDA_WORK_EXISTS","TARGET_NOT_READY");
    }
    @Test void pcWorkBlocksAssociationEvenBeforeAnyEntry() throws Exception {
        String id=f.create(false);var body=json.createObjectNode().put("expectedVersion","1").put("writerId",UUID.randomUUID().toString())
            .put("policyVersion","1").put("reason","只读候选实物边界验证");
        f.orders.api().data(post("/api/v1/orders/"+id+"/pc/start").header("Idempotency-Key",UUID.randomUUID().toString())
            .contentType("application/json").content(json.writeValueAsBytes(body)));
        assertThat(f.candidate(f.page(),id).path("blockers").toString()).contains("PC_WORK_EXISTS","TARGET_NOT_READY");
    }
    @Test void formalSubmissionAndTerminalSourceNeverBecomeLinkable() throws Exception {
        var formal=new SubmissionFixture(f.review.temporary.f);formal.submit();
        jdbc.update("UPDATE outbound_order SET created_at=UTC_TIMESTAMP(3) WHERE id=UUID_TO_BIN(?)",formal.order);
        f.review.temporary.abort();var result=f.page();var row=f.candidate(result,formal.order);
        assertThat(row.path("blockers").toString()).contains("SUBMISSION_EXISTS","PDA_WORK_EXISTS","SOURCE_NOT_PENDING");
        assertThat(row.path("linkAvailable").asBoolean()).isFalse();
    }
    @Test void selectedBeijingRangeIncludesBothDayBoundariesAndIsNot48HourPolicy() throws Exception {
        String first=f.create(true),last=f.create(true),outside=f.create(true);
        jdbc.update("UPDATE outbound_order SET created_at='2026-09-09 16:00:00' WHERE id=UUID_TO_BIN(?)",first);
        jdbc.update("UPDATE outbound_order SET created_at='2026-09-12 15:59:59.999' WHERE id=UUID_TO_BIN(?)",last);
        jdbc.update("UPDATE outbound_order SET created_at='2026-09-12 16:00:00' WHERE id=UUID_TO_BIN(?)",outside);
        var response=f.review.reviewer.data(f.request().param("fromDate","2026-09-10").param("throughDate","2026-09-12"));
        assertThat(response.path("items")).hasSize(2);assertThat(response.path("total").asInt()).isEqualTo(2);
        assertThat(f.candidate(response,first).at("/order/id").asText()).isEqualTo(first);
        assertThat(f.candidate(response,last).at("/order/id").asText()).isEqualTo(last);
    }
    @ParameterizedTest @ValueSource(strings={"missing","reverse","wide","early","late","malformed"})
    void invalidRangesAreErrorsAndNeverEmptyCandidateClaims(String kind) throws Exception {
        var request=f.request().param("fromDate","2026-01-01");
        switch(kind) {
            case "reverse"->request.param("throughDate","2025-12-31");
            case "wide"->request.param("throughDate","2027-01-02");
            case "early"->{ request.param("throughDate","1999-01-01"); }
            case "late"->request.param("throughDate","9999-01-01");
            case "malformed"->request.param("throughDate","not-a-date");
            default->{ }
        }
        f.review.reviewer.send(request).andExpect(status().isBadRequest());
    }
    @Test void missingBodyAndFormalBatchAreNotTemporaryCandidateContexts() throws Exception {
        var t=new TemporarySubmissionFixture(new SubmissionFixture(f.review.temporary.f));t.abort();
        f.review.reviewer.send(get("/api/v1/temporary-reviews/"+t.id()+"/duplicate-candidates")).andExpect(status().isNotFound());
        var formal=new SubmissionFixture(f.review.temporary.f);formal.submit();
        f.review.reviewer.send(get("/api/v1/temporary-reviews/"+formal.id()+"/duplicate-candidates")).andExpect(status().isNotFound());
    }
    @Test void completedPcResultRemainsEvidenceAndCannotBeAssociated() throws Exception {
        var pc=new PcFixture(f.orders.api(),false,1,1,false);pc.start();
        pc.add(0);pc.add(1);pc.add(2);pc.submit("完整计划");
        var row=f.candidate(f.page(),pc.order);
        assertThat(row.path("blockers").toString()).contains("PC_WORK_EXISTS","RESULT_EXISTS","TARGET_NOT_READY");
        assertThat(row.path("linkAvailable").asBoolean()).isFalse();
    }
}
