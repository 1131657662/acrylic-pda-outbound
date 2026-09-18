package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class PcExceptionFixture {
    final PcFixture pc;
    PcExceptionFixture(PublicationDriver api) throws Exception { this(api,1); }
    PcExceptionFixture(PublicationDriver api,int recordedPerCode) throws Exception {
        int perCode=Math.max(4,recordedPerCode);pc=new PcFixture(api,false,Math.max(3,recordedPerCode),perCode);pc.start();
        for(int code=0;code<3;code++)for(int index=0;index<recordedPerCode;index++)pc.add(code*perCode+index);
    }
    JsonNode view() throws Exception { return pc.api.data(get(pc.path("exception"))); }
    ObjectNode base() throws Exception { return pc.api.json().createObjectNode().put("expectedVersion",pc.view().at("/work/version").asText()).put("physicalConfirmed",true); }
    ObjectNode stopBody() throws Exception { return base().put("reason","现场暂停并逐包核对，准备交接"); }
    void stop() throws Exception { pc.send("exception/stop",stopBody()); }
    ObjectNode plan(String target,String... actions) throws Exception {
        var v=view();var body=base().put("caseId",v.at("/stopped/id").asText()).put("snapshotSha256",v.at("/stopped/snapshotSha256").asText())
            .put("targetUsername",target).put("outcome","CONTINUE").put("reason","已按车号核对全部包，安排实物交接");
        var lines=body.putArray("lines");int i=0;
        for(var entry:v.at("/stopped/snapshot/entries"))if(entry.path("status").asText().equals("ACTIVE")) {
            String action=i<actions.length?actions[i]:"RETAIN";i++;
            lines.addObject().put("entryId",entry.path("id").asText()).put("expectedCycle",entry.path("cycle").asLong())
                .put("action",action).put("physicalEvidence",action.equals("RETAIN")?"":"A区已卸下核对，凭据 PC-TEST");
        }
        return body;
    }
    void propose(ObjectNode body) throws Exception { pc.send("exception/plan",body); }
    ObjectNode decision(String decision) throws Exception {
        var p=view().at("/plans/0");return base().put("planId",p.path("id").asText()).put("payloadSha256",p.path("payloadSha256").asText())
            .put("decision",decision).put("reason","主管已复核实物、人员和位置");
    }
    void approve() throws Exception { pc.send("exception/review",decision("APPROVE")); }
    ObjectNode application() throws Exception {
        var p=view().at("/plans/0");return base().put("planId",p.path("id").asText()).put("payloadSha256",p.path("payloadSha256").asText()).put("physicalEvidence","接续人员逐包核对本车保留及已卸下包，确认交接");
    }
    void apply() throws Exception { pc.send("exception/apply",application()); }
    PublicationDriver user(JdbcTemplate jdbc,PasswordEncoder passwords,String role) {
        var created=IdentityFixture.create(jdbc,passwords,role);
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)",Ids.bytes(created.user().id()),Ids.bytes(pc.api.identity().warehouse()));
        return new PublicationDriver(pc.api.mvc(),pc.api.json(),new IdentityFixture(created.user(),pc.api.identity().warehouse()));
    }
    long held(JdbcTemplate jdbc,String state) { return jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE order_id=? AND state=?",Long.class,Ids.bytes(UUID.fromString(pc.order)),state); }
}
