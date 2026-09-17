package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class PairingAccessIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    private PublicationDriver web;private PairingTestDriver pair;
    @BeforeEach void fixture() throws Exception {
        web=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));pair=PairingTestDriver.fresh(web);
    }
    @Test void webApprovalRequiresCsrfPermissionWarehouseAndEpoch() throws Exception {
        pair.call("CREATE");var body=pair.approvalBody();
        mvc.perform(pair.approval(body,UUID.randomUUID().toString()).param("warehouseId",web.identity().warehouse().toString())
            .with(user(web.identity().user())).header("X-Server-Recovery-Epoch","1")).andExpect(status().isForbidden());
        mvc.perform(pair.approval(body,UUID.randomUUID().toString()).param("warehouseId",web.identity().warehouse().toString())
            .with(user(web.identity().user())).with(csrf()).header("X-Server-Recovery-Epoch","999")).andExpect(status().isConflict());
        var viewer=IdentityFixture.create(jdbc,passwords,"ORDER_VIEWER");
        new PublicationDriver(mvc,json,viewer).send(pair.approval(body,UUID.randomUUID().toString())).andExpect(status().isForbidden());
        pair.approve();var other=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));
        other.send(pair.approval(body,UUID.randomUUID().toString())).andExpect(status().isNotFound());
    }
    @Test void challengeExpiryAndRecoveryEpochFailClosed() throws Exception {
        var proof=pair.proof("CREATE");
        jdbc.update("UPDATE pairing_challenge SET expires_at=TIMESTAMPADD(SECOND,-1,UTC_TIMESTAMP(3)) WHERE id=?",Ids.bytes(UUID.fromString(proof.get("challengeId").asText())));
        mvc.perform(pair.command("CREATE",proof)).andExpect(status().isUnauthorized());
        pair.call("CREATE");jdbc.update("UPDATE device_pairing SET recovery_epoch=999 WHERE id=?",Ids.bytes(UUID.fromString(pair.id())));
        mvc.perform(pair.command("STATUS",pair.proof("STATUS"))).andExpect(status().isConflict());
    }
    @Test void unregisteredDeviceHasNoBusinessAccessAndCodeIsNotAuthentication() throws Exception {
        var application=pair.call("CREATE");mvc.perform(get("/api/v1/pda/orders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/devices").param("warehouseId",web.identity().warehouse().toString())).andExpect(status().isUnauthorized());
        pair.payload().put("credential",application.get("shortCode").asText());
        mvc.perform(pair.command("STATUS",pair.proof("STATUS"))).andExpect(status().isBadRequest());
    }
    @Test void lookupAndSignatureAttemptsAreRateLimitedWithoutSecretsInAudit() throws Exception {
        var application=pair.call("CREATE");String code=application.get("shortCode").asText();
        for(int i=0;i<30;i++) web.send(get("/api/v1/device-pairings").param("code",code)).andExpect(status().isOk());
        web.send(get("/api/v1/device-pairings").param("code",code)).andExpect(status().isTooManyRequests());
        var details=jdbc.queryForList("SELECT CAST(details AS CHAR) FROM operation_event WHERE entity_id=?",String.class,Ids.bytes(UUID.fromString(pair.id())));
        assertThat(details).allSatisfy(s->assertThat(s).doesNotContain(code,pair.payload().get("credential").asText()));
    }
    @Test void changedIdempotentApprovalCannotOverwriteFirstReceipt() throws Exception {
        pair.call("CREATE");String key=UUID.randomUUID().toString();var body=pair.approvalBody();var first=web.data(pair.approval(body,key));
        web.send(pair.approval(body.deepCopy().put("name","changed"),key)).andExpect(status().isConflict());
        assertThat(web.data(pair.approval(body,key))).isEqualTo(first);
    }
    @Test void repeatedInvalidSignaturesAreLimitedBeforeCreatingAnyIdentity() throws Exception {
        var proof=pair.proof("CREATE").put("signature","AAAA");
        for(int i=0;i<30;i++) mvc.perform(pair.command("CREATE",proof)).andExpect(status().isUnauthorized());
        mvc.perform(pair.command("CREATE",proof)).andExpect(status().isTooManyRequests());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM device_pairing WHERE id=?",Integer.class,Ids.bytes(UUID.fromString(pair.id())))).isZero();
    }
}
