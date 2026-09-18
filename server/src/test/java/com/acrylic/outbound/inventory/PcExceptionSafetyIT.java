package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
class PcExceptionSafetyIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    private PublicationDriver api;private PcExceptionFixture f;
    @BeforeEach void setup() throws Exception { api=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));f=new PcExceptionFixture(api); }
    @Test void omittedDuplicateWrongCycleAndForeignEntriesCannotBeProposed() throws Exception {
        f.stop();var body=f.plan(api.identity().user().getUsername());body.withArray("lines").remove(0);reject("exception/plan",body,"PC_DISPOSITION_SET_MISMATCH");
        body=f.plan(api.identity().user().getUsername());body.withArray("lines").set(1,body.withArray("lines").get(0).deepCopy());reject("exception/plan",body,"PC_DISPOSITION_SET_MISMATCH");
        body=f.plan(api.identity().user().getUsername());((ObjectNode)body.withArray("lines").get(0)).put("expectedCycle",99);reject("exception/plan",body,"PC_DISPOSITION_SET_MISMATCH");
        body=f.plan(api.identity().user().getUsername());((ObjectNode)body.withArray("lines").get(0)).put("entryId",Ids.next().toString());reject("exception/plan",body,"PC_DISPOSITION_SET_MISMATCH");
        body=f.plan(api.identity().user().getUsername());body.withArray("lines").addNull();
        api.send(f.pc.request("exception/plan",body,UUID.randomUUID().toString())).andExpect(status().isBadRequest());
        assertThat(f.view().get("plans").size()).isZero();assertThat(f.held(jdbc,"PC_RESERVED")).isEqualTo(3);
    }
    @Test void cancellationCannotRetainPackagesAndRemovalsNeedPhysicalEvidence() throws Exception {
        f.stop();reject("exception/plan",f.plan(api.identity().user().getUsername()).put("outcome","CANCEL"),"PC_CANCEL_HAS_RETAINED");
        var body=f.plan(api.identity().user().getUsername(),"RELEASE");((ObjectNode)body.withArray("lines").get(0)).put("physicalEvidence","   ");
        reject("exception/plan",body,"PC_PHYSICAL_CHECK_REQUIRED");
        reject("exception/plan",f.plan(api.identity().user().getUsername()).put("physicalConfirmed",false),"PC_PHYSICAL_CHECK_REQUIRED");
    }
    @Test void immutableStopAndPlanHashesAreCheckedAtEveryTransition() throws Exception {
        f.stop();reject("exception/plan",f.plan(api.identity().user().getUsername()).put("snapshotSha256","0".repeat(64)),"PC_STOP_HASH_MISMATCH");
        f.propose(f.plan(api.identity().user().getUsername()));reject("exception/review",f.decision("APPROVE").put("payloadSha256","0".repeat(64)),"PC_PLAN_HASH_MISMATCH");
        f.approve();var command=f.application();String id=f.view().at("/stopped/snapshot/entries/0/id").asText();
        jdbc.update("UPDATE pc_entry SET weight_g=weight_g+1 WHERE id=?",Ids.bytes(UUID.fromString(id)));
        try { reject("exception/apply",command,"PC_STOP_EVIDENCE_CHANGED");assertThat(f.held(jdbc,"PC_RESERVED")).isEqualTo(3); }
        finally { jdbc.update("UPDATE pc_entry SET weight_g=weight_g-1 WHERE id=?",Ids.bytes(UUID.fromString(id))); }
        f.apply();
    }
    @Test void lostResponsesReplayOriginalStopPlanReviewAndApplyWithNoSecondRoundOrEvidence() throws Exception {
        var stop=f.stopBody();replay("exception/stop",stop);var plan=f.plan(api.identity().user().getUsername(),"RELEASE","QUARANTINE");replay("exception/plan",plan);
        replay("exception/review",f.decision("APPROVE"));replay("exception/apply",f.application());
        assertThat(f.pc.view().at("/work/round").asInt()).isEqualTo(2);assertThat(f.view().get("history").size()).isEqualTo(1);
        assertThat(f.held(jdbc,"QUARANTINED")).isEqualTo(1);assertThat(f.view().get("reviews").size()).isEqualTo(1);
    }
    @Test void lastBaleConflictPreventsAllReleasesAndLeavesOriginalApprovalRecoverable() throws Exception {
        f.stop();f.propose(f.plan(api.identity().user().getUsername(),"RELEASE","RELEASE","RELEASE"));f.approve();
        var command=f.application();String key=UUID.randomUUID().toString();
        String bale=jdbc.queryForObject("SELECT BIN_TO_UUID(bale_id) FROM pc_entry WHERE work_id=UUID_TO_BIN(?) ORDER BY bale_id DESC LIMIT 1",String.class,f.pc.view().at("/work/id").asText());
        jdbc.update("UPDATE bale SET dispatch_cycle=dispatch_cycle+1 WHERE id=UUID_TO_BIN(?)",bale);
        try {
            api.send(f.pc.request("exception/apply",command,key)).andExpect(status().isConflict());
            assertThat(f.held(jdbc,"PC_RESERVED")).isEqualTo(3);assertThat(f.view().at("/plans/0/status").asText()).isEqualTo("APPROVED");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pc_disposition d INNER JOIN pc_entry e ON e.id=d.entry_id WHERE e.work_id=UUID_TO_BIN(?)",Integer.class,f.pc.view().at("/work/id").asText())).isZero();
        } finally { jdbc.update("UPDATE bale SET dispatch_cycle=dispatch_cycle-1 WHERE id=UUID_TO_BIN(?)",bale); }
        api.data(f.pc.request("exception/apply",command,key));assertThat(f.held(jdbc,"PC_RESERVED")).isZero();
        f.pc.send("writer",f.pc.startBody(f.pc.view().at("/work/version").asText()));
        reject("submit",f.pc.write("").put("reason","仍然零包"),"PC_EMPTY_SUBMISSION");
    }
    @Test void targetOrApproverPermissionRevocationKeepsAllOriginalReservations() throws Exception {
        var target=f.user(jdbc,passwords,"WAREHOUSE_ADMIN");f.stop();f.propose(f.plan(target.identity().user().getUsername()));f.approve();var command=f.application();
        jdbc.update("DELETE FROM user_role WHERE user_id=?",Ids.bytes(api.identity().user().id()));
        jdbc.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code='ORDER_VIEWER'",Ids.bytes(api.identity().user().id()));
        target.send(f.pc.request("exception/apply",command,UUID.randomUUID().toString())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PC_HANDOVER_PERMISSION_CHANGED"));
        assertThat(f.held(jdbc,"PC_RESERVED")).isEqualTo(3);
    }
    @Test void readonlyAndCrossWarehouseRequestsCannotMutateOrReadForeignCases() throws Exception {
        var viewer=f.user(jdbc,passwords,"ORDER_VIEWER");var stop=f.stopBody();viewer.send(f.pc.request("exception/stop",stop,UUID.randomUUID().toString())).andExpect(status().isForbidden());
        f.stop();viewer.data(get(f.pc.path("exception")));
        viewer.send(f.pc.request("exception/plan",f.plan(api.identity().user().getUsername()),UUID.randomUUID().toString())).andExpect(status().isForbidden());
        var outsider=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));
        outsider.send(get(f.pc.path("exception"))).andExpect(status().isNotFound());
        var other=new PcFixture(f.pc);other.start();api.send(get(other.path("exception")).param("caseId",f.view().at("/stopped/id").asText())).andExpect(status().isNotFound());
    }
    @Test void changedRetainedInventoryBlocksHandoverWhileUnloadingRemainsAValidRemedy() throws Exception {
        f.stop();f.propose(f.plan(api.identity().user().getUsername()));f.approve();var command=f.application();
        String bale=f.view().at("/stopped/snapshot/entries/0/baleId").asText();
        jdbc.update("UPDATE inventory_position SET blocked_reason='TEST_PHYSICAL_CONFLICT' WHERE bale_id=UUID_TO_BIN(?)",bale);
        try {
            reject("exception/apply",command,"PC_RETAINED_STOCK_CONFLICT");f.pc.send("exception/review",f.decision("REJECT"));
            f.propose(f.plan(api.identity().user().getUsername(),"QUARANTINE"));f.approve();f.apply();
            assertThat(f.held(jdbc,"QUARANTINED")).isEqualTo(1);
        } finally { jdbc.update("UPDATE inventory_position SET blocked_reason=NULL WHERE bale_id=UUID_TO_BIN(?)",bale); }
    }
    @Test void resumePermissionAloneCannotApproveReleasingAnOriginalPackage() throws Exception {
        var reviewer=f.user(jdbc,passwords,"ORDER_VIEWER");UUID role=Ids.next();
        jdbc.update("INSERT INTO role VALUES(?,?,?)",Ids.bytes(role),"PC-RESUME-"+role,"测试仅恢复权限");
        jdbc.update("INSERT INTO role_permission SELECT ?,id FROM permission WHERE code='order:resume'",Ids.bytes(role));
        jdbc.update("INSERT INTO user_role VALUES(?,?)",Ids.bytes(reviewer.identity().user().id()),Ids.bytes(role));
        f.stop();f.propose(f.plan(api.identity().user().username(),"RELEASE"));
        reviewer.send(f.pc.request("exception/review",f.decision("APPROVE"),UUID.randomUUID().toString())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PC_HANDOVER_PERMISSION_CHANGED"));
        assertThat(f.view().at("/plans/0/status").asText()).isEqualTo("PENDING");assertThat(f.held(jdbc,"PC_RESERVED")).isEqualTo(3);
    }
    @Test void approvalRechecksOriginalReservationsBeforeRecordingApproval() throws Exception {
        f.stop();f.propose(f.plan(api.identity().user().username()));String bale=f.view().at("/stopped/snapshot/entries/0/baleId").asText();
        jdbc.update("UPDATE bale SET dispatch_cycle=dispatch_cycle+1 WHERE id=UUID_TO_BIN(?)",bale);
        try { reject("exception/review",f.decision("APPROVE"),"PC_DISPOSITION_CYCLE_CHANGED");assertThat(f.view().get("reviews").size()).isZero(); }
        finally { jdbc.update("UPDATE bale SET dispatch_cycle=dispatch_cycle-1 WHERE id=UUID_TO_BIN(?)",bale); }
    }
    @Test void finalTransitionFailureRollsBackDispositionsOwnershipAndOriginalRequestCanRetry() throws Exception {
        var target=f.user(jdbc,passwords,"WAREHOUSE_ADMIN");f.stop();f.propose(f.plan(target.identity().user().username(),"RELEASE","QUARANTINE"));f.approve();
        var before=f.pc.view().get("work");var command=f.application();String key=UUID.randomUUID().toString();
        byte[] order=Ids.bytes(UUID.fromString(f.pc.order));UUID fault=Ids.next();
        long next=jdbc.queryForObject("SELECT row_version+1 FROM outbound_order WHERE id=?",Long.class,order);
        jdbc.update("INSERT INTO order_sync_outbox VALUES(?,1,?,?,?,'TEST_FAULT','{}',NULL,UTC_TIMESTAMP(3))",Ids.bytes(fault),Ids.bytes(api.identity().warehouse()),order,next);
        try {
            target.send(f.pc.request("exception/apply",command,key)).andExpect(status().isConflict());
            assertThat(f.pc.view().get("work")).isEqualTo(before);assertThat(f.held(jdbc,"PC_RESERVED")).isEqualTo(3);assertThat(f.held(jdbc,"QUARANTINED")).isZero();
            assertThat(f.view().at("/plans/0/status").asText()).isEqualTo("APPROVED");
            String plan=f.view().at("/plans/0/id").asText();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pc_disposition WHERE plan_id=UUID_TO_BIN(?)",Integer.class,plan)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pc_retained_entry WHERE plan_id=UUID_TO_BIN(?)",Integer.class,plan)).isZero();
        } finally { jdbc.update("DELETE FROM order_sync_outbox WHERE id=?",Ids.bytes(fault)); }
        target.data(f.pc.request("exception/apply",command,key));assertThat(f.pc.view().at("/work/round").asInt()).isEqualTo(2);
        assertThat(f.pc.view().at("/work/operator").asText()).isEqualTo(target.identity().user().id().toString());
        assertThat(f.held(jdbc,"PC_RESERVED")).isEqualTo(1);assertThat(f.held(jdbc,"QUARANTINED")).isEqualTo(1);
    }
    private void reject(String action,ObjectNode body,String code) throws Exception {
        api.send(f.pc.request(action,body,UUID.randomUUID().toString())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(code));
    }
    private void replay(String action,ObjectNode body) throws Exception {
        String key=UUID.randomUUID().toString();var first=api.data(f.pc.request(action,body,key));
        assertThat(api.data(f.pc.request(action,body,key))).isEqualTo(first);
        assertThat(api.data(get(f.pc.path("requests/"+key))).get("receipt")).isEqualTo(first);
        api.send(f.pc.request(action,body.deepCopy().put("physicalConfirmed",false),key)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONTENT_MISMATCH"));
    }
}
