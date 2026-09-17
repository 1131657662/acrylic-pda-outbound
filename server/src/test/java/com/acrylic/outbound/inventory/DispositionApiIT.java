package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
class DispositionApiIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    @Autowired com.acrylic.outbound.pda.OfflineSigner signer;
    private DispositionFixture fixture(String action) throws Exception {
        var web=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));
        return new DispositionFixture(new ContinuationFixture(new SubmissionFixture(web,jdbc,true,false)),action);
    }
    @ParameterizedTest @ValueSource(strings={"RELEASE","QUARANTINE"})
    void approvedReductionTakesEffectOnlyWithCompleteReplacement(String action) throws Exception {
        var d=fixture(action);d.activate();var c=d.c;assertThat(c.r.f.count("bale_commitment")).isEqualTo(1);
        assertThat(state(d)).isEqualTo("CONTINUATION_RESERVED");var body=d.second();
        var pending=c.r.f.pda.data(c.r.f.request(body),c.r.f.tokens);assertThat(pending.get("status").asText()).isEqualTo("REVIEW_REQUIRED");
        assertThat(c.r.f.pda.data(c.r.f.request(body),c.r.f.tokens)).isEqualTo(pending);
        assertThat(state(d)).isEqualTo(action.equals("RELEASE")?null:"QUARANTINED");
        var success=d.review(body);assertThat(success.get("actualCount").asText()).isEqualTo("1");assertThat(success.get("actualWeightGrams").asText()).isEqualTo("301000");
        assertThat(c.r.f.count("dispatch_record")).isEqualTo(1);assertThat(c.r.f.count("inventory_ledger")).isEqualTo(1);
        assertThat(body.at("/scans/0")).isEqualTo(c.r.body.at("/scans/0"));
        if(action.equals("RELEASE")) {
            var output=SubmissionJson.object();output.set("recovery",c.r.body);output.set("activation",c.activation);output.set("signingKey",json.valueToTree(signer.publicKey()));
            output.set("submission",body);output.set("receipt",success);
            java.nio.file.Files.writeString(java.nio.file.Path.of("build/pda-disposition-fixture.json"),json.writeValueAsString(output));
        }
    }
    @Test void approvalReplayPreservesEvidenceAndDoesNotRelease() throws Exception {
        var d=fixture("RELEASE");var body=d.evidenceBody();String key=UUID.randomUUID().toString();
        var first=d.c.r.f.web.data(d.approveEvidence(body,key));assertThat(d.c.r.f.web.data(d.approveEvidence(body,key))).isEqualTo(first);
        ((ObjectNode)body.at("/dispositions/0")).put("physicalEvidence","不同正文");
        d.c.r.f.web.send(d.approveEvidence(body,key)).andExpect(status().isConflict());assertThat(state(d)).isEqualTo("REVIEW_RESERVED");
    }
    @Test void unapprovedOrForeignEvidenceCannotReduce() throws Exception {
        var d=fixture("RELEASE");d.approveEvidence();var body=d.requestBody();
        ((ObjectNode)body.at("/dispositions/0")).put("approvedEvidenceId",UUID.randomUUID().toString());
        d.c.r.f.web.send(d.c.request(body,UUID.randomUUID().toString())).andExpect(status().isConflict());
        var foreign=fixture("RELEASE");foreign.approveEvidence();
        ((ObjectNode)body.at("/dispositions/0")).put("approvedEvidenceId",foreign.evidence.at("/evidence/0/approvedEvidenceId").asText());
        d.c.r.f.web.send(d.c.request(body,UUID.randomUUID().toString())).andExpect(status().isConflict());
    }
    @Test void wrongActionOrCycleCannotReuseApprovedEvidence() throws Exception {
        var d=fixture("QUARANTINE");d.approveEvidence();var body=d.requestBody();
        ((ObjectNode)body.at("/dispositions/0")).put("action","RELEASE");
        d.c.r.f.web.send(d.c.request(body,UUID.randomUUID().toString())).andExpect(status().isConflict());
        body=d.evidenceBody();((ObjectNode)body.at("/dispositions/0")).put("expectedDispatchCycle","2");
        d.c.r.f.web.send(d.approveEvidence(body,UUID.randomUUID().toString())).andExpect(status().isBadRequest());
    }
    @ParameterizedTest @ValueSource(strings={"omit","alter","rewrite"})
    void alteredReplacementPreservesAllReservations(String mode) throws Exception {
        var d=fixture("RELEASE");d.activate();var body=d.second();
        if(mode.equals("omit"))body.remove("dispositions");
        if(mode.equals("alter"))((ObjectNode)body.at("/dispositions/0")).put("action","QUARANTINE");
        if(mode.equals("rewrite"))((ObjectNode)body.at("/scans/0")).put("status","VOID");
        d.c.r.f.send(body).andExpect(status().isConflict());assertThat(state(d)).isEqualTo("CONTINUATION_RESERVED");assertThat(d.c.r.f.count("dispatch_record")).isZero();
    }
    @Test void conflictInNewBaleRollsBackReleaseAndCurrentSelection() throws Exception {
        var d=fixture("RELEASE");d.activate();var body=d.second();
        jdbc.update("UPDATE inventory_position SET blocked_reason='TEST_HOLD' WHERE bale_id=?",Ids.bytes(UUID.fromString(body.at("/scans/1/baleId").asText())));
        d.c.r.f.send(body).andExpect(status().isConflict());assertThat(state(d)).isEqualTo("CONTINUATION_RESERVED");
        assertThat(jdbc.queryForObject("SELECT current_submission_id FROM submission_scope WHERE source_order_id=?",byte[].class,Ids.bytes(UUID.fromString(d.c.r.f.order)))).isNull();
        assertThat(jdbc.queryForObject("SELECT applied_submission_id FROM physical_disposition_evidence WHERE id=?",byte[].class,Ids.bytes(UUID.fromString(d.evidence.at("/evidence/0/approvedEvidenceId").asText())))).isNull();
    }
    @Test void quarantinePreventsAnotherOrderFromTakingTheBale() throws Exception {
        var d=fixture("QUARANTINE");d.activate();d.c.r.f.pda.data(d.c.r.f.request(d.second()),d.c.r.f.tokens);
        var other=new SubmissionFixture(d.c.r.f);var response=other.submit();assertThat(response.get("status").asText()).isEqualTo("REVIEW_REQUIRED");
        assertThat(response.get("issues").toString()).contains("BALE_ALREADY_COMMITTED");assertThat(state(d)).isEqualTo("QUARANTINED");
    }
    @Test void anotherWarehouseCannotApprovePhysicalEvidence() throws Exception {
        var d=fixture("RELEASE");var other=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));
        other.send(d.approveEvidence(d.evidenceBody(),UUID.randomUUID().toString())).andExpect(status().isNotFound());
    }
    private String state(DispositionFixture d) {
        var rows=jdbc.queryForList("SELECT state FROM bale_commitment WHERE bale_id=?",String.class,Ids.bytes(UUID.fromString(d.c.r.body.at("/scans/0/baleId").asText())));
        return rows.isEmpty()?null:rows.getFirst();
    }
}
