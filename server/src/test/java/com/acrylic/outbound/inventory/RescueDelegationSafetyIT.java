package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.devices.DeviceCrypto;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="outbound.rescue.delegation-enabled=true") @AutoConfigureMockMvc
class RescueDelegationSafetyIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    private RescueFixture fixture() throws Exception { return new RescueFixture(mvc,json,jdbc,passwords); }
    private PublicationDriver reviewer(RescueFixture f) {
        var user=IdentityFixture.create(jdbc,passwords,"SUPERVISOR");
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)",Ids.bytes(user.user().id()),Ids.bytes(f.operator.identity().warehouse()));
        return new PublicationDriver(mvc,json,new IdentityFixture(user.user(),f.operator.identity().warehouse()));
    }
    @Test void independentlyAuthorizedIssuerCannotApproveForARevokedRecipient() throws Exception {
        var f=fixture();String id=f.create().path("requestId").asText();var reviewer=reviewer(f);
        jdbc.update("DELETE FROM user_role WHERE user_id=?",Ids.bytes(f.supervisor.identity().user().id()));
        reviewer.send(f.approve(id,f.approval(),UUID.randomUUID().toString())).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("RESCUE_RECIPIENT_UNAUTHORIZED"));
        assertThat(f.rows("rescue_grant","request_id",id)).isZero();
    }
    @Test void changedRegisteredDeviceKeyCannotAuthorizeTheOldRequest() throws Exception {
        var f=fixture();String id=f.create().path("requestId").asText();
        var generator=java.security.KeyPairGenerator.getInstance("EC");
        generator.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
        String key=java.util.Base64.getEncoder().encodeToString(generator.generateKeyPair().getPublic().getEncoded());
        jdbc.update("UPDATE device SET public_key=?,key_sha256=? WHERE id=?",key,DeviceCrypto.sha(key),Ids.bytes(UUID.fromString(f.device.id())));
        f.supervisor.send(f.approve(id,f.approval(),UUID.randomUUID().toString())).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("RESCUE_DEVICE_KEY_CHANGED"));
        assertThat(f.rows("rescue_grant","request_id",id)).isZero();
    }
    @Test void approvalRequiresExplicitNullableExpiryPolicy() throws Exception {
        var f=fixture();String id=f.create().path("requestId").asText();var input=f.approval();input.remove("expiresAt");
        f.supervisor.send(f.approve(id,input,UUID.randomUUID().toString())).andExpect(status().isBadRequest());
        assertThat(f.rows("rescue_grant","request_id",id)).isZero();
    }
    @Test void originalFormalAssignmentMustBelongToTheSignedDeviceOperatorAndWarehouse() throws Exception {
        var f=fixture();String order=f.pda.readyOrder();var assignment=f.pda.data(f.pda.claim(order,UUID.randomUUID().toString()),f.tokens);
        f.document.withArray("orderScope").removeAll().addObject().put("source","FORMAL").put("localOrderId",UUID.randomUUID().toString());
        f.pda.send(f.request(f.signed(f.document),f.key),f.tokens).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("RESCUE_ORDER_NOT_FOUND"));
        ((com.fasterxml.jackson.databind.node.ObjectNode)f.document.withArray("orderScope").get(0)).put("localOrderId",assignment.path("id").asText());
        String id=f.create().path("requestId").asText();assertThat(f.approve(id).path("signedAuthorization").asText()).isNotBlank();
        assertThat(jdbc.queryForObject("SELECT status FROM order_assignment WHERE id=?",String.class,
            Ids.bytes(UUID.fromString(assignment.path("id").asText())))).isEqualTo("ACTIVE");
    }
    @Test void finalRequestWriteFailureLeavesNoScopeOrAuditThenOriginalBytesCanRetry() throws Exception {
        var f=fixture();var input=f.signed(f.document);String trigger="rescue_request_tail_"+UUID.randomUUID().toString().replace("-","");
        int before=jdbc.queryForObject("SELECT COUNT(*) FROM rescue_request WHERE operator_id=?",Integer.class,Ids.bytes(f.operator.identity().user().id()));
        jdbc.execute("CREATE TRIGGER "+trigger+" BEFORE INSERT ON api_idempotency_request FOR EACH ROW BEGIN "+
            "IF NEW.route_scope='rescue.request' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='rescue-request-last-write'; END IF; END");
        try { f.pda.send(f.request(input,f.key),f.tokens).andExpect(status().is5xxServerError()); }
        finally { jdbc.execute("DROP TRIGGER "+trigger); }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rescue_request WHERE operator_id=?",Integer.class,
            Ids.bytes(f.operator.identity().user().id()))).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_event WHERE actor_id=? AND event_type='rescue.request'",Integer.class,
            Ids.bytes(f.operator.identity().user().id()))).isZero();
        assertThat(f.pda.data(f.request(input,f.key),f.tokens).path("requestId").asText()).isNotBlank();
    }
}
