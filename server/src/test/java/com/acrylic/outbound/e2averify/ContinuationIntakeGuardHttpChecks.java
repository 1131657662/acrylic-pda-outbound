package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.http.HttpResponse;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

/** A next-round authorization must never mint an unrelated initial submission. */
final class ContinuationIntakeGuardHttpChecks {
    static void verify(ContinuationReceiptHttpFixture fixture, JsonNode receipt, String stage) throws Exception {
        var decision = receipt.path("businessDecision");
        var body = control(fixture).put("authorizationId", decision.path("newAuthorizationId").asText());
        ((ObjectNode) body.get("source")).put("clientOrderId", decision.path("newClientOrderId").asText());
        var before = counts(fixture); var stock = fixture.database.stock();
        denied(fixture.submit(body, stage + "-target-submit"));
        denied(fixture.abort(body, stage + "-target-abort"));
        ((ObjectNode) body.get("source")).put("clientOrderId", UUID.randomUUID().toString());
        denied(fixture.submit(body, stage + "-other-submit"));
        denied(fixture.abort(body, stage + "-other-abort"));
        assertThat(counts(fixture)).isEqualTo(before);
        assertThat(fixture.database.stock()).isEqualTo(stock);
    }

    static void ordinaryAuthorizationStillRecoversAnAbort(ContinuationReceiptHttpFixture fixture) throws Exception {
        var body = control(fixture).put("authorizationId", fixture.database.seed.get("authorizationId"));
        var response = fixture.abort(body, "ordinary-abort");
        assertThat(response.statusCode()).as(new String(response.body())).isEqualTo(200);
        assertThat(SubmissionJson.parse(response.body()).path("data").path("status").asText()).isEqualTo("ABORTED");
        var replay = fixture.abort(body, "ordinary-abort-replay");
        assertThat(replay.statusCode()).isEqualTo(200);
        assertThat(SubmissionJson.parse(replay.body()).path("data")).isEqualTo(SubmissionJson.parse(response.body()).path("data"));
    }

    private static ObjectNode control(ContinuationReceiptHttpFixture fixture) {
        var body = SubmissionJson.object().put("submissionId", UUID.randomUUID().toString())
            .put("freezeDeviceId", fixture.database.seed.get("deviceId")).put("workRound", "1")
            .put("sourceRecoveryEpoch", "1").putNull("assignmentId").putNull("continuationId")
            .put("reason", "Synthetic intake isolation check");
        body.putObject("source").put("kind", "TEMP").put("creatorDeviceId", fixture.database.seed.get("deviceId"))
            .put("clientOrderId", UUID.randomUUID().toString());
        return body;
    }

    private static void denied(HttpResponse<byte[]> response) {
        assertThat(response.statusCode()).as(new String(response.body())).isEqualTo(409);
        assertThat(SubmissionJson.parse(response.body()).path("code").asText()).isEqualTo("TEMP_CONTINUATION_INTAKE_UNAVAILABLE");
    }

    private static java.util.Map<String,Object> counts(ContinuationReceiptHttpFixture fixture) {
        return fixture.database.jdbc.queryForMap("""
            SELECT (SELECT COUNT(*) FROM temporary_work_origin) origins,
              (SELECT COUNT(*) FROM submission_scope) scopes,(SELECT COUNT(*) FROM submission) submissions,
              (SELECT COUNT(*) FROM temporary_authorization) authorizations,
              (SELECT COUNT(*) FROM operation_event) audits
            """);
    }
}
