package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
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
class OrderPaginationIT {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc; @Autowired PasswordEncoder passwords;
    private PublicationDriver api; private OrderTestDriver orders;
    @BeforeEach void fixture() throws Exception {
        api=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));
        orders=new OrderTestDriver(api); orders.stock();
    }
    private void seed(int count) throws Exception {
        for(int i=0;i<count;i++) orders.create(orders.plan());
    }
    private JsonNode page(int number,int size) throws Exception {
        return api.data(get("/api/v1/orders").param("page",Integer.toString(number)).param("pageSize",Integer.toString(size)));
    }
    @Test void emptySingleAndBoundaryPagesHaveExactTotals() throws Exception {
        assertThat(page(8,20).get("total").asInt()).isZero();
        assertThat(page(8,20).get("page").asInt()).isEqualTo(1);
        seed(1); assertThat(page(1,20).get("items").size()).isEqualTo(1);
        seed(19); assertThat(page(1,20).get("items").size()).isEqualTo(20);
        assertThat(page(2,20).get("page").asInt()).isEqualTo(1);
        seed(1);
        assertThat(page(2,20).get("items").size()).isEqualTo(1);
        assertThat(page(2,20).get("total").asInt()).isEqualTo(21);
    }
    @Test void stableTiedTimestampsCoverEveryOrderWithoutDuplicates() throws Exception {
        seed(43);
        jdbc.update("UPDATE outbound_order SET created_at='2026-09-17 00:00:00.000' WHERE warehouse_id=?",Ids.bytes(api.identity().warehouse()));
        Set<String> ids=new HashSet<>();
        for(int number=1;number<=3;number++) {
            var result=page(number,20); assertThat(result.get("total").asInt()).isEqualTo(43);
            for(var row:result.get("items")) assertThat(ids.add(row.get("id").asText())).isTrue();
        }
        assertThat(ids).hasSize(43);
        assertThat(page(1000000,20).get("page").asInt()).isEqualTo(3);
        assertThat(page(1,50).get("items").size()).isEqualTo(43);
        assertThat(page(1,100).get("pageSize").asInt()).isEqualTo(100);
    }
    @Test void exactFiltersAndStatusAffectItemsAndTotalTogether() throws Exception {
        String id=orders.create(orders.plan().put("vehicleNo","苏A-%_测试")).get("orderId").asText();
        seed(2);
        api.data(orders.command("/"+id+"/cancel",orders.cancellation("1"),UUID.randomUUID().toString()));
        var match=api.data(get("/api/v1/orders").param("page","1").param("search","苏A-%_测试").param("status","CANCELLED"));
        assertThat(match.get("total").asInt()).isEqualTo(1);
        assertThat(match.at("/items/0/id").asText()).isEqualTo(id);
        assertThat(api.data(get("/api/v1/orders").param("page","1").param("search","%")).get("total").asInt()).isZero();
        String number=orders.detail(id).at("/order/orderNo").asText();
        assertThat(api.data(get("/api/v1/orders").param("page","1").param("search",number)).get("total").asInt()).isEqualTo(1);
        assertThat(api.data(get("/api/v1/orders").param("page","1").param("status","DRAFT")).get("total").asInt()).isEqualTo(2);
    }
    @Test void totalsDoNotCrossWarehouseOrPermissionBoundaries() throws Exception {
        seed(2);
        var other=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));
        assertThat(other.data(get("/api/v1/orders").param("page","1")).get("total").asInt()).isZero();
        var viewer=IdentityFixture.create(jdbc,passwords,"ORDER_VIEWER");
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)",Ids.bytes(viewer.user().id()),Ids.bytes(api.identity().warehouse()));
        var readonly=new PublicationDriver(mvc,json,new IdentityFixture(viewer.user(),api.identity().warehouse()));
        assertThat(readonly.data(get("/api/v1/orders").param("page","1")).get("total").asInt()).isEqualTo(2);
        var inventory=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_VIEWER"));
        inventory.send(get("/api/v1/orders").param("page","1")).andExpect(status().isForbidden());
    }
    @Test void rejectsInvalidNumberedQueriesAndPreservesCursorContract() throws Exception {
        for(String value:List.of("0","-1","1000001","word"))
            api.send(get("/api/v1/orders").param("page",value)).andExpect(status().isBadRequest());
        for(String value:List.of("0","21","101"))
            api.send(get("/api/v1/orders").param("page","1").param("pageSize",value)).andExpect(status().isBadRequest());
        api.send(get("/api/v1/orders").param("page","1").param("cursor","unexpected")).andExpect(status().isBadRequest());
        api.send(get("/api/v1/orders").param("page","1").param("status","UNKNOWN")).andExpect(status().isBadRequest());
        seed(2);
        var first=api.data(get("/api/v1/orders").param("limit","1"));
        var second=api.data(get("/api/v1/orders").param("limit","1").param("cursor",first.get("nextCursor").asText()));
        assertThat(first.has("total")).isFalse();
        assertThat(first.at("/items/0/id")).isNotEqualTo(second.at("/items/0/id"));
        assertThat(second.get("hasMore").asBoolean()).isFalse();
    }
}
