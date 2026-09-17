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
class SubmissionApiIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    @Autowired com.acrylic.outbound.pda.OfflineSigner signer;
    private SubmissionFixture fixture(boolean deduct,boolean shortPlan) throws Exception {
        return new SubmissionFixture(new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN")),jdbc,deduct,shortPlan);
    }
    @Test void completeSubmissionRecordsOnceAndRetriesReturnOriginalReceipt() throws Exception {
        var f=fixture(true,false);var receipt=f.submit();assertThat(receipt.get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(f.submit()).isEqualTo(receipt);assertThat(f.abort()).isEqualTo(receipt);
        assertThat(f.count("dispatch_record")).isEqualTo(2);assertThat(f.count("inventory_ledger")).isEqualTo(2);
        assertThat(f.count("order_result_version")).isEqualTo(1);assertThat(receipt.get("actualWeightGrams").asText()).isEqualTo("601500");
        f.send(f.body.deepCopy().put("shortShipmentReason","changed")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONTENT_MISMATCH"));
        var contract=json.createObjectNode();contract.set("body",f.body);contract.set("receipt",receipt);contract.set("signingKey",json.valueToTree(signer.publicKey()));
        java.nio.file.Files.writeString(java.nio.file.Path.of("build/pda-submission-fixture.json"),json.writeValueAsString(contract));
    }
    @Test void disabledAutomaticAccountingStillRetainsGlobalDispatchGuards() throws Exception {
        var f=fixture(false,false);assertThat(f.submit().get("inventoryDeducted").asBoolean()).isFalse();
        assertThat(f.count("inventory_ledger")).isZero();assertThat(f.count("bale_commitment")).isEqualTo(2);assertThat(f.count("dispatch_record")).isEqualTo(2);
    }
    @Test void shortShipmentReservesUntilExplicitSupervisorApproval() throws Exception {
        var f=fixture(true,true);assertThat(f.submit().get("status").asText()).isEqualTo("REVIEW_REQUIRED");
        assertThat(f.count("dispatch_record")).isZero();assertThat(f.count("bale_commitment")).isEqualTo(2);
        var approved=f.review("APPROVE");assertThat(approved.get("status").asText()).isEqualTo("SUCCEEDED");assertThat(f.review("APPROVE")).isEqualTo(approved);
        assertThat(f.count("inventory_ledger")).isEqualTo(2);
    }
    @Test void abortBeforeBodyLeavesPermanentTombstoneAndNoFakePayload() throws Exception {
        var f=fixture(true,false);assertThat(f.abort().get("status").asText()).isEqualTo("ABORTED");
        f.send(f.body).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SUBMISSION_ABORTED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submission_payload WHERE submission_id=?",Integer.class,Ids.bytes(UUID.fromString(f.id())))).isZero();
        assertThat(f.count("dispatch_record")).isZero();assertThat(new OrderTestDriver(f.web).detail(f.order).at("/order/status").asText()).isEqualTo("REVIEW_REQUIRED");
    }
    @Test void staleCycleIsRetainedForReviewAndCannotBeApproved() throws Exception {
        var f=fixture(false,false);jdbc.update("UPDATE bale SET dispatch_cycle=2 WHERE id=?",Ids.bytes(UUID.fromString(f.body.at("/scans/0/baleId").asText())));
        assertThat(f.submit().get("status").asText()).isEqualTo("REVIEW_REQUIRED");assertThat(f.count("dispatch_record")).isZero();
        var command=json.createObjectNode().put("decision","APPROVE").put("reason","核对").put("payloadSha256",com.acrylic.outbound.submissions.SubmissionJson.sha(f.body));
        f.web.send(post("/api/v1/submissions/"+f.id()+"/review").contentType("application/json").content(command.toString())).andExpect(status().isConflict());
    }
    @Test void forgedIdentityOrUnresolvedCaptureCannotProduceDispatch() throws Exception {
        var f=fixture(false,false);var changed=f.body.deepCopy();((ObjectNode)changed.at("/captures/0")).put("originDeviceId",UUID.randomUUID().toString());
        f.send(changed).andExpect(status().isBadRequest());changed=f.body.deepCopy();((ObjectNode)changed.at("/captures/0")).put("state","CONFIRM_REQUESTED");
        f.send(changed).andExpect(status().isBadRequest());assertThat(f.count("dispatch_record")).isZero();
        var other=f.pda.login(f.pda.register());f.pda.send(f.request(f.body),other).andExpect(status().isNotFound());
    }
    @Test void outboxFailureRollsBackEntireDispatchAndSameBodyCanRecover() throws Exception {
        var f=fixture(true,false);jdbc.update("INSERT INTO order_sync_outbox VALUES(?,1,?,?,4,'TEST_FAULT','{}',NULL,UTC_TIMESTAMP(3))",Ids.bytes(Ids.next()),Ids.bytes(f.web.identity().warehouse()),Ids.bytes(UUID.fromString(f.order)));
        f.send(f.body).andExpect(status().isConflict());assertThat(f.count("dispatch_record")).isZero();assertThat(f.count("bale_commitment")).isZero();assertThat(f.count("inventory_ledger")).isZero();
        jdbc.update("DELETE FROM order_sync_outbox WHERE order_id=? AND event_type='TEST_FAULT'",Ids.bytes(UUID.fromString(f.order)));
        assertThat(f.submit().get("status").asText()).isEqualTo("SUCCEEDED");
    }
    @Test void expiredOfflineCredentialAllowsUploadingPreviouslySavedRecords() throws Exception {
        var f=fixture(false,false);var issued=java.time.Instant.now().minusSeconds(49*3600).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);var expires=issued.plusSeconds(48*3600);
        var claims=(ObjectNode)json.readTree(Base64.getUrlDecoder().decode(f.assignment.get("offlineToken").asText().split("\\.")[1]));
        claims.put("iat",issued.getEpochSecond()).put("exp",expires.getEpochSecond());byte[] assignment=Ids.bytes(UUID.fromString(f.assignment.get("id").asText()));
        jdbc.update("UPDATE order_assignment SET issued_at=?,expires_at=? WHERE id=?",java.sql.Timestamp.from(issued),java.sql.Timestamp.from(expires),assignment);
        jdbc.update("UPDATE offline_authorization SET issued_at=?,expires_at=?,signed_token=? WHERE assignment_id=?",java.sql.Timestamp.from(issued),java.sql.Timestamp.from(expires),signer.sign(claims).token(),assignment);
        String saved=issued.plusSeconds(1).toString();f.body.put("startedAt",saved);
        f.body.withArray("captures").forEach(c->((ObjectNode)c).put("confirmedAt",saved));f.body.withArray("scans").forEach(s->((ObjectNode)s).put("savedAt",saved));
        assertThat(f.submit().get("status").asText()).isEqualTo("SUCCEEDED");
    }
    @Test void missingShortReasonAndChangedWeightCannotCreatePartialEvidence() throws Exception {
        var f=fixture(false,true);f.send(f.body.deepCopy().putNull("shortShipmentReason")).andExpect(status().isBadRequest());
        var changed=f.body.deepCopy();((ObjectNode)changed.at("/scans/0")).put("weightGrams","1");f.send(changed).andExpect(status().isBadRequest());
        assertThat(f.count("dispatch_record")).isZero();assertThat(f.count("bale_commitment")).isZero();
    }
    @Test void rejectedShortShipmentRetainsReservationsAndCannotBeResubmitted() throws Exception {
        var f=fixture(false,true);f.submit();assertThat(f.review("REJECT").get("status").asText()).isEqualTo("REJECTED");
        f.send(f.body).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SUBMISSION_REJECTED"));
        assertThat(f.count("bale_commitment")).isEqualTo(2);assertThat(f.count("dispatch_record")).isZero();
    }
    @Test void frozenAuditMustIdentifyTheSameBatch() throws Exception {
        var f=fixture(false,false);var changed=f.body.deepCopy();var events=changed.withArray("events");
        ((ObjectNode)events.get(events.size()-1)).put("detail",UUID.randomUUID().toString());
        f.send(changed).andExpect(status().isBadRequest());assertThat(f.count("dispatch_record")).isZero();
    }
    @Test void reviewRequiresPermissionAndWarehouseMembership() throws Exception {
        var f=fixture(false,true);f.submit();
        var outsider=IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN");
        var request=json.createObjectNode().put("decision","APPROVE").put("reason","核对").put("payloadSha256",com.acrylic.outbound.submissions.SubmissionJson.sha(f.body));
        new PublicationDriver(mvc,json,outsider).send(post("/api/v1/submissions/"+f.id()+"/review").contentType("application/json").content(request.toString())).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("WAREHOUSE_NOT_FOUND"));
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)",Ids.bytes(outsider.user().id()),Ids.bytes(f.web.identity().warehouse()));
        jdbc.update("DELETE FROM user_role WHERE user_id=?",Ids.bytes(outsider.user().id()));
        jdbc.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code='PDA_OPERATOR'",Ids.bytes(outsider.user().id()));
        new PublicationDriver(mvc,json,outsider).send(post("/api/v1/submissions/"+f.id()+"/review").contentType("application/json").content(request.toString())).andExpect(status().isForbidden());
        assertThat(f.count("dispatch_record")).isZero();assertThat(f.count("bale_commitment")).isEqualTo(2);
    }
}
