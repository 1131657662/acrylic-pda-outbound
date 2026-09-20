package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class AuditArchiveFixture {
    final SubmissionFixture f;
    AuditArchiveFixture(SubmissionFixture f) { this.f=f; }
    void upload() throws Exception {
        var body=SubmissionJson.object().put("originDeviceId",f.tokens.get("deviceId").asText());body.set("events",f.body.get("events"));
        f.pda.data(post("/api/v1/pda/audit/events").header("X-Server-Recovery-Epoch","1").contentType("application/json").content(body.toString()),f.tokens);
    }
    ObjectNode payload(int after,int through) {
        var body=SubmissionJson.object().put("originDeviceId",f.tokens.get("deviceId").asText()).put("afterSeq",""+after).put("throughSeq",""+through);
        var events=body.putArray("events");for(int i=after;i<through;i++)events.add(f.body.at("/events/"+i));return body;
    }
    ObjectNode body(int after,int through) {
        return SubmissionJson.object().put("originDeviceId",f.tokens.get("deviceId").asText()).put("afterSeq",""+after)
            .put("throughSeq",""+through).put("payloadSha256",SubmissionJson.sha(payload(after,through)));
    }
    MockHttpServletRequestBuilder request(ObjectNode body) { return post("/api/v1/pda/audit/archives").header("X-Server-Recovery-Epoch","1").contentType("application/json").content(body.toString()); }
    JsonNode confirm(int after,int through) throws Exception { return f.pda.data(request(body(after,through)),f.tokens); }
    MockHttpServletRequestBuilder restoring(JsonNode proof,String epoch) {
        var body=SubmissionJson.object();body.set("archive",proof);
        return post("/api/v1/pda/audit/archives/restore").header("X-Server-Recovery-Epoch",epoch).contentType("application/json").content(body.toString());
    }
    ObjectNode compact(RecallFixture recall,JsonNode... proofs) {
        var body=recall.body.deepCopy();int through=0;var archives=body.putArray("auditArchives");
        for(var proof:proofs) { archives.add(proof);through=Integer.parseInt(proof.get("throughSeq").asText()); }
        var all=body.withArray("auditEvents").deepCopy();var tail=body.putArray("auditEvents");
        for(int i=through;i<all.size();i++)tail.add(all.get(i));
        ((ObjectNode)body.at("/auditCoverage/0")).put("afterSeq",""+through);return body;
    }
}
