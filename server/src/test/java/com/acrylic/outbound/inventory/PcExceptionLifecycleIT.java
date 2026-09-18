package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.*;
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
class PcExceptionLifecycleIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    private PublicationDriver api;private PcExceptionFixture f;
    @BeforeEach void setup() throws Exception { api=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));f=new PcExceptionFixture(api); }
    @Test void stoppedWorkFencesLateWritesAndAbortsReviewWithoutChangingFrozenBody() throws Exception {
        f.pc.submit("短装待核对");var old=f.pc.view().get("submission");String lateHash=old.path("payloadSha256").asText();
        var late=f.pc.write(f.pc.barcodes.get(1));f.stop();
        api.send(f.pc.request("entries",late,UUID.randomUUID().toString())).andExpect(status().isConflict());
        api.send(f.pc.request("review",f.pc.reviewBody("APPROVE"),UUID.randomUUID().toString())).andExpect(status().isConflict());
        assertThat(f.pc.view().at("/submission/status").asText()).isEqualTo("ABORTED");
        assertThat(f.pc.view().at("/submission/payloadSha256").asText()).isEqualTo(lateHash);
        assertThat(f.held(jdbc,"PC_RESERVED")).isEqualTo(3);
    }
    @Test void handoverChangesOnlyFutureOperatorAndRetainedPackagesCannotBeOrdinarilyVoided() throws Exception {
        var target=f.user(jdbc,passwords,"WAREHOUSE_ADMIN");var late=f.pc.write(f.pc.barcodes.get(1));f.stop();
        f.propose(f.plan(target.identity().user().getUsername()));f.approve();
        assertThat(f.held(jdbc,"PC_RESERVED")).isEqualTo(3);var apply=f.application();
        api.send(f.pc.request("exception/apply",apply,UUID.randomUUID().toString())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PC_HANDOVER_WRONG_OPERATOR"));
        target.data(f.pc.request("exception/apply",apply,UUID.randomUUID().toString()));
        assertThat(f.pc.view().at("/work/operator").asText()).isEqualTo(target.identity().user().id().toString());
        api.send(f.pc.request("entries",late,UUID.randomUUID().toString())).andExpect(status().isForbidden());
        target.data(f.pc.request("writer",f.pc.startBody(f.pc.view().at("/work/version").asText()),UUID.randomUUID().toString()));
        String entry=f.view().at("/stopped/snapshot/entries/0/id").asText();
        target.send(f.pc.request("void",f.pc.write("").put("entryId",entry).put("reason","不能绕过交接"),UUID.randomUUID().toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PC_RETAINED_ENTRY"));
        target.data(f.pc.request("entries",f.pc.write(f.pc.barcodes.get(1)),UUID.randomUUID().toString()));
        var rows=api.data(get(f.pc.path("entries"))).get("items");
        assertThat(rows.get(0).path("sequence").asText()).isEqualTo("4");assertThat(rows.get(0).path("weightGrams").asText()).isEqualTo("300501");
        assertThat(f.pc.view().at("/work/round").asInt()).isEqualTo(2);
    }
    @Test void releaseAndQuarantineApplyTogetherWithIndependentEvidenceAndCumulativeHistory() throws Exception {
        f.stop();f.propose(f.plan(api.identity().user().getUsername(),"RETAIN","RELEASE","QUARANTINE"));f.approve();
        assertThat(f.held(jdbc,"PC_RESERVED")).isEqualTo(3);f.apply();
        assertThat(f.held(jdbc,"PC_RESERVED")).isEqualTo(1);assertThat(f.held(jdbc,"QUARANTINED")).isEqualTo(1);
        var rows=api.data(get(f.pc.path("entries"))).get("items");
        assertThat(rows.findValuesAsText("status")).containsExactly("QUARANTINED","RELEASED","ACTIVE");
        byte[] work=Ids.bytes(UUID.fromString(f.pc.view().at("/work/id").asText()));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pc_entry WHERE work_id=? AND status='ACTIVE'",Integer.class,work)).isEqualTo(3);
        f.pc.send("writer",f.pc.startBody(f.pc.view().at("/work/version").asText()));f.pc.add(4);
        api.send(f.pc.request("entries",f.pc.write(f.pc.barcodes.get(8)),UUID.randomUUID().toString())).andExpect(status().isConflict());
        f.stop();assertThat(f.view().at("/stopped/snapshot/dispositions").size()).isEqualTo(2);
        f.propose(f.plan(api.identity().user().getUsername()));f.approve();f.apply();
        f.pc.send("writer",f.pc.startBody(f.pc.view().at("/work/version").asText()));f.pc.submit("处置后短装");
        String payload=jdbc.queryForObject("SELECT payload_text FROM pc_submission WHERE work_id=? ORDER BY work_round DESC LIMIT 1",String.class,work);
        assertThat(json.readTree(payload).get("dispositions").size()).isEqualTo(2);
        f.pc.send("review",f.pc.reviewBody("APPROVE"));assertThat(f.pc.view().at("/submission/actualCount").asText()).isEqualTo("2");
    }
    @Test void completeUnloadingEndsOrderWithoutDispatchOrLedgerAndReleasedBalesCanBeReused() throws Exception {
        f.stop();f.propose(f.plan(api.identity().user().getUsername(),"RELEASE","RELEASE","QUARANTINE").put("outcome","CANCEL"));f.approve();f.apply();
        assertThat(f.pc.view().at("/work/status").asText()).isEqualTo("CANCELLED");assertThat(f.held(jdbc,"PC_RESERVED")).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_record WHERE order_id=?",Integer.class,Ids.bytes(UUID.fromString(f.pc.order)))).isZero();
        var other=new PcFixture(f.pc);other.start();other.add(0);
        api.send(f.pc.request("exception/stop",f.stopBody(),UUID.randomUUID().toString())).andExpect(status().isConflict());
    }
    @Test void withdrawnApprovalCannotBeAppliedAndNewPlanKeepsAllDecisions() throws Exception {
        f.stop();f.propose(f.plan(api.identity().user().getUsername()));f.approve();var old=f.application();
        f.pc.send("exception/review",f.decision("REJECT"));
        api.send(f.pc.request("exception/apply",old,UUID.randomUUID().toString())).andExpect(status().isConflict());
        f.propose(f.plan(api.identity().user().getUsername(),"RELEASE"));f.approve();f.apply();
        assertThat(f.view().get("plans").size()).isEqualTo(2);assertThat(f.view().get("reviews").size()).isEqualTo(3);
    }
}
