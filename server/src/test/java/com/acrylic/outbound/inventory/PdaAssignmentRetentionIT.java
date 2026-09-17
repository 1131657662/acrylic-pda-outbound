package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.*;
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
class PdaAssignmentRetentionIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    private PublicationDriver web;private PdaTestDriver pda;private PdaTestDriver.Device device;private JsonNode tokens;private String order;
    @BeforeEach void fixture() throws Exception {
        web=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));pda=new PdaTestDriver(web);
        order=pda.readyOrder();device=pda.register();tokens=pda.login(device);
    }
    @ParameterizedTest @ValueSource(strings={"logout","sessionExpired","disabled","offlineExpired","downloadMissing"})
    void credentialOrDownloadFailureNeverMakesOrderAvailableToOtherDevice(String interruption) throws Exception {
        String key=UUID.randomUUID().toString();var assignment=pda.data(pda.claim(order,key),tokens);
        interrupt(interruption,assignment);var other=pda.login(pda.register());
        pda.send(pda.claim(order,UUID.randomUUID().toString()),other).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_ASSIGNED"));
        assertThat(pda.data(get("/api/v1/pda/orders"),other).get("items")).isEmpty();
        var summary=web.data(get("/api/v1/orders/"+order+"/assignment")).get("assignment");
        assertThat(summary.get("id")).isEqualTo(assignment.get("id"));assertThat(summary.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(summary.get("downloadAckAt").isNull()).isTrue();
        var orders=new OrderTestDriver(web);
        web.send(orders.command("/"+order+"/cancel",orders.cancellation("3"),UUID.randomUUID().toString())).andExpect(status().isConflict());
        if(!interruption.equals("disabled")) {
            var recovered=pda.data(pda.claim(order,key),pda.login(device));
            assertThat(recovered.get("offlineToken")).isEqualTo(assignment.get("offlineToken"));
            assertThat(recovered.get("id")).isEqualTo(assignment.get("id"));
        }
    }
    @Test void anotherOperatorOnSameDeviceCannotObtainPreviousOperatorsCredential() throws Exception {
        pda.data(pda.claim(order,UUID.randomUUID().toString()),tokens);
        var other=IdentityFixture.create(jdbc,passwords,"PDA_OPERATOR");
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)",Ids.bytes(other.user().id()),Ids.bytes(web.identity().warehouse()));
        var colleague=new PdaTestDriver(new PublicationDriver(mvc,json,other));var login=colleague.login(device);
        var rows=colleague.data(get("/api/v1/pda/assignments"),login).get("items");
        assertThat(rows.size()).isEqualTo(1);
        assertThat(rows.get(0).get("offlineToken").isNull()).isTrue();
        colleague.send(colleague.claim(order,UUID.randomUUID().toString()),login).andExpect(status().isConflict());
    }
    @Test void recoveryEpochMismatchBlocksNewAssignment() throws Exception {
        pda.send(pda.claim(order,UUID.randomUUID().toString()).with(req->{req.removeHeader("X-Server-Recovery-Epoch");
            req.addHeader("X-Server-Recovery-Epoch","2");return req;}),tokens).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SERVER_RECOVERY_EPOCH_MISMATCH"));
        assertThat(web.data(get("/api/v1/orders/"+order+"/assignment")).get("assignment").isNull()).isTrue();
    }
    private void interrupt(String reason,JsonNode assignment) throws Exception {
        switch(reason) {
            case "logout" -> pda.data(post("/api/v1/pda/auth/logout"),tokens);
            case "sessionExpired" -> jdbc.update("UPDATE pda_session SET expires_at=TIMESTAMPADD(SECOND,-1,UTC_TIMESTAMP(3)) WHERE id=?",
                Ids.bytes(UUID.fromString(tokens.get("sessionId").asText())));
            case "disabled" -> pda.setStatus(device,"1","DISABLED");
            case "offlineExpired" -> expire(assignment);
            case "downloadMissing" -> assertThat(assignment.get("downloadAckAt").isNull()).isTrue();
            default -> throw new IllegalArgumentException(reason);
        }
    }
    private void expire(JsonNode assignment) {
        byte[] id=Ids.bytes(UUID.fromString(assignment.get("id").asText()));
        jdbc.update("UPDATE order_assignment SET issued_at=TIMESTAMPADD(HOUR,-49,UTC_TIMESTAMP(3)),expires_at=TIMESTAMPADD(HOUR,-1,UTC_TIMESTAMP(3)) WHERE id=?",id);
        jdbc.update("UPDATE offline_authorization SET issued_at=TIMESTAMPADD(HOUR,-49,UTC_TIMESTAMP(3)),expires_at=TIMESTAMPADD(HOUR,-1,UTC_TIMESTAMP(3)) WHERE assignment_id=?",id);
    }
}
