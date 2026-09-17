package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
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
class RecallApiIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    @Autowired com.acrylic.outbound.pda.OfflineSigner signer;
    private RecallFixture fixture() throws Exception {
        var web=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));
        return new RecallFixture(new SubmissionFixture(web,jdbc,true,false));
    }
    @Test void emptyRecoveryStopsPdaAndSupervisorCancelsWithOriginalReceipt() throws Exception {
        var r=fixture();r.start(true);assertThat(r.f.web.data(r.request())).isEqualTo(r.recall);
        var received=r.upload();assertThat(r.upload()).isEqualTo(received);var stopped=r.stop();assertThat(r.stop()).isEqualTo(stopped);
        assertThat(stopped.get("stockReleased").asBoolean()).isFalse();var command=r.cancelBody();String key=UUID.randomUUID().toString();
        var cancelled=r.f.web.data(r.cancel(command,key));assertThat(r.f.web.data(r.cancel(command,key))).isEqualTo(cancelled);
        assertThat(r.read().get("status").asText()).isEqualTo("CANCELLED");assertThat(r.f.count("dispatch_record")).isZero();
        var contract=json.createObjectNode();contract.set("body",r.body);contract.set("receipt",stopped);contract.set("signingKey",json.valueToTree(signer.publicKey()));
        java.nio.file.Files.writeString(java.nio.file.Path.of("build/pda-recall-fixture.json"),json.writeValueAsString(contract));
    }
    @Test void loadedRecoveryRetainsBalesAndRejectsZeroCancellation() throws Exception {
        var r=fixture();r.start(false);r.upload();r.stop();
        r.f.web.send(r.cancel(r.cancelBody(),UUID.randomUUID().toString())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RECALL_HAS_LOADED_BALES"));
        assertThat(r.f.count("bale_commitment")).isEqualTo(2);assertThat(r.f.count("inventory_ledger")).isZero();
        assertThat(new OrderTestDriver(r.f.web).detail(r.f.order).at("/order/status").asText()).isEqualTo("RECALL_PENDING");
        r.f.send(r.f.body).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RECOVERY_ALREADY_FIXED"));
    }
    @Test void acknowledgementRequiresDurablePackageAndItsExactDigest() throws Exception {
        var r=fixture();r.start(true);r.f.pda.send(r.ack(r.ackBody()),r.f.tokens).andExpect(status().isBadRequest());r.upload();
        r.f.pda.send(r.ack(r.ackBody().put("auditThroughSeq","999")),r.f.tokens).andExpect(status().isBadRequest());
        assertThat(r.read().get("status").asText()).isEqualTo("REQUESTED");
    }
    @Test void recallKeyAndRecoveryPackageCannotBeReusedWithDifferentContent() throws Exception {
        var r=fixture();r.start(true);r.command.put("reason","changed");
        r.f.web.send(r.request()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONTENT_MISMATCH"));r.upload();
        r.f.pda.send(r.upload(r.body.deepCopy().put("closedAt","2025-01-01T00:00:00Z")),r.f.tokens).andExpect(status().isConflict());
    }
    @Test void auditGapAndForgedOriginAreRejected() throws Exception {
        var r=fixture();r.start(false);var missing=r.body.deepCopy();missing.withArray("auditEvents").remove(0);
        r.f.pda.send(r.upload(missing),r.f.tokens).andExpect(status().isBadRequest());
        var forged=r.body.deepCopy();((ObjectNode)forged.withArray("auditEvents").get(0)).put("operatorId",UUID.randomUUID().toString());
        r.f.pda.send(r.upload(forged),r.f.tokens).andExpect(status().isBadRequest());assertThat(r.read().get("recovery").isNull()).isTrue();
    }
    @Test void recallBeforeSubmissionPreventsDispatchAndApproval() throws Exception {
        var r=fixture();r.start(false);assertThat(r.f.submit().get("status").asText()).isEqualTo("REVIEW_REQUIRED");
        var command=json.createObjectNode().put("decision","APPROVE").put("reason","主管审批").put("payloadSha256",com.acrylic.outbound.submissions.SubmissionJson.sha(r.f.body));
        r.f.web.send(post("/api/v1/submissions/"+r.f.id()+"/review").contentType("application/json").content(command.toString())).andExpect(status().isConflict());
        assertThat(r.f.count("dispatch_record")).isZero();r.upload();r.stop();assertThat(r.f.count("bale_commitment")).isEqualTo(2);
    }
    @Test void completedSubmissionCannotBeRecalled() throws Exception {
        var r=fixture();r.f.submit();r.command.put("expectedVersion",new OrderTestDriver(r.f.web).detail(r.f.order).at("/order/rowVersion").asText());
        r.f.web.send(r.request()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_STATE_CONFLICT"));
    }
    @Test void recoveryCannotOmitPreviouslySubmittedFacts() throws Exception {
        var r=fixture();r.start(false);r.f.submit();r.body=r.recovery(true);
        r.f.pda.send(r.upload(r.body),r.f.tokens).andExpect(status().isBadRequest());assertThat(r.read().get("recovery").isNull()).isTrue();
    }
    @Test void expiredOfflineCredentialAllowsEvidenceButDoesNotReleaseOrder() throws Exception {
        var r=fixture();r.start(true);
        jdbc.update("UPDATE offline_authorization SET expires_at=TIMESTAMPADD(SECOND,-1,UTC_TIMESTAMP(3)) WHERE assignment_id=?",Ids.bytes(UUID.fromString(r.f.assignment.get("id").asText())));
        r.upload();r.stop();var other=r.f.pda.login(r.f.pda.register());
        r.f.pda.send(r.f.pda.claim(r.f.order,UUID.randomUUID().toString()),other).andExpect(status().isConflict());
    }
    @Test void otherDeviceAndWarehouseCannotReadOrAcknowledgeRecall() throws Exception {
        var r=fixture();r.start(true);var other=r.f.pda.login(r.f.pda.register());
        r.f.pda.send(get("/api/v1/pda/assignments/"+r.f.assignment.get("id").asText()+"/change-request"),other).andExpect(status().isNotFound());
        r.f.pda.send(r.upload(r.body),other).andExpect(status().isNotFound());
        var outsider=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));
        assertThat(outsider.data(get("/api/v1/orders/"+r.f.order+"/change-requests")).get("request").isNull()).isTrue();
    }
    @Test void cancellationRequiresPhysicalEvidenceAndCurrentVersion() throws Exception {
        var r=fixture();r.start(true);r.upload();r.stop();var command=r.cancelBody();
        r.f.web.send(r.cancel(command.deepCopy().put("physicalEvidence"," "),UUID.randomUUID().toString())).andExpect(status().isBadRequest());
        r.f.web.send(r.cancel(command.deepCopy().put("expectedVersion","0"),UUID.randomUUID().toString())).andExpect(status().isConflict());
        assertThat(r.read().get("status").asText()).isEqualTo("STOPPED");
    }
}
