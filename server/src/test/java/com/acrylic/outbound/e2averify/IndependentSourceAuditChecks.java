package com.acrylic.outbound.e2averify;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

/** Verifies stored origin columns against original HTTP audit bytes; no origin is inferred from the enclosing TEMP set. */
final class IndependentSourceAuditChecks {
    private final E2aCrossSourceHttp http;
    private final E2aCrossSourceHttpSetup setup;
    private int requests;
    IndependentSourceAuditChecks(E2aCrossSourceHttp http,E2aCrossSourceHttpSetup setup) { this.http=http;this.setup=setup; }

    void verify() throws Exception {
        var events=new ArrayList<>(setup.extraAudits);events.add(setup.audit);
        var before=rows();
        assertThat(before).hasSize(events.size());
        for(var event:events) stored(event,before);
        assertThat(events.stream().filter(e->e.has("assignmentId")).count()).isPositive();
        assertThat(events.stream().filter(e->e.has("source")).count()).isPositive();
        upload(events,200);assertThat(rows()).isEqualTo(before);
        var formal=events.stream().filter(e->e.has("assignmentId")).findFirst().orElseThrow();
        var relabelled=formal.deepCopy();relabelled.remove("assignmentId");
        relabelled.put("authorizationId",text(setup.audit,"authorizationId")).set("source",setup.audit.path("source"));
        assertThat(upload(List.of(relabelled),409).path("code").asText()).isEqualTo("AUDIT_CONTENT_CONFLICT");
        var duplicate=formal.deepCopy().put("id",UUID.randomUUID().toString());
        assertThat(upload(List.of(duplicate),409).path("code").asText()).isEqualTo("AUDIT_CONTENT_CONFLICT");
        assertThat(rows()).isEqualTo(before);
        Files.writeString(http.evidence.resolve("separate-audit-rows.json"),new com.fasterxml.jackson.databind.ObjectMapper()
            .writerWithDefaultPrettyPrinter().writeValueAsString(before));
    }

    private List<Map<String,Object>> rows() {
        return setup.fixture.jdbc.queryForList("""
            SELECT BIN_TO_UUID(id) id,local_seq,BIN_TO_UUID(assignment_id) assignment,
              BIN_TO_UUID(temporary_authorization_id) authorization,BIN_TO_UUID(client_order_id) client,record_sha256,payload
            FROM pda_audit_event WHERE origin_device_id=UUID_TO_BIN(?) ORDER BY local_seq
            """,setup.fixture.seed.get("deviceId"));
    }

    private void stored(ObjectNode event,List<Map<String,Object>> rows) {
        var row=rows.stream().filter(r->r.get("id").equals(text(event,"id"))).findFirst().orElseThrow();
        assertThat(row.get("record_sha256")).isEqualTo(sha(event));
        assertThat(canonical(parse((String)row.get("payload")))).isEqualTo(canonical(event));
        if(event.has("assignmentId")) {
            assertThat(row.get("assignment")).isEqualTo(text(event,"assignmentId"));
            assertThat(row.get("authorization")).isNull();assertThat(row.get("client")).isNull();
        } else {
            assertThat(row.get("assignment")).isNull();assertThat(row.get("authorization")).isEqualTo(text(event,"authorizationId"));
            assertThat(row.get("client")).isEqualTo(text(event.path("source"),"clientOrderId"));
        }
    }

    private com.fasterxml.jackson.databind.JsonNode upload(List<ObjectNode> events,int status) throws Exception {
        var body=object().put("originDeviceId",setup.fixture.seed.get("deviceId"));events.forEach(body.putArray("events")::add);
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434/api/v1/pda/audit/events"))
            .header("Authorization","Bearer "+setup.token).header("X-Server-Recovery-Epoch","1").header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(canonical(body))).build();
        var response=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofByteArray());
        Files.write(http.evidence.resolve("independent-audit-"+(++requests)+".json"),response.body(),StandardOpenOption.CREATE_NEW);
        assertThat(response.statusCode()).as(new String(response.body())).isEqualTo(status);return parse(response.body());
    }
}
