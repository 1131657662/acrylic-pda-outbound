package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class DeviceTemporaryOrderIT {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc; @Autowired PasswordEncoder passwords;
    private PublicationDriver web; private PdaTestDriver pda;
    @BeforeEach void fixture() {
        web=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));pda=new PdaTestDriver(web);
    }
    @Test void registrationDefaultsClosedAndOldReceiptsRetainTheirAdmissionValue() throws Exception {
        var device=pda.register();
        assertThat(web.data(get("/api/v1/devices")).at("/items/0/temporaryOrderEnabled").asBoolean()).isFalse();
        String key=UUID.randomUUID().toString();var body=change("1",true);
        var original=web.data(command(device.id(),body,key));
        assertThat(original.get("temporaryOrderEnabled").asBoolean()).isTrue();
        web.data(command(device.id(),change("2",false),UUID.randomUUID().toString()));
        var fresh=pda.login(device);
        assertThat(web.data(command(device.id(),body,key))).isEqualTo(original);
        pda.send(get("/api/v1/pda/devices/me"),fresh).andExpect(status().isOk());
        assertThat(web.data(get("/api/v1/devices")).at("/items/0/temporaryOrderEnabled").asBoolean()).isFalse();
        web.send(command(device.id(),body.deepCopy().put("enabled",false),key)).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_event WHERE entity_id=? AND event_type='DEVICE_TEMPORARY_ORDER_CHANGED'",
            Integer.class,Ids.bytes(UUID.fromString(device.id())))).isEqualTo(2);
    }
    @Test void statusReceiptsPreserveEnabledAdmissionWithoutRegrantingSessions() throws Exception {
        var device=pda.register();web.data(command(device.id(),change("1",true),UUID.randomUUID().toString()));
        pda.setStatus(device,"2","DISABLED");
        assertThat(jdbc.queryForObject("SELECT temporary_order_enabled FROM device_command_receipt WHERE device_id=? AND row_version=3",
            Boolean.class,Ids.bytes(UUID.fromString(device.id())))).isTrue();
        web.send(command(device.id(),change("3",true),UUID.randomUUID().toString())).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DEVICE_DISABLED"));
        assertThat(web.data(command(device.id(),change("3",false),UUID.randomUUID().toString())).get("temporaryOrderEnabled").asBoolean()).isFalse();
    }
    @Test void staleVersionAndInvalidBodyDoNotRevokeSession() throws Exception {
        var device=pda.register();var tokens=pda.login(device);
        web.send(command(device.id(),change("2",true),UUID.randomUUID().toString())).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DEVICE_VERSION_CONFLICT"));
        web.send(command(device.id(),change("1",true).putNull("enabled"),UUID.randomUUID().toString())).andExpect(status().isBadRequest());
        web.send(command(device.id(),change("1",true).put("reason"," "),UUID.randomUUID().toString())).andExpect(status().isBadRequest());
        pda.send(get("/api/v1/pda/devices/me"),tokens).andExpect(status().isOk());
    }
    @ParameterizedTest @ValueSource(strings={"device:manage","temporary_order:manage"})
    void eitherMissingPermissionRejectsAdmissionChange(String permission) throws Exception {
        var device=pda.register();
        jdbc.update("DELETE rp FROM role_permission rp INNER JOIN role r ON r.id=rp.role_id INNER JOIN permission p ON p.id=rp.permission_id WHERE r.code='WAREHOUSE_ADMIN' AND p.code=?",permission);
        web.send(command(device.id(),change("1",true),UUID.randomUUID().toString())).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT temporary_order_enabled FROM device WHERE id=?",Boolean.class,Ids.bytes(UUID.fromString(device.id())))).isFalse();
    }
    @Test void commandRequiresWarehouseCsrfAndRecoveryEpoch() throws Exception {
        var device=pda.register();var body=change("1",true);String key=UUID.randomUUID().toString();
        var other=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));
        other.send(command(device.id(),body,key)).andExpect(status().isNotFound());
        mvc.perform(command(device.id(),body,key).param("warehouseId",web.identity().warehouse().toString())
            .with(user(web.identity().user())).header("X-Server-Recovery-Epoch","1")).andExpect(status().isForbidden());
        mvc.perform(command(device.id(),body,key).param("warehouseId",web.identity().warehouse().toString())
            .with(user(web.identity().user())).with(csrf()).header("X-Server-Recovery-Epoch","2"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SERVER_RECOVERY_EPOCH_MISMATCH"));
    }
    @Test void changingAdmissionRevokesSessionsButRetainsSubmittedFactsAndCommitments() throws Exception {
        var work=new SubmissionFixture(web,jdbc,true,true);work.submit();
        var before=facts(work);String device=work.tokens.get("deviceId").asText();
        web.data(command(device,change("1",true),UUID.randomUUID().toString()));
        work.pda.send(get("/api/v1/pda/devices/me"),work.tokens).andExpect(status().isUnauthorized());
        assertThat(facts(work)).isEqualTo(before);
        web.data(command(device,change("2",false),UUID.randomUUID().toString()));
        assertThat(facts(work)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE order_id=?",Integer.class,Ids.bytes(UUID.fromString(work.order)))).isEqualTo(2);
    }
    private Map<String,String> facts(SubmissionFixture work) throws Exception {
        var result=new TreeMap<String,String>();Object order=Ids.bytes(UUID.fromString(work.order));
        for(String table:List.of("outbound_order","order_assignment","bale_commitment"))
            result.put(table,json.writeValueAsString(jdbc.queryForList("SELECT * FROM "+table+" WHERE "+(table.equals("outbound_order")?"id":"order_id")+"=?",order)));
        for(String table:List.of("submitted_capture","submitted_event"))
            result.put(table,json.writeValueAsString(jdbc.queryForList("SELECT * FROM "+table+" WHERE submission_id=?",Ids.bytes(UUID.fromString(work.id())))));
        result.put("submission",json.writeValueAsString(jdbc.queryForList("SELECT * FROM submission WHERE id=?",Ids.bytes(UUID.fromString(work.id())))));
        result.put("authorization",json.writeValueAsString(jdbc.queryForList("SELECT * FROM offline_authorization WHERE assignment_id=?",Ids.bytes(UUID.fromString(work.assignment.get("id").asText())))));
        return result;
    }
    private ObjectNode change(String version,boolean enabled) {
        return json.createObjectNode().put("expectedVersion",version).put("enabled",enabled).put("reason","核对设备后调整临时作业准入");
    }
    private MockHttpServletRequestBuilder command(String device,JsonNode body,String key) throws Exception {
        return post("/api/v1/devices/"+device+"/temporary-order").header("Idempotency-Key",key).contentType("application/json").content(json.writeValueAsBytes(body));
    }
}
