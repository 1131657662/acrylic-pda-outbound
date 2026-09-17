package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.junit.jupiter.api.*;
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
class DeviceManagementIT {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc; @Autowired PasswordEncoder passwords;
    private PublicationDriver web; private PdaTestDriver pda;
    @BeforeEach void fixture() {
        web=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));pda=new PdaTestDriver(web);
    }
    @Test void listPaginatesWithoutMixingWarehouses() throws Exception {
        var first=pda.register();var second=pda.register();var third=pda.register();
        new PdaTestDriver(new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"))).register();
        var page=web.data(get("/api/v1/devices").param("limit","2"));
        assertThat(page.get("items").findValuesAsText("id")).containsExactly(third.id(),second.id());
        assertThat(page.get("nextCursor").asText()).isEqualTo(second.id());
        var last=web.data(get("/api/v1/devices").param("limit","2").param("beforeId",page.get("nextCursor").asText()));
        assertThat(last.get("items").findValuesAsText("id")).containsExactly(first.id());assertThat(last.get("nextCursor").isNull()).isTrue();
        web.send(get("/api/v1/devices").param("beforeId","bad")).andExpect(status().isBadRequest());
    }
    @Test void registrationReplayHasOneIdentityAndRejectsChangedContent() throws Exception {
        var key=java.security.KeyPairGenerator.getInstance("EC");key.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
        var body=json.createObjectNode().put("deviceCode",UUID.randomUUID().toString()).put("name","东门PDA")
            .put("publicKey",Base64.getEncoder().encodeToString(key.generateKeyPair().getPublic().getEncoded()));
        String idempotency=UUID.randomUUID().toString();var original=web.data(command("",body,idempotency));
        assertThat(web.data(command("",body,idempotency))).isEqualTo(original);
        web.send(command("",body.deepCopy().put("name","不同设备"),idempotency)).andExpect(status().isConflict());
        web.send(command("",body,UUID.randomUUID().toString())).andExpect(status().isConflict());
        assertThat(web.data(get("/api/v1/devices")).get("items").size()).isEqualTo(1);
    }
    @Test void statusReplayReturnsOriginalReceiptWithoutReversingNewerStatus() throws Exception {
        var device=pda.register();String key=UUID.randomUUID().toString();var body=change("1","DISABLED");
        var original=web.data(command("/"+device.id()+"/status",body,key));
        pda.setStatus(device,"2","ACTIVE");
        assertThat(web.data(command("/"+device.id()+"/status",body,key))).isEqualTo(original);
        var row=web.data(get("/api/v1/devices")).get("items").get(0);
        assertThat(row.get("status").asText()).isEqualTo("ACTIVE");assertThat(row.get("rowVersion").asText()).isEqualTo("3");
        web.send(command("/"+device.id()+"/status",body.deepCopy().put("reason","另一原因"),key)).andExpect(status().isConflict());
    }
    @Test void reenablingRequiresFreshLoginAndRetainsOriginalOrderCredential() throws Exception {
        var device=pda.register();var tokens=pda.login(device);String order=pda.readyOrder();String key=UUID.randomUUID().toString();
        var assignment=pda.data(pda.claim(order,key),tokens);pda.setStatus(device,"1","DISABLED");pda.setStatus(device,"2","ACTIVE");
        pda.send(get("/api/v1/pda/devices/me"),tokens).andExpect(status().isUnauthorized());
        var other=pda.login(pda.register());pda.send(pda.claim(order,UUID.randomUUID().toString()),other).andExpect(status().isConflict());
        var restored=pda.data(pda.claim(order,key),pda.login(device));
        assertThat(restored.get("id")).isEqualTo(assignment.get("id"));assertThat(restored.get("offlineToken")).isEqualTo(assignment.get("offlineToken"));
        assertThat(restored.get("expiresAt")).isEqualTo(assignment.get("expiresAt"));
    }
    @Test void staleStatusVersionDoesNotRevokeCurrentSession() throws Exception {
        var device=pda.register();var tokens=pda.login(device);
        web.send(command("/"+device.id()+"/status",change("2","DISABLED"),UUID.randomUUID().toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEVICE_VERSION_CONFLICT"));
        pda.send(get("/api/v1/pda/devices/me"),tokens).andExpect(status().isOk());
    }
    @Test void deviceManagementRequiresPermissionWarehouseCsrfAndCurrentEpoch() throws Exception {
        var device=pda.register();var body=change("1","DISABLED");String path="/"+device.id()+"/status";
        var outsider=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));
        outsider.send(command(path,body,UUID.randomUUID().toString())).andExpect(status().isNotFound());
        var viewer=IdentityFixture.create(jdbc,passwords,"ORDER_VIEWER");
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)",Ids.bytes(viewer.user().id()),Ids.bytes(web.identity().warehouse()));
        var readonly=new PublicationDriver(mvc,json,new IdentityFixture(viewer.user(),web.identity().warehouse()));
        readonly.send(get("/api/v1/devices")).andExpect(status().isForbidden());
        readonly.send(command(path,body,UUID.randomUUID().toString())).andExpect(status().isForbidden());
        mvc.perform(command(path,body,UUID.randomUUID().toString()).param("warehouseId",web.identity().warehouse().toString())
            .with(user(web.identity().user())).header("X-Server-Recovery-Epoch","1")).andExpect(status().isForbidden());
        mvc.perform(command(path,body,UUID.randomUUID().toString()).param("warehouseId",web.identity().warehouse().toString())
            .with(user(web.identity().user())).with(csrf()).header("X-Server-Recovery-Epoch","2"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SERVER_RECOVERY_EPOCH_MISMATCH"));
        assertThat(web.data(get("/api/v1/devices")).at("/items/0/status").asText()).isEqualTo("ACTIVE");
    }
    private ObjectNode change(String version,String status) {
        return json.createObjectNode().put("expectedVersion",version).put("status",status).put("reason","核对原机和订单占用后操作");
    }
    private MockHttpServletRequestBuilder command(String path,JsonNode body,String key) throws Exception {
        return post("/api/v1/devices"+path).header("Idempotency-Key",key).contentType("application/json").content(json.writeValueAsBytes(body));
    }
}
