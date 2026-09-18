package com.acrylic.outbound.inventory;

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
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @AutoConfigureMockMvc
class PcExceptionConcurrencyIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    private PcExceptionFixture f;
    @BeforeEach void setup() throws Exception { f=new PcExceptionFixture(new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"))); }
    @Test void concurrentStopAndRecordingHaveOneVersionWinnerAndNoUncapturedAcceptedEntry() throws Exception {
        var stop=f.stopBody();var add=f.pc.write(f.pc.barcodes.get(1));
        var results=race(()->call("exception/stop",stop,UUID.randomUUID().toString()),()->call("entries",add,UUID.randomUUID().toString()));
        assertThat(results.stream().filter(r->r.get("code").asText().equals("OK")).count()).isEqualTo(1);
        if(f.pc.view().at("/work/status").asText().equals("ACTIVE"))f.stop();
        long captured=f.view().at("/stopped/snapshot/entries").size();assertThat(f.held(jdbc,"PC_RESERVED")).isEqualTo(captured);
    }
    @Test void duplicateHandoverAppliesOnceAndReturnsIdenticalReceipt() throws Exception {
        f.stop();f.propose(f.plan(f.pc.api.identity().user().username(),"RELEASE","QUARANTINE"));f.approve();
        var apply=f.application();String key=UUID.randomUUID().toString();var results=race(()->call("exception/apply",apply,key),()->call("exception/apply",apply,key));
        assertThat(results.stream().map(r->r.get("code").asText())).containsOnly("OK");assertThat(results.get(0).get("data")).isEqualTo(results.get(1).get("data"));
        assertThat(f.pc.view().at("/work/round").asInt()).isEqualTo(2);assertThat(f.held(jdbc,"PC_RESERVED")).isEqualTo(1);assertThat(f.held(jdbc,"QUARANTINED")).isEqualTo(1);
    }
    @Test void withdrawingApprovalAndApplyingItCannotBothSucceed() throws Exception {
        f.stop();f.propose(f.plan(f.pc.api.identity().user().username(),"RELEASE"));f.approve();
        var apply=f.application();var reject=f.decision("REJECT");
        var results=race(()->call("exception/apply",apply,UUID.randomUUID().toString()),()->call("exception/review",reject,UUID.randomUUID().toString()));
        assertThat(results.stream().filter(r->r.get("code").asText().equals("OK")).count()).isEqualTo(1);
        String status=f.view().at("/plans/0/status").asText();assertThat(status).isIn("APPLIED","REJECTED");
        assertThat(f.held(jdbc,"PC_RESERVED")).isEqualTo(status.equals("APPLIED")?2:3);
    }
    private JsonNode call(String action,JsonNode body,String key) throws Exception {
        return json.readTree(f.pc.api.send(f.pc.request(action,(com.fasterxml.jackson.databind.node.ObjectNode)body,key)).andReturn().getResponse().getContentAsString());
    }
    private List<JsonNode> race(Callable<JsonNode> first,Callable<JsonNode> second) throws Exception {
        var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->{ready.countDown();start.await(5,TimeUnit.SECONDS);return first.call();});
            var b=pool.submit(()->{ready.countDown();start.await(5,TimeUnit.SECONDS);return second.call();});
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();return List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));
        }
    }
}
