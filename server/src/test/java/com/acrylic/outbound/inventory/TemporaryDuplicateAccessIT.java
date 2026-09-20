package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class TemporaryDuplicateAccessIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    private TemporaryDuplicateFixture f;
    @BeforeEach void setup() throws Exception { f=new TemporaryDuplicateFixture(jdbc,mvc,json,passwords); }
    @Test void temporaryOnlyCanReadScopedComparisonButFormalPermissionCannot() throws Exception {
        f.create(true);assertThat(f.page().path("items")).hasSize(1);
        f.review.reader("WAREHOUSE_ADMIN").send(f.request()).andExpect(status().isForbidden());
        f.review.reader("ORDER_VIEWER").send(f.request()).andExpect(status().isForbidden());
    }
    @Test void anotherWarehouseCannotLeakSourcesOrCandidates() throws Exception {
        var other=new TemporaryDuplicateFixture(jdbc,mvc,json,passwords);other.create(true);String own=f.create(true);
        var response=f.page();assertThat(response.path("items")).hasSize(1);assertThat(f.candidate(response,own)).isNotNull();
        other.review.reviewer.send(f.request()).andExpect(status().isNotFound());
        mvc.perform(get(f.path()).param("warehouseId",f.review.reviewer.identity().warehouse().toString())
            .with(user(other.review.reviewer.identity().user()))).andExpect(status().isNotFound());
        mvc.perform(get(f.path()).param("warehouseId",f.review.reviewer.identity().warehouse().toString())).andExpect(status().isUnauthorized());
    }
    @ParameterizedTest @ValueSource(strings={"session","role","warehouse"})
    void everyPageRechecksCurrentReviewerAuthorization(String change) throws Exception {
        f.create(true);f.create(true);var first=f.page("");var id=Ids.bytes(f.review.reviewer.identity().user().id());
        switch(change) {
            case "session"->jdbc.update("UPDATE app_user SET auth_version=auth_version+1 WHERE id=?",id);
            case "role"->jdbc.update("DELETE FROM user_role WHERE user_id=?",id);
            default->jdbc.update("DELETE FROM user_warehouse WHERE user_id=?",id);
        }
        f.review.reviewer.send(f.request().param("cursor",first.path("nextCursor").asText()))
            .andExpect(status().is(change.equals("session")?401:change.equals("role")?403:404));
    }
}
