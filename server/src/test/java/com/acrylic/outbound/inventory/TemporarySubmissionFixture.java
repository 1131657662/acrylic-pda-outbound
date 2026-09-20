package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.time.Instant;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

final class TemporarySubmissionFixture {
    final SubmissionFixture f;final TemporaryAuditFixture a;final ObjectNode body;final JsonNode manifest;
    TemporarySubmissionFixture(SubmissionFixture f) throws Exception {
        this.f=f;a=new TemporaryAuditFixture(f);manifest=SubmissionJson.parse(a.authorization.get("manifest").asText());
        body=f.body.deepCopy();body.putNull("assignmentId").put("authorizationId",a.authorization.get("authorizationId").asText())
            .put("authorizationEpoch",manifest.at("/identity/authorizationEpoch").asText()).put("orderRevision","1")
            .put("temporaryNo","TEMP-TEST-001");body.remove("assignmentEpoch");body.putArray("dispositions");
        body.set("source",a.event(1).get("source"));String time=Instant.now().toString();body.put("closedAt",time);
        var plan=body.putObject("temporaryOrder").put("clientOrderId",a.client).put("creatorDeviceId",a.device())
            .put("authorizationId",a.authorization.get("authorizationId").asText()).put("revision","1")
            .put("vehicleNo","临时合成车001").put("containerNo","").put("reason","现场临时作业");
        var lines=plan.putArray("lines");var mappings=new TreeMap<String,JsonNode>();part("mapping").forEach(m->mappings.put(m.get("deliveryCode").asText(),m));
        mappings.values().forEach(m->{var line=SubmissionJson.object().put("plannedCount","1");
            for(String field:List.of("deliveryCode","productId","model","name"))line.set(field,m.get(field));lines.add(line);});
        body.put("planSha256",SubmissionJson.sha(plan));
        body.withArray("captures").forEach(c->{var row=(ObjectNode)c;row.remove(List.of("assignmentId","orderId","assignmentEpoch","warehouseId"));
            row.put("clientOrderId",a.client).put("authorizationId",a.authorization.get("authorizationId").asText()).put("authorizationEpoch","1")
                .put("orderRevision","1").put("confirmedAt",time);});
        body.withArray("scans").forEach(s->((ObjectNode)s).put("savedAt",time));
        body.withArray("events").forEach(e->{var row=(ObjectNode)e;row.remove("assignmentId");row.set("source",body.get("source"));row.put("authorizationId",body.get("authorizationId").asText());});
    }
    JsonNode part(String name) throws Exception {
        byte[] bytes=f.jdbc.queryForObject("SELECT compressed FROM temporary_preparation_part WHERE authorization_id=UUID_TO_BIN(?) AND part_name=?",
            byte[].class,body.get("authorizationId").asText(),name);
        return SubmissionJson.parse(new GZIPInputStream(new ByteArrayInputStream(bytes)).readAllBytes());
    }
    String id() { return body.get("submissionId").asText(); }
    String base() { return "/api/v1/pda/submissions/"+id(); }
    MockHttpServletRequestBuilder request(String path,JsonNode body) { return a.request(path,body); }
    JsonNode submit() throws Exception { return f.pda.data(request("/api/v1/pda/temporary-orders/submissions",body),f.tokens); }
    JsonNode read() throws Exception { return f.pda.data(get(base()),f.tokens); }
    ObjectNode control() {
        var c=SubmissionJson.object();for(String key:List.of("source","assignmentId","continuationId","authorizationId","freezeDeviceId","workRound","sourceRecoveryEpoch"))c.set(key,body.get(key));return c;
    }
    JsonNode abort() throws Exception { return f.pda.data(request(base()+"/abort",control().put("reason","实物占用保留")),f.tokens); }
    int submissions() { return f.jdbc.queryForObject("SELECT COUNT(*) FROM submission WHERE temporary_authorization_id=UUID_TO_BIN(?)",Integer.class,body.get("authorizationId").asText()); }
    int orders() { return f.jdbc.queryForObject("SELECT COUNT(*) FROM submission_scope WHERE creator_device_id=UUID_TO_BIN(?) AND canonical_order_id IS NOT NULL",Integer.class,a.device()); }
}
