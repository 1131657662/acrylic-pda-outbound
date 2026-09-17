package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class PdaAuthenticationIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    private PublicationDriver web;private PdaTestDriver pda;private PdaTestDriver.Device device;
    @BeforeEach void fixture() throws Exception {
        web=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));
        pda=new PdaTestDriver(web);device=pda.register();
    }
    @Test void privateKeyProofIsRequiredEvenWithCorrectPassword() throws Exception {
        var challenge=pda.challenge(device,"LOGIN",web.identity().user().username());var body=pda.loginBody(device);
        body.put("challengeId",challenge.get("challengeId").asText())
            .put("signature",PdaTestDriver.sign(pda.register(),challenge.get("signedText").asText()));
        mvc.perform(pda.command("/auth/login",body)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_DEVICE_PROOF"));
        body.put("signature",PdaTestDriver.sign(device,challenge.get("signedText").asText()));
        var tokens=pda.data(pda.command("/auth/login",body));
        assertThat(pda.data(get("/api/v1/pda/devices/me"),tokens).get("id").asText()).isEqualTo(device.id());
        mvc.perform(pda.command("/auth/login",body)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("DEVICE_CHALLENGE_EXPIRED"));
    }
    @Test void proofCannotBeTransferredToAnotherAccount() throws Exception {
        var body=pda.loginBody(device);var other=IdentityFixture.create(jdbc,passwords,"PDA_OPERATOR");
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)",Ids.bytes(other.user().id()),Ids.bytes(web.identity().warehouse()));
        body.put("username",other.user().username());
        mvc.perform(pda.command("/auth/login",body)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_DEVICE_PROOF"));
    }
    @Test void accountOutsideDeviceWarehouseCannotLogin() throws Exception {
        var outsider=new PdaTestDriver(new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN")));
        mvc.perform(outsider.command("/auth/login",outsider.loginBody(device))).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }
    @Test void expiredChallengeCannotCreateSession() throws Exception {
        var body=pda.loginBody(device);
        jdbc.update("UPDATE device_challenge SET expires_at=TIMESTAMPADD(SECOND,-1,UTC_TIMESTAMP(3)) WHERE id=?",
            Ids.bytes(UUID.fromString(body.get("challengeId").asText())));
        mvc.perform(pda.command("/auth/login",body)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("DEVICE_CHALLENGE_EXPIRED"));
    }
    @Test void refreshRequiresProofRotatesTokensAndPreservesAbsoluteExpiry() throws Exception {
        var tokens=pda.login(device);var body=pda.refreshBody(device,tokens.get("refreshToken").asText());
        mvc.perform(pda.command("/auth/refresh",body.deepCopy().put("signature","invalid"))).andExpect(status().isUnauthorized());
        jdbc.update("UPDATE pda_session SET access_expires_at=TIMESTAMPADD(SECOND,-1,UTC_TIMESTAMP(3)) WHERE id=?",
            Ids.bytes(UUID.fromString(tokens.get("sessionId").asText())));
        var fresh=pda.data(pda.command("/auth/refresh",body));
        assertThat(fresh.get("refreshExpiresAt")).isEqualTo(tokens.get("refreshExpiresAt"));
        assertThat(fresh.get("accessToken")).isNotEqualTo(tokens.get("accessToken"));
        pda.send(get("/api/v1/pda/devices/me"),tokens).andExpect(status().isUnauthorized());
        pda.send(get("/api/v1/pda/devices/me"),fresh).andExpect(status().isOk());
        mvc.perform(pda.command("/auth/refresh",pda.refreshBody(device,tokens.get("refreshToken").asText())))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_EXPIRED"));
    }
    @ParameterizedTest @ValueSource(strings={"authVersion","role","warehouse","disabled"})
    void existingSessionRechecksCurrentAuthorization(String change) throws Exception {
        var tokens=pda.login(device);byte[] user=Ids.bytes(web.identity().user().id());
        switch(change) {
            case "authVersion" -> jdbc.update("UPDATE app_user SET auth_version=auth_version+1 WHERE id=?",user);
            case "role" -> jdbc.update("DELETE FROM user_role WHERE user_id=?",user);
            case "warehouse" -> jdbc.update("DELETE FROM user_warehouse WHERE user_id=?",user);
            case "disabled" -> pda.setStatus(device,"1","DISABLED");
            default -> throw new IllegalArgumentException(change);
        }
        pda.send(get("/api/v1/pda/devices/me"),tokens).andExpect(status().isUnauthorized());
    }
    @Test void browserSessionDoesNotAuthenticatePdaAndPdaTokenDoesNotAuthenticateWeb() throws Exception {
        web.send(get("/api/v1/pda/devices/me")).andExpect(status().isUnauthorized());
        var tokens=pda.login(device);
        pda.send(get("/api/v1/devices").param("warehouseId",web.identity().warehouse().toString()),tokens).andExpect(status().isUnauthorized());
    }
    @Test void invalidDevicePublicKeyIsRejected() throws Exception {
        var body=json.createObjectNode().put("deviceCode","invalid-key").put("name","无效公钥").put("publicKey","AAAA");
        web.send(post("/api/v1/devices").header("Idempotency-Key",UUID.randomUUID().toString())
            .contentType("application/json").content(json.writeValueAsBytes(body))).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_DEVICE_KEY"));
    }
}
