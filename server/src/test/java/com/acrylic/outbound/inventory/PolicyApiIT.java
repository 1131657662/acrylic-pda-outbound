package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class PolicyApiIT {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc; @Autowired PasswordEncoder passwords;
    private PublicationDriver api;
    @BeforeEach void fixture() { api=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN")); }
    @Test void noImplicitDeductionDefaultBeforeExplicitPolicy() throws Exception {
        assertThat(api.data(get("/api/v1/policies/current")).get("policy").isNull()).isTrue();
        assertThat(api.data(get("/api/v1/policies")).get("items")).isEmpty();
    }
    @Test void policyReplayAndHistoryKeepOriginalValue() throws Exception {
        String key=UUID.randomUUID().toString();var request=body("0",true);
        var original=api.data(postPolicy(request,key));
        assertThat(original.get("offlineHours").asInt()).isEqualTo(48);
        api.data(postPolicy(body("1",false),UUID.randomUUID().toString()));
        assertThat(api.data(postPolicy(request,key))).isEqualTo(original);
        assertThat(api.data(get("/api/v1/policies/current")).at("/policy/autoDeductInventory").asBoolean()).isFalse();
        var history=api.data(get("/api/v1/policies").param("limit","1"));
        assertThat(history.at("/items/0/version").asText()).isEqualTo("2");
        assertThat(api.data(get("/api/v1/policies").param("beforeVersion",history.get("nextCursor").asText()))
            .at("/items/0/autoDeductInventory").asBoolean()).isTrue();
        api.send(postPolicy(body("0",false),key)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONTENT_MISMATCH"));
    }
    @Test void stalePolicyAndMissingDecisionAreRejected() throws Exception {
        api.data(postPolicy(body("0",false),UUID.randomUUID().toString()));
        api.send(postPolicy(body("0",true),UUID.randomUUID().toString())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("POLICY_VERSION_CONFLICT"));
        var absent=body("1",true);absent.remove("autoDeductInventory");
        api.send(postPolicy(absent,UUID.randomUUID().toString())).andExpect(status().isBadRequest());
        assertThat(api.data(get("/api/v1/policies")).get("items").size()).isEqualTo(1);
    }
    @Test void clerkCanReadButCannotChangePolicy() throws Exception {
        jdbc.update("DELETE FROM user_role WHERE user_id=?",Ids.bytes(api.identity().user().id()));
        jdbc.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code='ORDER_CLERK'",Ids.bytes(api.identity().user().id()));
        api.data(get("/api/v1/policies/current"));
        api.send(postPolicy(body("0",true),UUID.randomUUID().toString())).andExpect(status().isForbidden());
    }
    private com.fasterxml.jackson.databind.node.ObjectNode body(String version,boolean deduct) {
        return json.createObjectNode().put("expectedVersion",version).put("autoDeductInventory",deduct).put("reason","明确本仓库扣账方式");
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postPolicy(Object body,String key) throws Exception {
        return post("/api/v1/policies").contentType("application/json").header("Idempotency-Key",key).content(json.writeValueAsBytes(body));
    }
}
