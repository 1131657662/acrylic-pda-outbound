package com.acrylic.outbound.inventory;

import com.fasterxml.jackson.databind.*;
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
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class TemporaryAccessIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    private TemporaryFixture f;
    @BeforeEach void fixture() throws Exception {
        f=new TemporaryFixture(new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN")),jdbc);f.stock();
    }
    @Test void managerWithoutCreateCannotIssueEvenOnEnabledDevice() throws Exception {
        var request=f.request();jdbc.update("DELETE ur FROM user_role ur INNER JOIN role r ON r.id=ur.role_id WHERE ur.user_id=UUID_TO_BIN(?) AND r.code='TEMPORARY_ORDER_CREATOR'",
            f.tokens.get("userId").asText());
        f.pda.send(f.command("",request,UUID.randomUUID().toString()),f.tokens).andExpect(status().isForbidden());assertThat(f.authorizations()).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"pda:scan","pda:submit"})
    void partialWorkPermissionsCannotPrepare(String permission) throws Exception {
        var body=f.request();
        jdbc.update("DELETE rp FROM role_permission rp INNER JOIN role r ON r.id=rp.role_id INNER JOIN permission p ON p.id=rp.permission_id WHERE r.code='WAREHOUSE_ADMIN' AND p.code=?",permission);
        f.pda.send(f.command("",body,UUID.randomUUID().toString()),f.tokens).andExpect(status().isForbidden());assertThat(f.authorizations()).isZero();
    }
    @Test void admissionDisabledRevokesSessionAndBlocksDownloadAfterLogin() throws Exception {
        var receipt=f.prepare();f.admission("2",false);
        f.pda.send(f.download(receipt,"inventory"),f.tokens).andExpect(status().isUnauthorized());f.tokens=f.pda.login(f.device);
        f.pda.send(f.download(receipt,"inventory"),f.tokens).andExpect(status().isForbidden());
        assertThat(f.state(receipt).get("preparation")).isEqualTo(receipt);assertThat(f.authorizations()).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"user","device"})
    void freshLoginCannotReactivateOldVersion(String target) throws Exception {
        var receipt=f.prepare();
        if(target.equals("user")) jdbc.update("UPDATE app_user SET auth_version=auth_version+1 WHERE id=UUID_TO_BIN(?)",f.tokens.get("userId").asText());
        else jdbc.update("UPDATE device SET row_version=row_version+1 WHERE id=UUID_TO_BIN(?)",f.device.id());
        f.tokens=f.pda.login(f.device);
        f.pda.send(f.download(receipt,"inventory"),f.tokens).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TEMPORARY_REAUTHORIZATION_REQUIRED"));
        f.pda.send(f.acknowledge(receipt,f.ack(receipt),UUID.randomUUID().toString()),f.tokens).andExpect(status().isConflict());
        assertThat(f.state(receipt).get("preparation")).isEqualTo(receipt);
    }
    @Test void foreignDeviceWarehouseAndPersonCannotReadOriginalCredential() throws Exception {
        var receipt=f.prepare();var otherDevice=f.pda.login(f.pda.register());
        f.pda.send(get(TemporaryFixture.BASE+"/"+receipt.get("authorizationId").asText()),otherDevice).andExpect(status().isNotFound());
        var other=new TemporaryFixture(new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN")),jdbc);
        other.pda.send(f.download(receipt,"inventory"),other.tokens).andExpect(status().isNotFound());
        jdbc.update("INSERT INTO user_warehouse VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?))",other.tokens.get("userId").asText(),f.tokens.get("warehouseId").asText());
        var sameDeviceOtherPerson=other.pda.login(f.device);
        other.pda.send(f.download(receipt,"inventory"),sameDeviceOtherPerson).andExpect(status().isNotFound());
        other.pda.send(f.acknowledge(receipt,f.ack(receipt),UUID.randomUUID().toString()),sameDeviceOtherPerson).andExpect(status().isNotFound());
    }
    @Test void recoveryModeBlocksPreparationAndDownloadButRetainsHistoricalProof() throws Exception {
        var receipt=f.prepare();jdbc.update("UPDATE server_state SET service_mode='RECOVERY_PENDING' WHERE id=1");
        f.pda.send(f.download(receipt,"inventory"),f.tokens).andExpect(status().isServiceUnavailable());
        assertThat(f.state(receipt).get("preparation")).isEqualTo(receipt);
    }
    @Test void newRecoveryEpochCannotActivateOriginalQualification() throws Exception {
        var receipt=f.prepare();jdbc.update("UPDATE server_state SET recovery_epoch=2 WHERE id=1");
        f.pda.send(f.download(receipt,"inventory").with(r->{r.removeHeader("X-Server-Recovery-Epoch");r.addHeader("X-Server-Recovery-Epoch","2");return r;}),f.tokens)
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TEMPORARY_RECOVERY_REQUIRED"));
        assertThat(f.state(receipt).get("preparation")).isEqualTo(receipt);
    }
    @Test void missingInventoryOrPolicyCannotIssue() throws Exception {
        var other=new TemporaryFixture(new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN")),jdbc);
        other.pda.send(get(TemporaryFixture.BASE+"/baseline").header("X-Server-Recovery-Epoch","1"),other.tokens)
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVENTORY_REQUIRED"));
        new OrderTestDriver(other.web).stock();
        other.pda.send(get(TemporaryFixture.BASE+"/baseline").header("X-Server-Recovery-Epoch","1"),other.tokens)
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("POLICY_REQUIRED"));assertThat(other.authorizations()).isZero();
    }
}
