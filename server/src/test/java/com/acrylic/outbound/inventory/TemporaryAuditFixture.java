package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.UUID;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

final class TemporaryAuditFixture {
    final SubmissionFixture formal;final JsonNode authorization;final String client=UUID.randomUUID().toString();
    TemporaryAuditFixture(SubmissionFixture formal) throws Exception {
        this.formal=formal;
        formal.jdbc.update("INSERT IGNORE INTO user_role SELECT UUID_TO_BIN(?),id FROM role WHERE code='TEMPORARY_ORDER_CREATOR'",formal.tokens.get("userId").asText());
        // Fixture-only admission: preserve the already-issued formal session for mixed evidence.
        formal.jdbc.update("UPDATE device SET temporary_order_enabled=1 WHERE id=UUID_TO_BIN(?)",device());
        authorization=prepare();acknowledge(authorization);
    }
    String device() { return formal.tokens.get("deviceId").asText(); }
    JsonNode prepare() throws Exception {
        var b=formal.pda.data(get(TemporaryFixture.BASE+"/baseline").header("X-Server-Recovery-Epoch","1"),formal.tokens);
        var request=SubmissionJson.object().put("inventorySnapshotId",b.get("inventorySnapshotId").asText())
            .put("mappingRevision",b.get("mappingRevision").asText()).put("policyVersion",b.at("/policy/version").asText());
        return formal.pda.data(request(TemporaryFixture.BASE,request).header("Idempotency-Key",UUID.randomUUID().toString()),formal.tokens);
    }
    void acknowledge(JsonNode receipt) throws Exception {
        var manifest=SubmissionJson.parse(receipt.get("manifest").asText());
        var body=SubmissionJson.object().put("bundleId",receipt.get("bundleId").asText()).put("manifestSha256",receipt.get("manifestSha256").asText())
            .put("authorizationEpoch",manifest.at("/identity/authorizationEpoch").asText()).put("activatedAt",Instant.now().toString());
        formal.pda.data(request(TemporaryFixture.BASE+"/"+receipt.get("authorizationId").asText()+"/download-ack",body)
            .header("Idempotency-Key",UUID.randomUUID().toString()),formal.tokens);
    }
    ObjectNode event(long sequence) {
        var row=SubmissionJson.object().put("id",UUID.randomUUID().toString()).putNull("captureId").put("originDeviceId",device())
            .put("operatorId",formal.tokens.get("userId").asText()).put("localSeq",Long.toString(sequence))
            .put("type","DRAFT_CREATED").put("detail","").put("wallMs","1000").put("elapsedMs","1000").put("boot",1)
            .put("authorizationId",authorization.get("authorizationId").asText());
        row.putObject("source").put("kind","TEMP").put("creatorDeviceId",device()).put("clientOrderId",client);return row;
    }
    ObjectNode batch(JsonNode... events) {
        var body=SubmissionJson.object().put("originDeviceId",device());var rows=body.putArray("events");
        for(var event:events)rows.add(event);return body;
    }
    ObjectNode mixed(JsonNode temporary) { var b=batch();b.withArray("events").addAll(formal.body.withArray("events"));b.withArray("events").add(temporary);return b; }
    MockHttpServletRequestBuilder upload(JsonNode body) { return request("/api/v1/pda/audit/events",body); }
    JsonNode send(JsonNode body) throws Exception { return formal.pda.data(upload(body),formal.tokens); }
    MockHttpServletRequestBuilder request(String path,JsonNode body) { return post(path).header("X-Server-Recovery-Epoch","1").contentType("application/json").content(body.toString()); }
    int count(String table) { return formal.jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE "+(table.equals("temporary_work_origin")?"creator_device_id":"origin_device_id")+"=UUID_TO_BIN(?)",Integer.class,device()); }
    JsonNode archive(ObjectNode mixed) throws Exception {
        var payload=mixed.deepCopy().put("afterSeq","0").put("throughSeq","8");
        var body=SubmissionJson.object().put("originDeviceId",device()).put("afterSeq","0").put("throughSeq","8").put("payloadSha256",SubmissionJson.sha(payload));
        return formal.pda.data(request("/api/v1/pda/audit/archives",body),formal.tokens);
    }
}
