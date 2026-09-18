package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @AutoConfigureMockMvc
class PcConcurrencyIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    private PcFixture fixture() throws Exception { return new PcFixture(new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN")),false,2,1); }
    @Test void competingPcOrdersCannotBothReserveOneBale() throws Exception {
        var first=fixture();first.start();var second=new PcFixture(first);second.start();
        var a=first.request("entries",first.write(first.barcodes.get(0)),UUID.randomUUID().toString());
        var b=second.request("entries",second.write(second.barcodes.get(0)),UUID.randomUUID().toString());
        var results=race(()->json.readTree(first.api.send(a).andReturn().getResponse().getContentAsString()),()->json.readTree(second.api.send(b).andReturn().getResponse().getContentAsString()));
        assertThat(results.stream().map(r->r.get("code").asText())).containsExactlyInAnyOrder("OK","PC_BALE_UNAVAILABLE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment c INNER JOIN bale b ON b.id=c.bale_id WHERE b.barcode=?",Integer.class,first.barcodes.get(0))).isEqualTo(1);
    }
    @Test void duplicateStartCreatesOneWorkAndOnePublication() throws Exception {
        var f=fixture();var body=f.startBody("1");String key=UUID.randomUUID().toString();
        var results=race(()->f.api.data(f.request("start",body,key)),()->f.api.data(f.request("start",body,key)));
        assertThat(results.get(0)).isEqualTo(results.get(1));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pc_work WHERE order_id=?",Integer.class,Ids.bytes(UUID.fromString(f.order)))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_publication WHERE order_id=?",Integer.class,Ids.bytes(UUID.fromString(f.order)))).isEqualTo(1);
    }
    @Test void pdaAndPcCompetingClaimsHaveExactlyOneWinner() throws Exception {
        var f=fixture();f.api.data(f.orders.command("/"+f.order+"/publish",f.orders.release("1","1"),UUID.randomUUID().toString()));
        var pda=new PdaTestDriver(f.api);var tokens=pda.login(pda.register());
        var results=race(()->json.readTree(f.api.send(f.request("start",f.startBody("2"),UUID.randomUUID().toString())).andReturn().getResponse().getContentAsString()),
            ()->json.readTree(pda.send(pda.claim(f.order,UUID.randomUUID().toString()),tokens).andReturn().getResponse().getContentAsString()));
        assertThat(results.stream().filter(r->r.get("code").asText().equals("OK")).count()).isEqualTo(1);
        long count=jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM pc_work WHERE order_id=?)+(SELECT COUNT(*) FROM order_assignment WHERE order_id=? AND active_marker=1)",Long.class,Ids.bytes(UUID.fromString(f.order)),Ids.bytes(UUID.fromString(f.order)));
        assertThat(count).isEqualTo(1);
    }
    private List<JsonNode> race(Callable<JsonNode> first,Callable<JsonNode> second) throws Exception {
        var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->{ ready.countDown();start.await(5,TimeUnit.SECONDS);return first.call(); });
            var b=pool.submit(()->{ ready.countDown();start.await(5,TimeUnit.SECONDS);return second.call(); });
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();return List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));
        }
    }
}
