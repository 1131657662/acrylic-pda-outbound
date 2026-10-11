package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
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
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.DEFINED_PORT,
    properties={"server.port=19434","outbound.recall.fault-withdrawal-enabled=true"}) @AutoConfigureMockMvc
class FaultRestartReliabilityHttpIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired WebApplicationContext context;
    private FaultWithdrawalHttp withdrawn() throws Exception {
        var h=new FaultWithdrawalHttp(context,new FaultRestartFixture(mvc,json,jdbc,passwords,false));
        h.data(h.withdraw(h.f.withdrawal(false),UUID.randomUUID()),200);return h;
    }
    private JsonNode login(FaultWithdrawalHttp h) throws Exception {
        var device=h.f.source.pda.register();
        return h.data(h.post("/pda/auth/login",h.f.source.pda.loginBody(device),SubmissionJson.object(),null),200);
    }

    @Test void concurrentOriginalRepublishProducesOnePublicationAndReceipt() throws Exception {
        var h=withdrawn();var f=h.f;var body=f.orders.release(f.version(),"1");var key=UUID.randomUUID();
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->{start.await();return h.web.post("/orders/"+f.source.order+"/publish",body,key);});
            var second=pool.submit(()->{start.await();return h.web.post("/orders/"+f.source.order+"/publish",body,key);});start.countDown();
            assertThat(h.data(first.get(45,TimeUnit.SECONDS),200)).isEqualTo(h.data(second.get(45,TimeUnit.SECONDS),200));
        }
        assertThat(f.source.count("order_publication")).isEqualTo(2);
        assertThat(f.source.count("bale_commitment")).isZero();assertThat(f.source.count("dispatch_record")).isZero();
    }

    @Test void independentlyRegisteredDevicesCompeteForOnlyOneNewAssignment() throws Exception {
        var h=withdrawn();var f=h.f;f.publish(UUID.randomUUID().toString());
        var first=login(h);var second=login(h);var body=f.source.pda.claimBody().put("expectedVersion",f.version());
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->{start.await();return h.post("/pda/orders/"+f.source.order+"/assignments",body,first,UUID.randomUUID());});
            var b=pool.submit(()->{start.await();return h.post("/pda/orders/"+f.source.order+"/assignments",body,second,UUID.randomUUID());});start.countDown();
            var ar=a.get(45,TimeUnit.SECONDS);var br=b.get(45,TimeUnit.SECONDS);
            assertThat(List.of(ar.statusCode(),br.statusCode())).containsExactlyInAnyOrder(200,409);
            var winner=h.data(ar.statusCode()==200?ar:br,200);
            assertThat(winner.path("workRound").asText()).isEqualTo("2");
            assertThat(winner.path("startOrderScanSeq").asText()).isEqualTo("1");
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_assignment WHERE order_id=? AND status='ACTIVE'",Integer.class,
            Ids.bytes(UUID.fromString(f.source.order)))).isEqualTo(1);
        assertThat(f.source.count("order_assignment")).isEqualTo(2);assertThat(f.source.count("bale_commitment")).isZero();
    }

    @Test void finalPublicationAuditFailureRollsBackItsReceiptSnapshotAndOutbox() throws Exception {
        var h=withdrawn();var f=h.f;var body=f.orders.release(f.version(),"1");var key=UUID.randomUUID();
        int outbox=f.source.count("order_sync_outbox");String trigger="fault_publish_"+UUID.randomUUID().toString().replace("-","");
        jdbc.execute("CREATE TRIGGER "+trigger+" BEFORE INSERT ON operation_event FOR EACH ROW BEGIN "+
            "IF NEW.event_type='ORDER_PUBLISHED' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fault-publication-final-audit'; END IF; END");
        try {assertThat(h.web.post("/orders/"+f.source.order+"/publish",body,key).statusCode()).isEqualTo(500);}
        finally {jdbc.execute("DROP TRIGGER "+trigger);}
        assertThat(f.source.count("order_publication")).isEqualTo(1);assertThat(f.source.count("order_sync_outbox")).isEqualTo(outbox);
        assertThat(f.orders.detail(f.source.order).at("/order/status").asText()).isEqualTo("DRAFT");
        var success=h.data(h.web.post("/orders/"+f.source.order+"/publish",body,key),200);
        assertThat(h.data(h.web.post("/orders/"+f.source.order+"/publish",body,key),200)).isEqualTo(success);
        assertThat(f.source.count("order_publication")).isEqualTo(2);
    }

    @Test void finalAssignmentOutboxFailureRollsBackCredentialAndOriginalKeyCanRetry() throws Exception {
        var h=withdrawn();var f=h.f;f.publish(UUID.randomUUID().toString());var tokens=login(h);
        var body=f.source.pda.claimBody().put("expectedVersion",f.version());var key=UUID.randomUUID();
        String authorizationCount="SELECT COUNT(*) FROM offline_authorization a INNER JOIN order_assignment o ON o.id=a.assignment_id WHERE o.order_id=?";
        byte[] order=Ids.bytes(UUID.fromString(f.source.order));
        int authorizations=jdbc.queryForObject(authorizationCount,Integer.class,order),outbox=f.source.count("order_sync_outbox");
        String trigger="fault_claim_"+UUID.randomUUID().toString().replace("-","");
        jdbc.execute("CREATE TRIGGER "+trigger+" BEFORE INSERT ON order_sync_outbox FOR EACH ROW BEGIN "+
            "IF NEW.event_type='ORDER_ASSIGNED' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fault-assignment-final-outbox'; END IF; END");
        try {assertThat(h.post("/pda/orders/"+f.source.order+"/assignments",body,tokens,key).statusCode()).isEqualTo(500);}
        finally {jdbc.execute("DROP TRIGGER "+trigger);}
        assertThat(f.source.count("order_assignment")).isEqualTo(1);
        assertThat(jdbc.queryForObject(authorizationCount,Integer.class,order)).isEqualTo(authorizations);assertThat(f.source.count("order_sync_outbox")).isEqualTo(outbox);
        var success=h.data(h.post("/pda/orders/"+f.source.order+"/assignments",body,tokens,key),200);
        assertThat(h.data(h.post("/pda/orders/"+f.source.order+"/assignments",body,tokens,key),200)).isEqualTo(success);
        assertThat(f.source.count("order_assignment")).isEqualTo(2);assertThat(f.source.count("dispatch_record")).isZero();
    }
}
