package com.acrylic.outbound.inventory;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class TemporarySourceEvidenceAccessIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    @Value("${outbound.storage}") String storage;
    TemporarySourceEvidenceFixture f;
    @BeforeEach void setup() throws Exception {
        f=new TemporarySourceEvidenceFixture(new TemporarySourceEvidenceFixture.Inputs(jdbc,mvc,json,passwords,storage),false);
        f.temporary.submit();f.temporary.abort();
    }
    @ParameterizedTest @ValueSource(strings={"","/payload","/chunks/0"}) void permissionsAreRecheckedForEveryResource(String suffix) throws Exception {
        jdbc.update("DELETE FROM user_role WHERE user_id=UUID_TO_BIN(?)",f.reviewer.identity().user().id().toString());
        var before=f.facts();f.reviewer.send(f.request(suffix)).andExpect(status().isForbidden());assertThat(f.facts()).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={"","/payload","/chunks/0"}) void revokedSessionCannotReadAnyResource(String suffix) throws Exception {
        jdbc.update("UPDATE app_user SET auth_version=auth_version+1 WHERE id=UUID_TO_BIN(?)",f.reviewer.identity().user().id().toString());
        f.reviewer.send(f.request(suffix)).andExpect(status().isUnauthorized());
    }
    @ParameterizedTest @ValueSource(strings={"","/payload","/chunks/0"}) void crossWarehouseSourceIsNotDisclosed(String suffix) throws Exception {
        var other=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"TEMPORARY_ORDER_CONFIRMER"));
        other.send(f.request(suffix)).andExpect(status().isNotFound());
    }
    @ParameterizedTest @ValueSource(strings={"creatorDeviceId","clientOrderId","workRound"}) void wrongOriginalIdentityDoesNotFallbackToCanonical(String field) throws Exception {
        var request=f.request("");request.queryParam("unrelated","ignored");
        request.with(r->{r.setParameter(field,field.equals("workRound")?"2":UUID.randomUUID().toString());return r;});
        f.reviewer.send(request).andExpect(status().isNotFound());
    }
    @Test void invalidOrMissingOriginalIdentityIsRejected() throws Exception {
        f.reviewer.send(get(f.path())).andExpect(status().isBadRequest());
        f.reviewer.send(f.request("").with(r->{r.setParameter("workRound","9999999999999999999");return r;})).andExpect(status().isBadRequest());
        f.reviewer.send(f.request("/chunks/-1")).andExpect(status().isBadRequest());
        f.reviewer.send(f.request("/chunks/128")).andExpect(status().isBadRequest());
    }
    @Test void unknownSubmissionReturnsNoReleaseMeaning() throws Exception {
        f.reviewer.send(get("/api/v1/temporary-source-evidence/"+UUID.randomUUID()).param("creatorDeviceId",f.temporary.a.device())
            .param("clientOrderId",f.temporary.a.client).param("workRound","1")).andExpect(status().isNotFound());
    }
    @Test void currentRecoveryBarrierIsAssessmentOnlyAndOriginalProofNeverChanges() throws Exception {
        String original=f.storedReceipt();
        try {
            jdbc.update("UPDATE server_state SET recovery_epoch=2,service_mode='RECOVERY_PENDING' WHERE id=1");
            var before=f.facts();var view=f.read();
            assertThat(view.at("/control/sourceRecoveryEpoch").asText()).isEqualTo("1");
            assertThat(view.at("/currentAssessment/serverRecoveryEpoch").asText()).isEqualTo("2");
            assertThat(view.at("/currentAssessment/gaps").toString()).contains("SOURCE_EPOCH_DIFFERS","SERVER_RECOVERY_REQUIRED");
            assertThat(view.at("/terminalProof/originalStoredReceiptUtf8").asText()).isEqualTo(original);
            assertThat(view.at("/currentAssessment/writeAllowed").asBoolean()).isFalse();assertThat(f.facts()).isEqualTo(before);
        } finally { jdbc.update("UPDATE server_state SET recovery_epoch=1,service_mode='NORMAL' WHERE id=1"); }
    }
    @Test void warehouseGrantRemovalCannotKeepOldReadAccess() throws Exception {
        jdbc.update("DELETE FROM user_warehouse WHERE user_id=UUID_TO_BIN(?)",f.reviewer.identity().user().id().toString());
        f.reviewer.send(f.request("")).andExpect(status().isNotFound());
    }
}
