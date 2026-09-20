package com.acrylic.outbound.inventory;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
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
class PdaCapabilitiesIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    private TemporaryFixture f;
    @BeforeEach void fixture() throws Exception {
        f=new TemporaryFixture(new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN")),jdbc);f.stock();
    }
    @Test void temporaryOnlyLoginAndRefreshAdvertiseNoFormalOrdersButAllowPreparation() throws Exception {
        remove("pda:assign");remove("pda:recover");f.tokens=f.pda.login(f.device);
        var capabilities=f.tokens.get("capabilities");
        assertThat(capabilities.get("formalOrders").asBoolean()).isFalse();
        assertThat(capabilities.get("temporaryWork").asBoolean()).isTrue();
        assertThat(capabilities.get("barcodeSync").asBoolean()).isTrue();
        assertThat(capabilities.get("auditUpload").asBoolean()).isTrue();
        assertThat(capabilities.get("recoveryRead").asBoolean()).isFalse();
        f.pda.send(get("/api/v1/pda/orders"),f.tokens).andExpect(status().isForbidden());
        assertThat(f.prepare().get("authorizationId").asText()).isNotBlank();
        var fresh=f.pda.data(f.pda.command("/auth/refresh",f.pda.refreshBody(f.device,f.tokens.get("refreshToken").asText())));
        assertThat(fresh.get("capabilities")).isEqualTo(capabilities);
    }
    @Test void disablingAdmissionRevokesOldSessionAndNewLoginRemovesTemporaryCapabilities() throws Exception {
        remove("pda:assign");f.tokens=f.pda.login(f.device);f.admission("2",false);
        f.pda.send(get("/api/v1/pda/devices/me"),f.tokens).andExpect(status().isUnauthorized());
        var fresh=f.pda.login(f.device).get("capabilities");
        assertThat(fresh.get("temporaryWork").asBoolean()).isFalse();assertThat(fresh.get("barcodeSync").asBoolean()).isFalse();
        assertThat(fresh.get("auditUpload").asBoolean()).isTrue();
    }
    @Test void missingSubmitCannotAdvertiseTemporaryWorkOrAuditUpload() throws Exception {
        remove("pda:submit");var capabilities=f.pda.login(f.device).get("capabilities");
        assertThat(capabilities.get("formalOrders").asBoolean()).isTrue();assertThat(capabilities.get("barcodeSync").asBoolean()).isTrue();
        assertThat(capabilities.get("temporaryWork").asBoolean()).isFalse();assertThat(capabilities.get("auditUpload").asBoolean()).isFalse();
    }
    private void remove(String permission) {
        jdbc.update("DELETE FROM role_permission WHERE role_id=(SELECT id FROM role WHERE code='WAREHOUSE_ADMIN') AND permission_id=(SELECT id FROM permission WHERE code=?)",permission);
    }
}
