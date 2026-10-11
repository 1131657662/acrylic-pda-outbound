package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class FaultWithdrawalHttpIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired WebApplicationContext context;
    private FaultWithdrawalHttp fixture(boolean shortPlan) throws Exception {
        return new FaultWithdrawalHttp(context,new FaultRestartFixture(mvc,json,jdbc,passwords,shortPlan));
    }

    @Test void lostResponseReplayRepublishAndNewDeviceUseRealHttp() throws Exception {
        var h=fixture(false);var f=h.f;var command=f.withdrawal(false);UUID key=UUID.randomUUID();
        var first=h.data(h.withdraw(command,key),200);var again=h.data(h.withdraw(command,key),200);
        assertThat(again).isEqualTo(first);
        assertThat(again.path("signedReceipt").asText()).isNotBlank().isEqualTo(first.path("signedReceipt").asText());
        assertThat(h.withdraw(command.deepCopy().put("reason","异文"),key).statusCode()).isEqualTo(409);
        var publish=f.orders.release(f.version(),"1");UUID publishKey=UUID.randomUUID();
        var published=h.web.post("/orders/"+f.source.order+"/publish",publish,publishKey);
        assertThat(published.statusCode()).isEqualTo(200);
        assertThat(h.data(h.web.post("/orders/"+f.source.order+"/publish",publish,publishKey),200)).isEqualTo(h.data(published,200));
        h.claimAndDownload();var body=f.body();var result=h.data(h.submit(body,f.tokens),200);
        assertThat(result.path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(h.data(h.submit(body,f.tokens),200)).isEqualTo(result);
        assertThat(h.submit(f.source.body,f.source.tokens).statusCode()).isEqualTo(409);
        assertThat(h.get("/pda/assignments/"+f.source.assignment.path("id").asText()+"/content/plan?bundleId="+UUID.randomUUID(),f.source.tokens).statusCode()).isEqualTo(409);
        assertThat(f.source.count("dispatch_record")).isEqualTo(2);assertThat(f.source.count("inventory_ledger")).isEqualTo(2);
    }

    @Test void competingOriginalSubmitAndWithdrawalHaveOnlyOneEffectiveResult() throws Exception {
        var h=fixture(false);var f=h.f;var command=f.withdrawal(false);var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var withdrawal=pool.submit(()->{start.await();return h.withdraw(command,UUID.randomUUID());});
            var submit=pool.submit(()->{start.await();return h.submit(f.source.body,f.source.tokens);});start.countDown();
            var w=withdrawal.get(45,TimeUnit.SECONDS);var s=submit.get(45,TimeUnit.SECONDS);
            if(w.statusCode()==200) {
                assertThat(s.statusCode()).isEqualTo(409);assertThat(f.source.count("dispatch_record")).isZero();
                assertThat(f.orders.detail(f.source.order).at("/order/status").asText()).isEqualTo("DRAFT");
            } else {
                assertThat(w.statusCode()).isEqualTo(409);assertThat(h.data(s,200).path("status").asText()).isEqualTo("SUCCEEDED");
                assertThat(f.source.count("dispatch_record")).isEqualTo(2);
            }
        }
    }

    @Test void competingApprovalAndPhysicalWithdrawalCannotBothChangeStock() throws Exception {
        var h=fixture(true);var f=h.f;f.source.submit();var command=f.withdrawal(true);var start=new CountDownLatch(1);
        var review=SubmissionJson.object().put("decision","APPROVE").put("reason","隔离测试实物核验")
            .put("payloadSha256",SubmissionJson.sha(f.source.body));
        try(var pool=Executors.newFixedThreadPool(2)) {
            var withdrawal=pool.submit(()->{start.await();return h.withdraw(command,UUID.randomUUID());});
            var approval=pool.submit(()->{start.await();return h.web.post("/submissions/"+f.source.id()+"/review",review,UUID.randomUUID());});start.countDown();
            var w=withdrawal.get(45,TimeUnit.SECONDS);var a=approval.get(45,TimeUnit.SECONDS);
            if(w.statusCode()==200) {
                assertThat(a.statusCode()).isEqualTo(409);assertThat(f.source.count("dispatch_record")).isZero();assertThat(f.source.count("bale_commitment")).isZero();
                assertThat(h.data(h.get("/pda/submissions/"+f.source.id(),f.source.tokens),200).path("status").asText()).isEqualTo("ABORTED");
            } else {
                assertThat(w.statusCode()).isEqualTo(409);assertThat(h.data(a,200).path("status").asText()).isEqualTo("SUCCEEDED");
                assertThat(f.source.count("dispatch_record")).isEqualTo(2);assertThat(f.source.count("inventory_ledger")).isEqualTo(2);
            }
        }
    }
    @Test void unansweredRecallCanBeWithdrawnWithoutInventingAStopOrOverwritingTheOriginalRequest() throws Exception {
        var h=fixture(false);var f=h.f;
        var requested=h.data(h.web.post("/orders/"+f.source.order+"/change-requests",SubmissionJson.object()
            .put("type","RECALL").put("expectedVersion",f.version()).put("reason","原申请：等待原机停止"),UUID.randomUUID()),200);
        var original=jdbc.queryForMap("SELECT reason,BIN_TO_UUID(requested_by) AS requested_by,created_at FROM order_change_request WHERE id=?",
            com.acrylic.outbound.common.Ids.bytes(UUID.fromString(requested.path("changeRequestId").asText())));
        var command=f.withdrawal(false);var key=UUID.randomUUID();var receipt=h.data(h.withdraw(command,key),200);
        assertThat(receipt.path("changeRequestId")).isEqualTo(requested.path("changeRequestId"));
        assertThat(receipt.path("originalDeviceStopReceived").asBoolean()).isFalse();
        assertThat(h.data(h.withdraw(command,key),200)).isEqualTo(receipt);
        assertThat(jdbc.queryForMap("SELECT reason,BIN_TO_UUID(requested_by) AS requested_by,created_at FROM order_change_request WHERE id=?",
            com.acrylic.outbound.common.Ids.bytes(UUID.fromString(receipt.path("changeRequestId").asText())))).isEqualTo(original);
        assertThat(f.source.count("order_change_request")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recovery_package p JOIN order_change_request r ON r.id=p.request_id WHERE r.order_id=?",
            Integer.class,com.acrylic.outbound.common.Ids.bytes(UUID.fromString(f.source.order)))).isZero();
        assertThat(h.submit(f.source.body,f.source.tokens).statusCode()).isEqualTo(409);
        f.publish(UUID.randomUUID().toString());h.claimAndDownload();
        assertThat(h.data(h.submit(f.body(),f.tokens),200).path("status").asText()).isEqualTo("SUCCEEDED");
    }
}
