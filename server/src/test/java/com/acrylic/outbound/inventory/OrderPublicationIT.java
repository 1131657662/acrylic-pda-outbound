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
class OrderPublicationIT {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc; @Autowired PasswordEncoder passwords;
    private PublicationDriver api;private OrderTestDriver orders;
    @BeforeEach void fixture() throws Exception {
        api=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));
        orders=new OrderTestDriver(api);orders.stock();
    }
    @Test void publicationRequiresExplicitPolicyAndCurrentBaseline() throws Exception {
        String id=orders.create(orders.plan()).get("orderId").asText();
        api.send(orders.command("/"+id+"/publish",orders.release("1","1"),UUID.randomUUID().toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("POLICY_REQUIRED"));
        orders.policy("0",true);api.revise("003","1","新版型号",true);
        api.send(orders.command("/"+id+"/publish",orders.release("1","1"),UUID.randomUUID().toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_PUBLICATION_BLOCKED"));
        assertThat(orders.detail(id).at("/order/status").asText()).isEqualTo("DRAFT");
        assertThat(history(id)).isEmpty();assertThat(outboxCount(id)).isZero();
    }
    @Test void publish361BalesFixesPolicyAndQueuesExactlyOnce() throws Exception {
        orders.policy("0",true);String id=orders.create(orders.plan()).get("orderId").asText();
        String key=UUID.randomUUID().toString();var body=orders.release("1","1");
        var receipt=api.data(orders.command("/"+id+"/publish",body,key));
        orders.policy("1",false);
        assertThat(api.data(orders.command("/"+id+"/publish",body,key))).isEqualTo(receipt);
        assertThat(receipt.get("rowVersion").asText()).isEqualTo("2");
        assertThat(receipt.get("status").asText()).isEqualTo("READY");
        var publication=history(id).get(0);
        assertThat(publication.get("autoDeductInventory").asBoolean()).isTrue();
        assertThat(publication.get("documentStatus").asText()).isEqualTo("PENDING");
        assertThat(history(id).size()).isEqualTo(1);assertThat(outboxCount(id)).isEqualTo(1);
        String snapshot=jdbc.queryForObject("SELECT snapshot_json FROM order_publication WHERE order_id=?",String.class,Ids.bytes(UUID.fromString(id)));
        assertThat(json.readTree(snapshot).at("/order/plan/plannedTotal").asText()).isEqualTo("361");
        assertThat(orders.detail(id).at("/order/revision").asInt()).isEqualTo(1);
        api.send(orders.command("/"+id+"/publish",orders.release("2","2"),UUID.randomUUID().toString())).andExpect(status().isConflict());
    }
    @Test void readyRevisionRepublishesWithNewPolicyWithoutMutatingHistory() throws Exception {
        orders.policy("0",true);var plan=orders.plan();String id=orders.create(plan).get("orderId").asText();
        var originalPlan=orders.detail(id).get("plan");
        api.data(orders.command("/"+id+"/publish",orders.release("1","1"),UUID.randomUUID().toString()));
        orders.policy("1",false);
        var revised=orders.revision(plan.deepCopy().put("vehicleNo","新车辆"),"2").put("expectedPolicyVersion","2");
        String key=UUID.randomUUID().toString();var result=api.data(orders.command("/"+id+"/revisions",revised,key));
        assertThat(api.data(orders.command("/"+id+"/revisions",revised,key))).isEqualTo(result);
        assertThat(result.get("status").asText()).isEqualTo("READY");assertThat(result.get("revision").asInt()).isEqualTo(2);
        var history=history(id);assertThat(history.size()).isEqualTo(2);
        assertThat(history.get(0).get("autoDeductInventory").asBoolean()).isFalse();
        assertThat(history.get(1).get("autoDeductInventory").asBoolean()).isTrue();
        assertThat(history.get(1).get("documentStatus").asText()).isEqualTo("SUPERSEDED");
        assertThat(api.data(get("/api/v1/orders/"+id).param("revision","1")).get("plan")).isEqualTo(originalPlan);
        assertThat(outboxCount(id)).isEqualTo(2);
    }
    @Test void cancelReadyWritesTombstoneAndPreservesOriginalPublicationReceipt() throws Exception {
        orders.policy("0",false);String id=orders.create(orders.plan()).get("orderId").asText();String key=UUID.randomUUID().toString();
        var body=orders.release("1","1");var original=api.data(orders.command("/"+id+"/publish",body,key));
        api.data(orders.command("/"+id+"/cancel",orders.cancellation("2"),UUID.randomUUID().toString()));
        assertThat(api.data(orders.command("/"+id+"/publish",body,key))).isEqualTo(original);
        assertThat(orders.detail(id).at("/order/status").asText()).isEqualTo("CANCELLED");
        assertThat(history(id).get(0).get("documentStatus").asText()).isEqualTo("SUPERSEDED");
        assertThat(outboxCount(id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT event_type FROM order_sync_outbox WHERE order_id=? AND entity_version=3",
            String.class,Ids.bytes(UUID.fromString(id)))).isEqualTo("ORDER_CANCELLED");
    }
    @Test void outdatedPolicyCannotBeSilentlyReplacedDuringPublishOrRevision() throws Exception {
        orders.policy("0",true);String id=orders.create(orders.plan()).get("orderId").asText();orders.policy("1",false);
        api.send(orders.command("/"+id+"/publish",orders.release("1","1"),UUID.randomUUID().toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("POLICY_VERSION_CONFLICT"));
        api.data(orders.command("/"+id+"/publish",orders.release("1","2"),UUID.randomUUID().toString()));
        api.send(orders.command("/"+id+"/revisions",orders.revision(orders.plan(),"2"),UUID.randomUUID().toString()))
            .andExpect(status().isBadRequest());
        assertThat(orders.detail(id).at("/order/rowVersion").asText()).isEqualTo("2");
    }
    @Test void assignedOrderCannotUseUnassignedMutationPaths() throws Exception {
        orders.policy("0",false);var plan=orders.plan();String id=orders.create(plan).get("orderId").asText();
        jdbc.update("UPDATE outbound_order SET status='ASSIGNED' WHERE id=?",Ids.bytes(UUID.fromString(id)));
        api.send(orders.command("/"+id+"/cancel",orders.cancellation("1"),UUID.randomUUID().toString())).andExpect(status().isConflict());
        api.send(orders.command("/"+id+"/revisions",orders.revision(plan,"1"),UUID.randomUUID().toString())).andExpect(status().isConflict());
        api.send(orders.command("/"+id+"/publish",orders.release("1","1"),UUID.randomUUID().toString())).andExpect(status().isConflict());
        assertThat(outboxCount(id)).isZero();
    }
    @Test void publishPermissionAndWarehouseScopeAreEnforced() throws Exception {
        orders.policy("0",true);String id=orders.create(orders.plan()).get("orderId").asText();
        var outsider=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));
        outsider.send(orders.command("/"+id+"/publish",orders.release("1","1"),UUID.randomUUID().toString())).andExpect(status().isNotFound());
        outsider.send(get("/api/v1/orders/"+id+"/publications")).andExpect(status().isNotFound());
        jdbc.update("DELETE FROM user_role WHERE user_id=?",Ids.bytes(api.identity().user().id()));
        jdbc.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code='ORDER_VIEWER'",Ids.bytes(api.identity().user().id()));
        api.send(orders.command("/"+id+"/publish",orders.release("1","1"),UUID.randomUUID().toString())).andExpect(status().isForbidden());
    }
    private com.fasterxml.jackson.databind.JsonNode history(String id) throws Exception {
        return api.data(get("/api/v1/orders/"+id+"/publications")).get("items");
    }
    private long outboxCount(String id) { return jdbc.queryForObject("SELECT COUNT(*) FROM order_sync_outbox WHERE order_id=?",Long.class,Ids.bytes(UUID.fromString(id))); }
}
