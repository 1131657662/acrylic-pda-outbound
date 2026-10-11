package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Synthetic records exercise restart accounting; they do not prove Android capture. */
final class FaultRestartFixture {
    final SubmissionFixture source;
    final OrderTestDriver orders;
    JsonNode assignment,tokens;
    FaultRestartFixture(MockMvc mvc,com.fasterxml.jackson.databind.ObjectMapper json,JdbcTemplate jdbc,PasswordEncoder passwords,boolean shortPlan) throws Exception {
        source=new SubmissionFixture(new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN")),jdbc,true,shortPlan);
        orders=new OrderTestDriver(source.web);
    }
    ObjectNode withdrawal(boolean physical) throws Exception {
        var body=SubmissionJson.object().put("type","FAULT_WITHDRAWAL").put("reason","坏机离线，撤回后重新核对")
            .put("expectedVersion",version());
        if(physical)body.put("physicalChecked",true).put("physicalEvidence","隔离测试合成实物凭据：两包卸回原货位，主管逐包核验");
        return body;
    }
    JsonNode withdraw(ObjectNode body,String key) throws Exception {
        return source.web.data(orders.command("/"+source.order+"/change-requests",body,key));
    }
    String version() throws Exception { return orders.detail(source.order).at("/order/rowVersion").asText(); }
    JsonNode publish(String key) throws Exception {
        return source.web.data(orders.command("/"+source.order+"/publish",orders.release(version(),"1"),key));
    }
    void claim() throws Exception {
        tokens=source.pda.login(source.pda.register());
        assignment=source.pda.data(source.pda.command("/orders/"+source.order+"/assignments",
            source.pda.claimBody().put("expectedVersion",version())).header("Idempotency-Key",UUID.randomUUID().toString())
            .header("X-Server-Recovery-Epoch","1"),tokens);
        source.jdbc.update("UPDATE order_assignment SET download_ack_at=UTC_TIMESTAMP(3) WHERE id=?",Ids.bytes(UUID.fromString(assignment.path("id").asText())));
    }
    ObjectNode body() {
        var b=source.body.deepCopy();String time=Instant.parse(assignment.path("issuedAt").asText()).plusMillis(100).toString();
        b.put("submissionId",UUID.randomUUID().toString()).put("assignmentId",assignment.path("id").asText())
            .put("assignmentEpoch",assignment.path("epoch").asText()).put("orderRevision",assignment.path("revision").asInt())
            .put("freezeDeviceId",tokens.path("deviceId").asText()).put("workRound",assignment.path("workRound").asText())
            .put("startedAt",time).put("closedAt",time);
        var ids=new HashMap<String,String>();
        b.withArray("captures").forEach(c->{
            String id=UUID.randomUUID().toString();ids.put(c.path("id").asText(),id);var row=(ObjectNode)c;
            row.put("id",id).put("assignmentId",assignment.path("id").asText()).put("originDeviceId",tokens.path("deviceId").asText())
                .put("operatorId",tokens.path("userId").asText()).put("authorizationId",assignment.path("offlineAuthorizationId").asText())
                .put("assignmentEpoch",assignment.path("epoch").asText()).put("orderRevision",assignment.path("revision").asInt()).put("confirmedAt",time);
        });
        b.withArray("scans").forEach(s->((ObjectNode)s).put("captureId",ids.get(s.path("captureId").asText())).put("savedAt",time));
        b.withArray("events").forEach(e->event((ObjectNode)e,b,ids));
        return b;
    }
    private void event(ObjectNode event,ObjectNode body,Map<String,String> captures) {
        event.put("id",UUID.randomUUID().toString()).put("assignmentId",assignment.path("id").asText())
            .put("originDeviceId",tokens.path("deviceId").asText()).put("operatorId",tokens.path("userId").asText());
        if(event.hasNonNull("captureId"))event.put("captureId",captures.get(event.path("captureId").asText()));
        if(event.path("type").asText().equals("FROZEN"))event.put("detail",body.path("submissionId").asText());
    }
    JsonNode submit(ObjectNode body) throws Exception {
        return source.pda.data(source.request(body),tokens);
    }
    JsonNode approve(ObjectNode body) throws Exception {
        return source.web.data(post("/api/v1/submissions/"+body.path("submissionId").asText()+"/review").contentType("application/json")
            .content(SubmissionJson.object().put("decision","APPROVE").put("reason","隔离测试主管核对短装")
                .put("payloadSha256",SubmissionJson.sha(body)).toString()));
    }
}
