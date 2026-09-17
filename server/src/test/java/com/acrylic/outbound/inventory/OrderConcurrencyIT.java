package com.acrylic.outbound.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest @AutoConfigureMockMvc
class OrderConcurrencyIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    private OrderTestDriver fixture() throws Exception {
        var api=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));
        String serial=UUID.randomUUID().toString().replace("-","").substring(0,7).toUpperCase();
        api.publish(api.stage(SkuFixture.concat(SkuFixture.row("003X"+serial,"300.5"),SkuFixture.row("571X"+serial,"301"))));
        return new OrderTestDriver(api);
    }
    @Test void simultaneousCreateWithSameKeyCreatesExactlyOneOrder() throws Exception {
        var orders=fixture();var plan=orders.plan();String key=UUID.randomUUID().toString();
        var result=compete(orders,new Command("",plan,key),new Command("",plan,key));
        assertThat(result).allSatisfy(response->assertThat(response.get("code").asText()).isEqualTo("OK"));
        assertThat(result.get(0).get("data")).isEqualTo(result.get(1).get("data"));
        assertThat(orders.api().data(get("/api/v1/orders")).get("items").size()).isEqualTo(1);
    }
    @Test void simultaneousRevisionsCannotBothReplaceTheSameVersion() throws Exception {
        var orders=fixture();var plan=orders.plan();String id=orders.create(plan).get("orderId").asText();
        var first=orders.revision(plan.deepCopy().put("vehicleNo","车辆一"),"1");
        var second=orders.revision(plan.deepCopy().put("vehicleNo","车辆二"),"1");
        var result=compete(orders,new Command("/"+id+"/revisions",first,UUID.randomUUID().toString()),
            new Command("/"+id+"/revisions",second,UUID.randomUUID().toString()));
        assertThat(result.stream().map(r->r.get("code").asText())).containsExactlyInAnyOrder("OK","ORDER_VERSION_CONFLICT");
        assertThat(orders.api().data(get("/api/v1/orders/"+id+"/revisions")).get("items").size()).isEqualTo(2);
    }
    private List<com.fasterxml.jackson.databind.JsonNode> compete(OrderTestDriver orders,Command a,Command b) throws Exception {
        var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->send(new Attempt(orders,a,ready,start)));
            var second=pool.submit(()->send(new Attempt(orders,b,ready,start)));
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();
            return List.of(first.get(30,TimeUnit.SECONDS),second.get(30,TimeUnit.SECONDS));
        }
    }
    @Test void simultaneousPublishWithSameKeyCreatesSinglePublicationAndTask() throws Exception {
        var orders=fixture();orders.policy("0",true);String id=orders.create(orders.plan()).get("orderId").asText();
        var command=new Command("/"+id+"/publish",orders.release("1","1"),UUID.randomUUID().toString());
        var result=compete(orders,command,command);
        assertThat(result).allSatisfy(r->assertThat(r.get("code").asText()).isEqualTo("OK"));
        assertThat(result.get(0).get("data")).isEqualTo(result.get(1).get("data"));
        assertThat(orders.api().data(get("/api/v1/orders/"+id+"/publications")).get("items").size()).isEqualTo(1);
    }
    @Test void simultaneousPublishAndCancelCannotBothUseSameOrderVersion() throws Exception {
        var orders=fixture();orders.policy("0",false);String id=orders.create(orders.plan()).get("orderId").asText();
        var result=compete(orders,new Command("/"+id+"/publish",orders.release("1","1"),UUID.randomUUID().toString()),
            new Command("/"+id+"/cancel",orders.cancellation("1"),UUID.randomUUID().toString()));
        assertThat(result.stream().map(r->r.get("code").asText())).containsExactlyInAnyOrder("OK","ORDER_VERSION_CONFLICT");
        String state=orders.detail(id).at("/order/status").asText();
        assertThat(state).isIn("READY","CANCELLED");
        assertThat(orders.api().data(get("/api/v1/orders/"+id+"/publications")).get("items").size()).isEqualTo(state.equals("READY")?1:0);
    }
    private com.fasterxml.jackson.databind.JsonNode send(Attempt attempt) throws Exception {
        attempt.ready().countDown();if(!attempt.start().await(5,TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
        var command=attempt.command();var api=attempt.orders().api();
        return json.readTree(api.send(attempt.orders().command(command.path(),command.body(),command.key()))
            .andReturn().getResponse().getContentAsString());
    }
    private record Command(String path,ObjectNode body,String key) { }
    private record Attempt(OrderTestDriver orders,Command command,CountDownLatch ready,CountDownLatch start) { }
}
