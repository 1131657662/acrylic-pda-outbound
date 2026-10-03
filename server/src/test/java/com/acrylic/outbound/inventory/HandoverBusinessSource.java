package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.assertj.core.api.Assertions.assertThat;

/** Shared business prerequisites: original captures stay complete, even when every scan was legitimately voided. */
public final class HandoverBusinessSource {
    private final RecallFixture recall;
    public final UUID warehouse, order, device, operator, assignment, scope, submission, bale;
    public final JsonNode scan;
    public final String username;
    private final int retained;

    public HandoverBusinessSource(WebApplicationContext app) { this(app, 1); }
    public HandoverBusinessSource(WebApplicationContext app, int retained) {
        if (retained < 0 || retained > 1) throw new IllegalArgumentException("Only zero or one retained test scan");
        this.retained = retained;
        try {
            var jdbc = app.getBean(JdbcTemplate.class);
            var mvc = MockMvcBuilders.webAppContextSetup(app).addFilters(app.getBean(RequestIdFilter.class)).apply(springSecurity()).build();
            var identity = IdentityFixture.create(jdbc, app.getBean(PasswordEncoder.class), "WAREHOUSE_ADMIN");
            jdbc.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code='SUPERVISOR'", Ids.bytes(identity.user().id()));
            var fixture = new SubmissionFixture(new PublicationDriver(mvc, app.getBean(ObjectMapper.class), identity), jdbc, true, false);
            voidScans(fixture.body); recall = new RecallFixture(fixture); recall.start(false);
            if (retained == 0) assertThat(fixture.abort().path("status").asText()).isEqualTo("ABORTED");
            else assertThat(fixture.submit().path("status").asText()).isEqualTo("REVIEW_REQUIRED");
            recall.upload(); username = identity.user().username();
            warehouse = identity.warehouse(); order = UUID.fromString(fixture.order); submission = UUID.fromString(fixture.id());
            device = UUID.fromString(fixture.tokens.path("deviceId").asText()); operator = identity.user().id();
            assignment = UUID.fromString(fixture.assignment.path("id").asText());
            scope = UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(scope_id) FROM submission WHERE id=?", String.class, Ids.bytes(submission)));
            scan = recall.body.path("scans").get(0); bale = UUID.fromString(scan.path("baleId").asText());
        } catch (Exception failure) { throw new IllegalStateException("Cannot prepare authentic handover source", failure); }
    }
    private void voidScans(ObjectNode body) {
        var scans = body.withArray("scans"); var events = body.withArray("events");
        var frozen = (ObjectNode) events.remove(events.size() - 1); int seq = events.size() + 1;
        for (int i = retained; i < scans.size(); i++) {
            var scan = (ObjectNode) scans.get(i); scan.put("status", "VOID");
            events.add(frozen.deepCopy().put("id", UUID.randomUUID().toString()).put("type", "VOID")
                .put("localSeq", Integer.toString(seq++)).put("captureId", scan.path("captureId").asText()).put("detail", "测试实物核对撤销"));
        }
        events.add(frozen.put("localSeq", Integer.toString(seq)));
        body.put("expectedCount", Integer.toString(retained)).put("expectedWeightGrams", retained == 0 ? "0" : scans.get(0).path("weightGrams").asText())
            .put("shortShipmentReason", "测试短装，原始输入全部保留");
        ((ObjectNode) body.path("captureSummary")).put("saved", Integer.toString(retained)).put("voided", Integer.toString(2 - retained));
    }
    public void stopAndAbort(UUID kase) {
        try {
            var body = recall.ackBody().put("commandId", UUID.randomUUID().toString()).put("expectedCaseVersion", "1")
                .put("changeRequestId", recall.recall.path("changeRequestId").asText());
            send("/pda/handovers/" + kase + "/stop-ack", body); abortOriginal();
        } catch (Exception failure) { throw new IllegalStateException("Cannot stop and terminalize original handover source", failure); }
    }
    public void abortOriginal() throws Exception {
        assertThat(send("/pda/submissions/" + submission + "/abort", recall.f.abortBody()).path("data").path("status").asText()).isEqualTo("ABORTED");
    }
    public void approveFields(ObjectNode command) {
        command.put("stopState", "DEVICE_ACKED").put("integrityState", "COMPLETE").put("originalResultState", "ABORTED")
            .put("physicalState", "MATCHED").put("inputHighWatermark", recall.body.path("captureHighWatermark").asText());
        var lines = command.putArray("checklist");
        if (retained > 0) lines.addObject().put("baleId", bale.toString()).put("deliveryCode", scan.path("deliveryCode").asText())
            .put("state", "RETAINED").put("dispatchCycle", scan.path("expectedDispatchCycle").asText());
    }
    private JsonNode send(String path, JsonNode body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434/api/v1" + path))
            .timeout(java.time.Duration.ofSeconds(30)).header("Content-Type", "application/json").header("X-Server-Recovery-Epoch", "1")
            .header("Authorization", "Bearer " + recall.f.tokens.path("accessToken").asText())
            .POST(HttpRequest.BodyPublishers.ofString(SubmissionJson.canonical(body))).build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200); return SubmissionJson.parse(response.body());
    }
}