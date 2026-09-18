package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
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
class PcSearchIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    private PublicationDriver api;
    @BeforeEach void identity() { api=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN")); }
    private String start(byte[] stock) throws Exception {
        api.publish(api.stage(stock));var orders=new OrderTestDriver(api);orders.policy("0",false);var plan=orders.plan();plan.withArray("lines").removeAll();
        var codes=jdbc.queryForList("SELECT delivery_code FROM delivery_mapping WHERE warehouse_id=? ORDER BY delivery_code",String.class,Ids.bytes(api.identity().warehouse()));
        for(String code:codes) plan.withArray("lines").addObject().put("deliveryCode",code).put("plannedCount",20);
        String order=orders.create(plan).get("orderId").asText();String path="/api/v1/orders/"+order+"/pc";
        var body=json.createObjectNode().put("writerId",UUID.randomUUID().toString()).put("expectedVersion","1").put("policyVersion","1").put("reason","核对查询范围");
        api.data(post(path+"/start").header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json").content(json.writeValueAsBytes(body)));return path;
    }
    @Test void twoMatchedProductsUseSuffixPriorityAndNeverMatchAcrossCodeBoundary() throws Exception {
        String path=start(SkuFixture.concat(SkuFixture.row("003ABCD1234","300.501"),SkuFixture.row("003A1234BCD","300.501"),SkuFixture.row("571ABCD1234","300.501"),SkuFixture.row("719ZZZZZZZZ","300.501")));
        var found=api.data(get(path+"/candidates").param("query","1234"));
        assertThat(found.get("matchedCodes").asInt()).isEqualTo(2);assertThat(found.at("/groups/0/items/0/barcode").asText()).isEqualTo("003ABCD1234");
        assertThat(api.data(get(path)).get("products").size()).isEqualTo(3);
        assertThat(api.data(get(path+"/candidates").param("query","3ABC")).get("total").asInt()).isZero();
        assertThat(api.data(get(path+"/candidates").param("query","003abcd1234")).at("/groups/0/items/0/barcode").asText()).isEqualTo("003ABCD1234");
        jdbc.update("UPDATE inventory_position p INNER JOIN bale b ON b.id=p.bale_id SET p.blocked_reason='库存待核对' WHERE b.warehouse_id=? AND b.barcode='003ABCD1234'",Ids.bytes(api.identity().warehouse()));
        assertThat(api.data(get(path+"/candidates").param("query","003ABCD1234")).at("/groups/0/items/0/availability").asText()).isEqualTo("UNAVAILABLE");
    }
    @Test void sampleQueryIsBoundedAcrossAll145ProductsAndRateLimited() throws Exception {
        String path=start(Files.readAllBytes(Path.of("../docs/sku.txt")));var timings=new ArrayList<Double>();
        for(int i=0;i<5;i++) {
            long began=System.nanoTime();var result=api.data(get(path+"/candidates").param("query","0000"));timings.add((System.nanoTime()-began)/1_000_000.0);
            for(var group:result.get("groups")) assertThat(group.get("items").size()).isLessThanOrEqualTo(5);
        }
        Files.writeString(Path.of("build/pc-search-performance.json"),json.writeValueAsString(Map.of("inventoryBales",12639,"products",145,"query","0000","samplesMs",timings,"note","本机单用户MockMvc含真实MySQL查询；非生产负载验收")));
        for(int i=5;i<120;i++) api.data(get(path+"/candidates").param("query","NOHIT000000"));
        api.send(get(path+"/candidates").param("query","0000")).andExpect(status().isTooManyRequests());
    }
}
