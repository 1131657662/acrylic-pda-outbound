package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
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
class OrderNumberApiIT {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc; @Autowired PasswordEncoder passwords;
    private PublicationDriver web; private OrderTestDriver orders;
    @BeforeEach void fixture() throws Exception {
        web=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));
        orders=new OrderTestDriver(web);orders.stock();
    }

    @Test void sameNumberReachesWebPdaAndSignedPreparationWithoutChangingVehicle() throws Exception {
        var pda=new PdaTestDriver(web);String id=pda.readyOrder();
        var detail=orders.detail(id);String number=detail.at("/order/orderNo").asText();
        assertThat(number).matches("FH[0-9]{12,18}");
        String createdDate=jdbc.queryForObject("SELECT DATE_FORMAT(created_at + INTERVAL 8 HOUR,'%Y%m%d') FROM outbound_order WHERE id=?",String.class,Ids.bytes(UUID.fromString(id)));
        assertThat(number).startsWith("FH"+createdDate);
        assertThat(web.data(get("/api/v1/orders").param("search",number)).at("/items/0/orderNo").asText()).isEqualTo(number);
        var tokens=pda.login(pda.register());var available=pda.data(get("/api/v1/pda/orders"),tokens);
        assertThat(available.at("/items/0/orderNo").asText()).isEqualTo(number);
        assertThat(available.at("/items/0/vehicleNo").asText()).isEqualTo("苏A-测试车");

        var assignment=pda.data(pda.claim(id,UUID.randomUUID().toString()),tokens);
        String base="/api/v1/pda/assignments/"+assignment.get("id").asText();
        var receipt=pda.data(post(base+"/preparation").header("X-Server-Recovery-Epoch","1"),tokens);
        byte[] compressed=pda.send(get(base+"/content/plan").param("bundleId",receipt.get("bundleId").asText()),tokens)
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        var plan=json.readTree(new GZIPInputStream(new ByteArrayInputStream(compressed)).readAllBytes());
        assertThat(plan.at("/order/order/orderNo").asText()).isEqualTo(number);
        assertThat(plan.at("/order/plan/vehicleNo").asText()).isEqualTo("苏A-测试车");
    }

    @Test void replayAndRevisionKeepTheOriginalNumber() throws Exception {
        var plan=orders.plan();String key=UUID.randomUUID().toString();
        var receipt=web.data(orders.command("",plan,key));String id=receipt.get("orderId").asText();
        String number=orders.detail(id).at("/order/orderNo").asText();
        long before=jdbc.queryForObject("SELECT SUM(last_sequence) FROM order_number_counter",Long.class);
        assertThat(web.data(orders.command("",plan,key))).isEqualTo(receipt);
        web.data(orders.command("/"+id+"/revisions",orders.revision(plan.deepCopy().put("vehicleNo","苏A-修订车"),"1"),UUID.randomUUID().toString()));
        assertThat(orders.detail(id).at("/order/orderNo").asText()).isEqualTo(number);
        assertThat(jdbc.queryForObject("SELECT SUM(last_sequence) FROM order_number_counter",Long.class)).isEqualTo(before);
    }

    @Test void legacyNumberRemainsSearchableAndUnchangedAlongsideNewOrders() throws Exception {
        String id=orders.create(orders.plan()).get("orderId").asText();String legacy="OB"+id.replace("-","");
        jdbc.update("UPDATE outbound_order SET order_no=? WHERE id=?",legacy,Ids.bytes(UUID.fromString(id)));
        var next=orders.create(orders.plan());
        assertThat(orders.detail(next.get("orderId").asText()).at("/order/orderNo").asText()).startsWith("FH");
        assertThat(orders.detail(id).at("/order/orderNo").asText()).isEqualTo(legacy);
        assertThat(web.data(get("/api/v1/orders").param("search",legacy)).at("/items/0/id").asText()).isEqualTo(id);
    }
}
