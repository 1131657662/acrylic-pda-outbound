package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="outbound.rescue.delegation-enabled=true") @AutoConfigureMockMvc
class RescueDelegationApiIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    private RescueFixture fixture() throws Exception { return new RescueFixture(mvc,json,jdbc,passwords); }

    @Test void signedAuthorizationAndRevocationPreserveOriginalReadOnlyMaterial() throws Exception {
        var f=fixture();String id=f.create().path("requestId").asText();var grant=f.approve(id);
        var material=f.own(id);
        assertThat(material.path("status").asText()).isEqualTo("AUTHORIZED");
        assertThat(material.path("authorization")).isEqualTo(grant);assertThat(material.path("localDeletionAllowed").asBoolean()).isFalse();
        assertThat(SubmissionJson.parse(material.path("originalText").asText().getBytes(java.nio.charset.StandardCharsets.UTF_8))).isEqualTo(f.document);
        var claims=SubmissionJson.parse(Base64.getUrlDecoder().decode(grant.path("signedAuthorization").asText().split("\\.")[1]));
        assertThat(claims.path("orderScope")).isEqualTo(f.document.path("orderScope"));
        assertThat(claims.at("/capabilities/readOriginalAllowed").asBoolean()).isTrue();
        for(String flag:List.of("mayResumeScanning","maySubmit","releaseAllowed","stockReleased","localDeletionAllowed"))
            assertThat(claims.at("/capabilities/"+flag).asBoolean()).isFalse();
        var revocation=f.supervisor.data(f.revoke(grant.path("grantId").asText(),SubmissionJson.object().put("reason","救援结束，撤销只读资格"),UUID.randomUUID().toString()));
        assertThat(f.own(id).path("status").asText()).isEqualTo("REVOKED");
        assertThat(f.own(id).path("revocation")).isEqualTo(revocation);assertThat(f.own(id).path("authorization")).isEqualTo(grant);
    }

    @Test void originalKeyReplaysExactGrantAndRejectsChangedReason() throws Exception {
        var f=fixture();var input=f.signed(f.document);var request=f.pda.data(f.request(input,f.key),f.tokens);
        assertThat(f.pda.data(f.request(input,f.key),f.tokens)).isEqualTo(request);
        f.pda.send(f.request(f.signed(f.document.deepCopy().put("reason","另一理由")),f.key),f.tokens)
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONTENT_MISMATCH"));
        String id=request.path("requestId").asText(),key=UUID.randomUUID().toString();var approval=f.approval();
        var grant=f.supervisor.data(f.approve(id,approval,key));
        assertThat(f.supervisor.data(f.approve(id,approval,key))).isEqualTo(grant);
        f.supervisor.send(f.approve(id,approval.deepCopy().put("reason","改理由"),key)).andExpect(status().isConflict());
        f.supervisor.send(f.approve(id,approval,UUID.randomUUID().toString())).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("RESCUE_ALREADY_AUTHORIZED"));
        assertThat(f.rows("rescue_grant","request_id",id)).isEqualTo(1);
    }

    @Test void wrongWarehouseAndOriginalOperatorCannotApprove() throws Exception {
        var f=fixture();String id=f.create().path("requestId").asText();
        jdbc.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code='SUPERVISOR'",Ids.bytes(f.operator.identity().user().id()));
        f.operator.send(f.approve(id,f.approval(),UUID.randomUUID().toString())).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("INDEPENDENT_RESCUE_APPROVAL_REQUIRED"));
        var outsider=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"SUPERVISOR"));
        outsider.send(f.approve(id,f.approval(),UUID.randomUUID().toString())).andExpect(status().isNotFound());
        assertThat(f.rows("rescue_grant","request_id",id)).isZero();
    }

    @Test void otherPdaCannotReadTheOriginalDeviceMaterial() throws Exception {
        var f=fixture();String id=f.create().path("requestId").asText();f.approve(id);
        var other=f.pda.login(f.pda.register());
        f.pda.send(get("/api/v1/pda/rescue-requests/"+id),other).andExpect(status().isNotFound());
    }

    @Test void rescuerPermissionRevokedBeforeApprovalCannotProduceGrant() throws Exception {
        var f=fixture();String id=f.create().path("requestId").asText();
        jdbc.update("DELETE FROM user_role WHERE user_id=?",Ids.bytes(f.supervisor.identity().user().id()));
        f.supervisor.send(f.approve(id,f.approval(),UUID.randomUUID().toString())).andExpect(status().isForbidden());
        assertThat(f.rows("rescue_grant","request_id",id)).isZero();
    }

    @Test void expiryAndBlankReasonCannotIssueAuthorization() throws Exception {
        var f=fixture();String id=f.create().path("requestId").asText();
        f.supervisor.send(f.approve(id,f.approval().put("expiresAt",Instant.now().minusSeconds(1).toString()),UUID.randomUUID().toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RESCUE_EXPIRY_INVALID"));
        f.supervisor.send(f.approve(id,f.approval().put("reason"," "),UUID.randomUUID().toString())).andExpect(status().isBadRequest());
        assertThat(f.rows("rescue_grant","request_id",id)).isZero();
    }

    @Test void finalAuditWriteFailureRollsBackGrantAndAllowsOriginalRetry() throws Exception {
        var f=fixture();String id=f.create().path("requestId").asText(),key=UUID.randomUUID().toString();
        String trigger="rescue_tail_"+UUID.randomUUID().toString().replace("-","");
        jdbc.execute("CREATE TRIGGER "+trigger+" BEFORE INSERT ON operation_event FOR EACH ROW BEGIN "+
            "IF NEW.event_type='rescue.approve' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='rescue-audit-tail'; END IF; END");
        try { f.supervisor.send(f.approve(id,f.approval(),key)).andExpect(status().is5xxServerError()); }
        finally { jdbc.execute("DROP TRIGGER "+trigger); }
        assertThat(f.rows("rescue_grant","request_id",id)).isZero();assertThat(f.own(id).path("status").asText()).isEqualTo("REQUESTED");
        assertThat(f.supervisor.data(f.approve(id,f.approval(),key)).path("signedAuthorization").asText()).isNotBlank();
    }

    @Test void databaseRejectsOverwritesDeletesAndScopeExpansionAfterGrant() throws Exception {
        var f=fixture();String id=f.create().path("requestId").asText();var grant=f.approve(id);String gid=grant.path("grantId").asText();
        f.supervisor.data(f.revoke(gid,SubmissionJson.object().put("reason","测试保留原撤权"),UUID.randomUUID().toString()));
        for(String table:List.of("rescue_request","rescue_request_scope","rescue_grant","rescue_revocation")) {
            String column=table.equals("rescue_request_scope")?"request_id":table.equals("rescue_revocation")?"grant_id":"id";
            var identity=Ids.bytes(UUID.fromString(table.startsWith("rescue_request")?id:gid));
            assertThatThrownBy(()->jdbc.update("UPDATE "+table+" SET "+column+"="+column+" WHERE "+column+"=?",identity)).hasMessageContaining("IMMUTABLE");
            assertThatThrownBy(()->jdbc.update("DELETE FROM "+table+" WHERE "+column+"=?",identity)).hasMessageContaining("IMMUTABLE");
        }
        assertThatThrownBy(()->jdbc.update("INSERT INTO rescue_request_scope VALUES(?,2,'TEMP',?)",Ids.bytes(UUID.fromString(id)),Ids.bytes(Ids.next())))
            .hasMessageContaining("RESCUE_SCOPE_ALREADY_AUTHORIZED");
        assertThat(f.own(id).path("authorization")).isEqualTo(grant);
    }
}
