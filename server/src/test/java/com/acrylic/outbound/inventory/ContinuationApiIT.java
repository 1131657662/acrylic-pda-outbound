package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
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
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class ContinuationApiIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    @Autowired com.acrylic.outbound.pda.OfflineSigner signer;
    private ContinuationFixture fixture() throws Exception {
        var web=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));
        return new ContinuationFixture(new SubmissionFixture(web,jdbc,true,false));
    }
    @Test void rejectedRoundContinuesWithOriginalFactsAndNewAuthorization() throws Exception {
        var c=fixture();c.request();c.approve();assertThat(c.r.f.count("bale_commitment")).isEqualTo(1);c.activate();
        var body=c.second();var response=c.r.f.pda.data(c.r.f.request(body),c.r.f.tokens);
        assertThat(response.get("status").asText()).isEqualTo("SUCCEEDED");assertThat(c.r.f.count("dispatch_record")).isEqualTo(2);
        assertThat(c.r.f.pda.data(c.r.f.request(body),c.r.f.tokens)).isEqualTo(response);
        assertThat(jdbc.queryForObject("SELECT status FROM submission WHERE id=?",String.class,Ids.bytes(UUID.fromString(c.r.f.id())))).isEqualTo("REJECTED");
        var contract=json.createObjectNode();contract.set("recovery",c.r.body);contract.set("activation",c.activation);contract.set("signingKey",json.valueToTree(signer.publicKey()));
        java.nio.file.Files.writeString(java.nio.file.Path.of("build/pda-continuation-fixture.json"),json.writeValueAsString(contract));
    }
    @Test void requestAndApprovalReplayKeepOneRoundAndFixedExpiry() throws Exception {
        var c=fixture();var body=c.requestBody();String key=UUID.randomUUID().toString();c.ticket=c.r.f.web.data(c.request(body,key));
        assertThat(c.r.f.web.data(c.request(body,key))).isEqualTo(c.ticket);var approval=c.approveBody();String approveKey=UUID.randomUUID().toString();
        var receipt=c.r.f.web.data(c.approve(approval,approveKey));assertThat(c.r.f.web.data(c.approve(approval,approveKey))).isEqualTo(receipt);
        c.activate();assertThat(c.r.f.pda.data(c.activate(c.activateBody()),c.r.f.tokens)).isEqualTo(c.activation);
        c.r.f.web.send(c.approve(approval.deepCopy().put("physicalEvidence","changed"),approveKey)).andExpect(status().isConflict());
    }
    @Test void missingRetainedRowRollsBackCurrentAndStockTransfer() throws Exception {
        var c=fixture();c.request();c.approve();c.activate();var body=c.second();body.withArray("scans").remove(0);
        c.r.f.send(body).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RETAINED_SET_MISMATCH"));assertContinuing(c);
    }
    @Test void rewritingOriginalAuthorizationOrTimeIsRejected() throws Exception {
        var c=fixture();c.request();c.approve();c.activate();var body=c.second();
        ((ObjectNode)body.withArray("captures").get(0)).put("assignmentId",c.activation.at("/assignment/id").asText());
        c.r.f.send(body).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RETAINED_SET_MISMATCH"));assertContinuing(c);
    }
    @Test void approvalRequiresEvidenceAndCurrentVersion() throws Exception {
        var c=fixture();c.request();var command=c.approveBody();
        c.r.f.web.send(c.approve(command.deepCopy().put("physicalEvidence"," "),UUID.randomUUID().toString())).andExpect(status().isBadRequest());
        c.r.f.web.send(c.approve(command.deepCopy().put("expectedVersion","0"),UUID.randomUUID().toString())).andExpect(status().isConflict());
        assertThat(c.r.read().at("/continuation/status").asText()).isEqualTo("REQUESTED");
    }
    @Test void requestCannotOmitBalesOrChangeDevice() throws Exception {
        var c=fixture();var body=c.requestBody();body.putArray("dispositions");
        c.r.f.web.send(c.request(body,UUID.randomUUID().toString())).andExpect(status().isConflict());
        c.r.f.web.send(c.request(c.requestBody().put("requestedDeviceId",UUID.randomUUID().toString()),UUID.randomUUID().toString())).andExpect(status().isBadRequest());
    }
    @Test void cycleConflictAfterApprovalDoesNotReleaseOrAcceptSecondRound() throws Exception {
        var c=fixture();c.request();c.approve();c.activate();
        jdbc.update("UPDATE bale SET dispatch_cycle=dispatch_cycle+1 WHERE id=?",Ids.bytes(UUID.fromString(c.r.body.at("/scans/0/baleId").asText())));
        c.r.f.send(c.second()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DISPATCH_CYCLE_CONFLICT"));assertContinuing(c);
    }
    @Test void lateOldApprovalAndUploadCannotSelectOldCurrent() throws Exception {
        var c=fixture();c.request();c.approve();
        var old=SubmissionJson.object().put("decision","APPROVE").put("reason","迟到审批").put("payloadSha256",SubmissionJson.sha(c.r.f.body));
        c.r.f.web.send(post("/api/v1/submissions/"+c.r.f.id()+"/review").contentType("application/json").content(old.toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SUBMISSION_SUPERSEDED"));
        c.r.f.send(c.r.f.body).andExpect(status().isConflict());assertContinuing(c);
    }
    @Test void otherDeviceCannotActivateAndExpiredCredentialKeepsReservation() throws Exception {
        var c=fixture();c.request();var approval=c.approve();var other=c.r.f.pda.login(c.r.f.pda.register());
        c.r.f.pda.send(c.activate(c.activateBody()),other).andExpect(status().isNotFound());
        jdbc.update("UPDATE offline_authorization SET expires_at=TIMESTAMPADD(SECOND,-1,UTC_TIMESTAMP(3)) WHERE assignment_id=?",Ids.bytes(UUID.fromString(approval.get("assignmentId").asText())));
        c.r.f.pda.send(c.activate(c.activateBody()),c.r.f.tokens).andExpect(status().isConflict());assertContinuing(c);
    }
    @Test void chunkControlDoesNotSelectCurrentUntilCompleteEvidenceTransfersReservations() throws Exception {
        var c=fixture();c.request();c.approve();c.activate();var body=c.second();
        byte[] bytes=SubmissionJson.canonical(body).getBytes(java.nio.charset.StandardCharsets.UTF_8);String id=body.get("submissionId").asText();
        var control=body.deepCopy().put("payloadSha256",SubmissionJson.sha(body)).put("byteCount",Integer.toString(bytes.length)).put("chunkCount","1");
        c.r.f.pda.data(post("/api/v1/pda/submissions/"+id+"/upload").header("X-Server-Recovery-Epoch","1").contentType("application/json").content(control.toString()),c.r.f.tokens);
        assertContinuing(c);
        c.r.f.pda.data(post("/api/v1/pda/submissions/"+id+"/chunks/0").header("X-Server-Recovery-Epoch","1").contentType("application/octet-stream").content(bytes),c.r.f.tokens);
        var receipt=c.r.f.pda.data(post("/api/v1/pda/submissions/"+id+"/finalize").header("X-Server-Recovery-Epoch","1"),c.r.f.tokens);
        assertThat(receipt.get("status").asText()).isEqualTo("SUCCEEDED");assertThat(c.r.f.count("dispatch_record")).isEqualTo(2);
    }
    @Test void otherWarehouseCannotApproveOrChangeReservations() throws Exception {
        var c=fixture();c.request();var identity=IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN");
        jdbc.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code='SUPERVISOR'",Ids.bytes(identity.user().id()));
        var outsider=new PublicationDriver(mvc,json,identity);
        outsider.send(c.approve(c.approveBody(),UUID.randomUUID().toString())).andExpect(status().isBadRequest());
        assertThat(c.r.read().at("/continuation/status").asText()).isEqualTo("REQUESTED");
    }
    @Test void resumePermissionIsRequiredEvenForRecallSupervisor() throws Exception {
        var c=fixture();c.request();var role=Ids.next();UUID user=c.r.f.web.identity().user().id();
        jdbc.update("INSERT INTO role VALUES(?,?,?)",Ids.bytes(role),"CONT_"+role.toString().substring(20),"仅召回测试角色");
        jdbc.update("INSERT INTO role_permission SELECT ?,p.id FROM permission p WHERE p.code IN ('order:read','order:recall')",Ids.bytes(role));
        jdbc.update("DELETE FROM user_role WHERE user_id=?",Ids.bytes(user));jdbc.update("INSERT INTO user_role VALUES(?,?)",Ids.bytes(user),Ids.bytes(role));
        c.r.f.web.send(c.approve(c.approveBody(),UUID.randomUUID().toString())).andExpect(status().isForbidden());
    }
    private void assertContinuing(ContinuationFixture c) {
        assertThat(c.r.f.count("dispatch_record")).isZero();
        assertThat(jdbc.queryForObject("SELECT state FROM bale_commitment WHERE order_id=?",String.class,Ids.bytes(UUID.fromString(c.r.f.order)))).isEqualTo("CONTINUATION_RESERVED");
        assertThat(jdbc.queryForObject("SELECT current_submission_id FROM submission_scope WHERE source_order_id=?",byte[].class,Ids.bytes(UUID.fromString(c.r.f.order)))).isNull();
    }
}
