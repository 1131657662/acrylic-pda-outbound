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
class OrderApiIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    private PublicationDriver api;private OrderTestDriver orders;
    @BeforeEach void fixture() throws Exception {
        api=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));
        orders=new OrderTestDriver(api);orders.stock();
    }
    @Test void multiProductOrderExceeds116AndSameVehicleCanHaveTwoTrips() throws Exception {
        var plan=orders.plan();var first=orders.create(plan);var second=orders.create(plan);
        var detail=orders.detail(first.get("orderId").asText());
        assertThat(first.get("orderId")).isNotEqualTo(second.get("orderId"));
        assertThat(detail.at("/plan/plannedTotal").asText()).isEqualTo("361");
        assertThat(detail.get("lines").size()).isEqualTo(2);
        assertThat(detail.at("/lines/0/deliveryCode").asText()).isEqualTo("003");
        assertThat(detail.at("/order/status").asText()).isEqualTo("DRAFT");
        assertThat(detail.at("/plan/inventorySnapshotId")).isEqualTo(plan.get("expectedActiveSnapshot"));
    }
    @Test void replayKeepsOriginalReceiptAfterFurtherRevision() throws Exception {
        var plan=orders.plan();String key=UUID.randomUUID().toString();
        var receipt=api.data(orders.command("",plan,key));String id=receipt.get("orderId").asText();
        var revised=plan.deepCopy().put("vehicleNo","新车号");
        api.data(orders.command("/"+id+"/revisions",orders.revision(revised,"1"),UUID.randomUUID().toString()));
        assertThat(api.data(orders.command("",plan,key))).isEqualTo(receipt);
        api.send(orders.command("",revised,key)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONTENT_MISMATCH"));
        assertThat(orders.detail(id).at("/plan/vehicleNo").asText()).isEqualTo("新车号");
    }
    @Test void oldPlanRemainsImmutableAfterMappingAndInventoryChanges() throws Exception {
        String id=orders.create(orders.plan()).get("orderId").asText();var original=orders.detail(id).get("plan");
        api.revise("003","1","修订型号",true);
        var check=api.data(get("/api/v1/orders/"+id+"/preflight"));
        assertThat(check.get("checksPassed").asBoolean()).isFalse();
        assertThat(check.at("/lines/0/mappingValid").asBoolean()).isFalse();
        var plan=orders.plan();api.data(orders.command("/"+id+"/revisions",orders.revision(plan,"1"),UUID.randomUUID().toString()));
        var old=api.data(get("/api/v1/orders/"+id).param("revision","1"));
        assertThat(old.get("plan")).isEqualTo(original);
        assertThat(old.at("/lines/0/model").asText()).isEqualTo("H815B 1.7 RE 38 300");
        assertThat(orders.detail(id).at("/lines/0/model").asText()).isEqualTo("修订型号");
        assertThat(api.data(get("/api/v1/orders/"+id+"/revisions")).get("items").size()).isEqualTo(2);
        assertThat(api.data(get("/api/v1/orders/"+id+"/events")).get("items").size()).isEqualTo(2);
    }
    @Test void outdatedVersionCannotOverwriteOrCancelNewRevision() throws Exception {
        var plan=orders.plan();String id=orders.create(plan).get("orderId").asText();
        api.data(orders.command("/"+id+"/revisions",orders.revision(plan,"1"),UUID.randomUUID().toString()));
        api.send(orders.command("/"+id+"/revisions",orders.revision(plan,"1"),UUID.randomUUID().toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_VERSION_CONFLICT"));
        api.send(orders.command("/"+id+"/cancel",orders.cancellation("1"),UUID.randomUUID().toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_VERSION_CONFLICT"));
        assertThat(orders.detail(id).at("/order/rowVersion").asText()).isEqualTo("2");
    }
    @Test void cancelPreservesPlansAndReplayButPreventsEditing() throws Exception {
        var plan=orders.plan();String id=orders.create(plan).get("orderId").asText();String key=UUID.randomUUID().toString();
        var receipt=api.data(orders.command("/"+id+"/cancel",orders.cancellation("1"),key));
        assertThat(api.data(orders.command("/"+id+"/cancel",orders.cancellation("1"),key))).isEqualTo(receipt);
        assertThat(orders.detail(id).at("/order/status").asText()).isEqualTo("CANCELLED");
        assertThat(orders.detail(id).get("lines").size()).isEqualTo(2);
        api.send(orders.command("/"+id+"/revisions",orders.revision(plan,"2"),UUID.randomUUID().toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_STATE_CONFLICT"));
    }
    @Test void preflightWarnsShortageWithoutClaimingReservationOrDispatch() throws Exception {
        String id=orders.create(orders.plan()).get("orderId").asText();
        var check=api.data(get("/api/v1/orders/"+id+"/preflight"));
        assertThat(check.get("dispatchAvailable").asBoolean()).isFalse();
        assertThat(check.at("/lines/0/shortage").asLong()).isEqualTo(179);
        jdbc.update("UPDATE inventory_position SET blocked_reason='隔离' WHERE bale_id=(SELECT id FROM bale WHERE barcode='003B0051601')");
        assertThat(api.data(get("/api/v1/orders/"+id+"/preflight")).at("/lines/0/candidateCount").asInt()).isZero();
    }
    @Test void rejectsDuplicateProductsNonIntegerPlansAndDisabledMapping() throws Exception {
        var plan=orders.plan();plan.withArray("lines").add(plan.withArray("lines").get(0).deepCopy());
        api.send(orders.command("",plan,UUID.randomUUID().toString())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ORDER_DUPLICATE_PRODUCT"));
        var invalid=orders.plan();((com.fasterxml.jackson.databind.node.ObjectNode)invalid.withArray("lines").get(0)).put("plannedCount",0);
        api.send(orders.command("",invalid,UUID.randomUUID().toString())).andExpect(status().isBadRequest());
        ((com.fasterxml.jackson.databind.node.ObjectNode)invalid.withArray("lines").get(0)).put("plannedCount",1.5);
        api.send(orders.command("",invalid,UUID.randomUUID().toString())).andExpect(status().isBadRequest());
        api.revise("003","1","停用型号",false);
        api.send(orders.command("",orders.plan(),UUID.randomUUID().toString())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_PRODUCT_UNAVAILABLE"));
    }
    @Test void listUsesStableCursorAndWarehouseScope() throws Exception {
        orders.create(orders.plan());orders.create(orders.plan());
        var page=api.data(get("/api/v1/orders").param("limit","1"));
        var next=api.data(get("/api/v1/orders").param("limit","1").param("cursor",page.get("nextCursor").asText()));
        assertThat(page.at("/items/0/id")).isNotEqualTo(next.at("/items/0/id"));
        assertThat(next.get("hasMore").asBoolean()).isFalse();
        var other=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));
        other.send(get("/api/v1/orders/"+page.at("/items/0/id").asText())).andExpect(status().isNotFound());
        api.send(get("/api/v1/orders").param("cursor","eHx5")).andExpect(status().isBadRequest());
    }
    @Test void orderViewerCannotWriteAndInventoryViewerCannotReadOrders() throws Exception {
        var viewer=IdentityFixture.create(jdbc,passwords,"ORDER_VIEWER");
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)",Ids.bytes(viewer.user().id()),Ids.bytes(api.identity().warehouse()));
        var readonly=new PublicationDriver(mvc,json,new IdentityFixture(viewer.user(),api.identity().warehouse()));
        readonly.send(orders.command("",orders.plan(),UUID.randomUUID().toString())).andExpect(status().isForbidden());
        assertThat(readonly.data(get("/api/v1/orders")).get("items").size()).isZero();
        var inventoryOnly=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_VIEWER"));
        inventoryOnly.send(get("/api/v1/orders")).andExpect(status().isForbidden());
    }
    @Test void staleInventoryBaselineIsRejectedWithoutCreatingOrder() throws Exception {
        var plan=orders.plan();api.revise("571","1","新的映射",true);
        api.send(orders.command("",plan,UUID.randomUUID().toString())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_BASELINE_CHANGED"));
        assertThat(api.data(get("/api/v1/orders")).get("items").size()).isZero();
    }
    @Test void chineseVehicleAndAsciiOrderNumberBothFilterExactly() throws Exception {
        String id=orders.create(orders.plan()).get("orderId").asText();
        var detail=orders.detail(id);String number=detail.at("/order/orderNo").asText();
        assertThat(api.data(get("/api/v1/orders").param("search","苏A-测试车")).get("items").size()).isEqualTo(1);
        assertThat(api.data(get("/api/v1/orders").param("search",number)).at("/items/0/id").asText()).isEqualTo(id);
        assertThat(api.data(get("/api/v1/orders").param("search","苏A-其他车' OR 1=1 --")).get("items").size()).isZero();
    }
}
