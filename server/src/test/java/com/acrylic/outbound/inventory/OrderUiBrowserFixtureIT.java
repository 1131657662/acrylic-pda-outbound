package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest @AutoConfigureMockMvc
class OrderUiBrowserFixtureIT {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc; @Autowired PasswordEncoder passwords;
    @Test void createsIsolatedOrdersForBrowserVerification() throws Exception {
        var identity=IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN");
        var other=IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN");
        jdbc.update("UPDATE warehouse SET name='界面验证一仓' WHERE id=?",Ids.bytes(identity.warehouse()));
        jdbc.update("UPDATE warehouse SET name='界面验证二仓' WHERE id=?",Ids.bytes(other.warehouse()));
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)",Ids.bytes(identity.user().id()),Ids.bytes(other.warehouse()));
        var api=new PublicationDriver(mvc,json,identity); var orders=new OrderTestDriver(api);
        String serial=java.util.UUID.randomUUID().toString().replace("-","").substring(0,7).toUpperCase();
        api.publish(api.stage(SkuFixture.concat(SkuFixture.row("003X"+serial,"300.5"),SkuFixture.row("571X"+serial,"301"))));
        var ids=json.createArrayNode();
        for(int i=0;i<57;i++) {
            var plan=orders.plan().put("vehicleNo",i<43?"苏A-分页验证":"苏B-界面验证"+i)
                .put("notes","界面自动化合成订单，仅用于本机隔离测试。");
            String id=orders.create(plan).get("orderId").asText(); ids.add(id);
        }
        var viewer=IdentityFixture.create(jdbc,passwords,"ORDER_VIEWER");
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)",Ids.bytes(viewer.user().id()),Ids.bytes(identity.warehouse()));
        var inventory=IdentityFixture.create(jdbc,passwords,"WAREHOUSE_VIEWER");
        var business=IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN");
        var value=json.createObjectNode().put("username",identity.user().username()).put("password","Test-password-123")
            .put("businessUsername",business.user().username())
            .put("warehouseId",identity.warehouse().toString()).put("otherWarehouseId",other.warehouse().toString())
            .put("viewer",viewer.user().username()).put("inventoryViewer",inventory.user().username());
        value.set("orders",ids);
        Files.writeString(Path.of("build/order-ui-browser-fixture.json"),json.writeValueAsString(value));
        var plan=jdbc.queryForList("""
            EXPLAIN SELECT o.id FROM outbound_order o
            INNER JOIN order_revision r ON r.order_id=o.id AND r.revision=o.current_revision
            INNER JOIN app_user u ON u.id=o.created_by
            WHERE o.warehouse_id=? AND o.status='DRAFT' ORDER BY o.created_at DESC,o.id DESC LIMIT 20 OFFSET 20
            """,Ids.bytes(identity.warehouse()));
        Files.writeString(Path.of("build/order-pagination-explain.json"),json.writeValueAsString(plan));
    }
}
