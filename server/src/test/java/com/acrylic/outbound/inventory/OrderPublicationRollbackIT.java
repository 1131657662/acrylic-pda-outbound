package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class OrderPublicationRollbackIT {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc; @Autowired PasswordEncoder passwords;
    @Test void failedOutboxInsertRollsBackStatePublicationTaskAndReceipt() throws Exception {
        var identity=IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN");var api=new PublicationDriver(mvc,json,identity);
        String serial=UUID.randomUUID().toString().replace("-","").substring(0,7).toUpperCase();
        api.publish(api.stage(SkuFixture.concat(SkuFixture.row("003X"+serial,"300.5"),SkuFixture.row("571X"+serial,"301"))));
        var orders=new OrderTestDriver(api);orders.policy("0",true);
        String id=orders.create(orders.plan()).get("orderId").asText();byte[] order=Ids.bytes(UUID.fromString(id));
        jdbc.update("""
            INSERT INTO order_sync_outbox(id,recovery_epoch,warehouse_id,order_id,entity_version,event_type,payload,created_at)
            VALUES(?,1,?,?,2,'TEST_FAULT','{}',UTC_TIMESTAMP(3))
            """,Ids.bytes(Ids.next()),Ids.bytes(identity.warehouse()),order);
        String key=UUID.randomUUID().toString();var command=orders.release("1","1");
        api.send(orders.command("/"+id+"/publish",command,key)).andExpect(status().isConflict());
        assertThat(orders.detail(id).at("/order/status").asText()).isEqualTo("DRAFT");
        assertThat(orders.detail(id).at("/order/rowVersion").asText()).isEqualTo("1");
        assertThat(api.data(get("/api/v1/orders/"+id+"/publications")).get("items")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_command_receipt WHERE order_id=?",Integer.class,order)).isEqualTo(1);
        jdbc.update("DELETE FROM order_sync_outbox WHERE order_id=? AND event_type='TEST_FAULT'",order);
        assertThat(api.data(orders.command("/"+id+"/publish",command,key)).get("status").asText()).isEqualTo("READY");
    }
}
