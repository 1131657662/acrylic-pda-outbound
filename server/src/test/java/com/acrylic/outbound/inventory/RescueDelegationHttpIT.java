package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
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
    properties={"server.port=19434","outbound.rescue.delegation-enabled=true"}) @AutoConfigureMockMvc
class RescueDelegationHttpIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;@Autowired WebApplicationContext context;
    private RescueHttp fixture() throws Exception { return new RescueHttp(context,new RescueFixture(mvc,json,jdbc,passwords)); }

    @Test void lostResponsesReturnExactOriginalAuthorizationAndRevocationOverHttp() throws Exception {
        var h=fixture();var f=h.f;var input=f.signed(f.document);UUID key=UUID.fromString(f.key);
        var request=h.data(h.post("/pda/rescue-requests",input,key),200);
        assertThat(h.data(h.post("/pda/rescue-requests",input,key),200)).isEqualTo(request);
        String id=request.path("requestId").asText();UUID approvalKey=UUID.randomUUID();var approval=f.approval();
        var grant=h.data(h.approve(id,approval,approvalKey),200);
        assertThat(h.data(h.approve(id,approval,approvalKey),200)).isEqualTo(grant);
        var material=h.get("/pda/rescue-requests/"+id);
        assertThat(material.headers().firstValue("Cache-Control")).hasValue("no-store");
        assertThat(h.data(material,200).path("authorization")).isEqualTo(grant);
        var revoke=SubmissionJson.object().put("reason","结束救援");UUID revokeKey=UUID.randomUUID();String gid=grant.path("grantId").asText();
        var revoked=h.data(h.revoke(gid,revoke,revokeKey),200);
        assertThat(h.data(h.revoke(gid,revoke,revokeKey),200)).isEqualTo(revoked);
        assertThat(h.revoke(gid,revoke.deepCopy().put("reason","不同撤销理由"),revokeKey).statusCode()).isEqualTo(409);
        var result=h.data(h.get("/pda/rescue-requests/"+id),200);
        assertThat(result.path("status").asText()).isEqualTo("REVOKED");assertThat(result.path("revocation")).isEqualTo(revoked);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_event WHERE actor_id=? AND event_type IN ('rescue.approve','rescue.revoke')",
            Integer.class,Ids.bytes(f.supervisor.identity().user().id()))).isEqualTo(2);
    }

    @Test void concurrentSameKeyApprovalsHaveOneDurableGrant() throws Exception {
        var h=fixture();String id=h.create().path("requestId").asText();UUID key=UUID.randomUUID();var body=h.f.approval();
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->{start.await();return h.approve(id,body,key);});
            var second=pool.submit(()->{start.await();return h.approve(id,body,key);});start.countDown();
            assertThat(h.data(first.get(45,TimeUnit.SECONDS),200)).isEqualTo(h.data(second.get(45,TimeUnit.SECONDS),200));
        }
        assertThat(h.f.rows("rescue_grant","request_id",id)).isEqualTo(1);
    }

    @Test void supervisorCanRevokeAfterOriginalDeviceIsDisabled() throws Exception {
        var h=fixture();String id=h.create().path("requestId").asText();var grant=h.data(h.approve(id,h.f.approval(),UUID.randomUUID()),200);
        h.f.pda.setStatus(h.f.device,"1","DISABLED");
        var revoked=h.data(h.revoke(grant.path("grantId").asText(),SubmissionJson.object().put("reason","设备停用后撤销原只读救援"),UUID.randomUUID()),200);
        assertThat(revoked.path("signedRevocation").asText()).isNotBlank();
        assertThat(h.data(h.supervisor.get("/rescue-requests/"+id),200).path("status").asText()).isEqualTo("REVOKED");
        assertThat(h.get("/pda/rescue-requests/"+id).statusCode()).isEqualTo(401);
    }

    @Test void finalIdempotencyFailureLeavesNoRevocationAndExactOriginalKeyCanRetry() throws Exception {
        var h=fixture();String id=h.create().path("requestId").asText();var grant=h.data(h.approve(id,h.f.approval(),UUID.randomUUID()),200);
        String gid=grant.path("grantId").asText(),trigger="rescue_tail_"+UUID.randomUUID().toString().replace("-","");
        UUID key=UUID.randomUUID();var body=SubmissionJson.object().put("reason","末写回滚后仍用原键");
        jdbc.execute("CREATE TRIGGER "+trigger+" BEFORE INSERT ON api_idempotency_request FOR EACH ROW BEGIN "+
            "IF NEW.route_scope='rescue.revoke' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='rescue-last-write'; END IF; END");
        try { assertThat(h.revoke(gid,body,key).statusCode()).isEqualTo(500); }
        finally { jdbc.execute("DROP TRIGGER "+trigger); }
        assertThat(h.f.rows("rescue_revocation","grant_id",gid)).isZero();
        assertThat(h.data(h.get("/pda/rescue-requests/"+id),200).path("status").asText()).isEqualTo("AUTHORIZED");
        assertThat(h.data(h.revoke(gid,body,key),200).path("signedRevocation").asText()).isNotBlank();
    }
}
