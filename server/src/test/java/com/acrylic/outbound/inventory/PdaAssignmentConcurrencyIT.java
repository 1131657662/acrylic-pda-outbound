package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class PdaAssignmentConcurrencyIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    private PublicationDriver web;private PdaTestDriver pda;private String order;private JsonNode tokens;
    @BeforeEach void fixture() throws Exception {
        web=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));
        pda=new PdaTestDriver(web);order=pda.readyOrder();tokens=pda.login(pda.register());
    }
    @Test void twoDevicesRacingHaveExactlyOneWinner() throws Exception {
        var other=pda.login(pda.register());
        var result=compete(new Attempt(pda.claim(order,UUID.randomUUID().toString()),tokens),
            new Attempt(pda.claim(order,UUID.randomUUID().toString()),other));
        assertThat(result.stream().map(r->r.get("code").asText())).containsExactlyInAnyOrder("OK","ORDER_ASSIGNED");
        assertSingleAssignment();
    }
    @Test void concurrentSameKeyRetriesReturnIdenticalAssignment() throws Exception {
        String key=UUID.randomUUID().toString();
        var result=compete(new Attempt(pda.claim(order,key),tokens),new Attempt(pda.claim(order,key),tokens));
        assertThat(result).allSatisfy(r->assertThat(r.get("code").asText()).isEqualTo("OK"));
        assertThat(result.get(0).get("data")).isEqualTo(result.get(1).get("data"));assertSingleAssignment();
    }
    @Test void claimAndCancellationCannotBothSucceed() throws Exception {
        var orders=new OrderTestDriver(web);
        var cancel=orders.command("/"+order+"/cancel",orders.cancellation("2"),UUID.randomUUID().toString());
        var result=compete(new Attempt(pda.claim(order,UUID.randomUUID().toString()),tokens),new Attempt(cancel,null));
        assertThat(result.stream().filter(r->r.get("code").asText().equals("OK")).count()).isEqualTo(1);
        String state=new OrderTestDriver(web).detail(order).at("/order/status").asText();
        assertThat(state).isIn("ASSIGNED","CANCELLED");
        int count=jdbc.queryForObject("SELECT COUNT(*) FROM order_assignment WHERE order_id=?",Integer.class,Ids.bytes(UUID.fromString(order)));
        assertThat(count).isEqualTo(state.equals("ASSIGNED")?1:0);
    }
    @Test void disablingDeviceCompetesWithClaimWithoutLosingSuccessfulAssignment() throws Exception {
        String device=tokens.get("deviceId").asText();
        var body=json.createObjectNode().put("expectedVersion","1").put("status","DISABLED").put("reason","停用故障设备");
        var disable=post("/api/v1/devices/"+device+"/status").header("Idempotency-Key",UUID.randomUUID().toString())
            .contentType("application/json").content(json.writeValueAsBytes(body));
        var result=compete(new Attempt(pda.claim(order,UUID.randomUUID().toString()),tokens),new Attempt(disable,null));
        assertThat(result.get(1).get("code").asText()).isEqualTo("OK");
        assertThat(result.get(0).get("code").asText()).isIn("OK","AUTH_EXPIRED","DEVICE_DISABLED");
        var summary=web.data(get("/api/v1/orders/"+order+"/assignment")).get("assignment");
        if(result.get(0).get("code").asText().equals("OK")) {
            assertSingleAssignment();assertThat(summary.get("id")).isEqualTo(result.get(0).at("/data/id"));
        } else {
            assertThat(summary.isNull()).isTrue();assertThat(new OrderTestDriver(web).detail(order).at("/order/status").asText()).isEqualTo("READY");
        }
    }
    @Test void outboxFailureRollsBackAssignmentCredentialReceiptAndOrder() throws Exception {
        byte[] id=Ids.bytes(UUID.fromString(order));String key=UUID.randomUUID().toString();
        jdbc.update("""
            INSERT INTO order_sync_outbox(id,recovery_epoch,warehouse_id,order_id,entity_version,event_type,payload,created_at)
            VALUES(?,1,?,?,3,'TEST_FAULT','{}',UTC_TIMESTAMP(3))
            """,Ids.bytes(Ids.next()),Ids.bytes(web.identity().warehouse()),id);
        pda.send(pda.claim(order,key),tokens).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_assignment WHERE order_id=?",Integer.class,id)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM offline_authorization WHERE user_id=?",Integer.class,Ids.bytes(web.identity().user().id()))).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM api_idempotency_request WHERE principal_id=? AND route_scope='pda.assign'",
            Integer.class,Ids.bytes(web.identity().user().id()))).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_event WHERE entity_id=? AND event_type='ORDER_ASSIGNED'",Integer.class,id)).isZero();
        assertThat(new OrderTestDriver(web).detail(order).at("/order/status").asText()).isEqualTo("READY");
        jdbc.update("DELETE FROM order_sync_outbox WHERE order_id=? AND event_type='TEST_FAULT'",id);
        pda.data(pda.claim(order,key),tokens);assertSingleAssignment();
    }
    private List<JsonNode> compete(Attempt first,Attempt second) throws Exception {
        var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->send(first,ready,start));var b=pool.submit(()->send(second,ready,start));
            try { assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue(); }
            finally { start.countDown(); }
            return List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));
        }
    }
    private JsonNode send(Attempt attempt,CountDownLatch ready,CountDownLatch start) throws Exception {
        ready.countDown();if(!start.await(5,TimeUnit.SECONDS)) throw new TimeoutException("Competition did not start");
        var response=attempt.tokens()==null?web.send(attempt.request()):pda.send(attempt.request(),attempt.tokens());
        return json.readTree(response.andReturn().getResponse().getContentAsString());
    }
    private void assertSingleAssignment() throws Exception {
        byte[] id=Ids.bytes(UUID.fromString(order));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_assignment WHERE order_id=?",Integer.class,id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_sync_outbox WHERE order_id=? AND event_type='ORDER_ASSIGNED'",Integer.class,id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_event WHERE entity_id=? AND event_type='ORDER_ASSIGNED'",Integer.class,id)).isEqualTo(1);
        assertThat(new OrderTestDriver(web).detail(order).at("/order/rowVersion").asText()).isEqualTo("3");
    }
    private record Attempt(MockHttpServletRequestBuilder request,JsonNode tokens) { }
}
