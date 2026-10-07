package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Synthetic target login; the Android recipient itself prepares and persists all live HTTP evidence. */
public final class HandoverTemporaryAndroidBridge {
    private final HandoverTemporaryTargetFixture f;
    private final Path directory;

    public HandoverTemporaryAndroidBridge(WebApplicationContext app, HandoverTemporaryApprovalChecks approval, Path attempt) throws Exception {
        f = new HandoverTemporaryTargetFixture(app, approval.targetContext());
        directory = Files.createDirectory(attempt.resolve("temporary-handover-android"));
        var session = ((ObjectNode) f.session).deepCopy();
        var response = SubmissionJson.parse(f.get("/signing-key", f.token, 200, null));
        session.set("signingKey", response.path("data"));
        session.put("serverRecoveryEpoch", response.path("serverRecoveryEpoch").asText());
        var input = SubmissionJson.object().put("fixturePurpose", "SYNTHETIC_TEMP_HANDOVER_HTTP")
            .put("baseUrl", "http://127.0.0.1:19434/api/v1/").put("caseId", f.source.kase.toString());
        input.set("session", session);
        Files.writeString(attempt.resolve("private/temporary-handover-input.json"), SubmissionJson.canonical(input), StandardOpenOption.CREATE_NEW);
    }

    public void prepare() throws Exception {
        var prepared = SubmissionJson.parse(Files.readAllBytes(directory.resolve("prepare-result.json")));
        var id = prepared.path("authorizationId").asText();
        assertThat(f.source.jdbc.queryForObject("SELECT COUNT(*) FROM temporary_preparation_ack WHERE authorization_id=UUID_TO_BIN(?)", Long.class, id)).isEqualTo(1);
        var command = f.command().put("targetAuthorizationId", id);
        var receipt = f.context.reviewer().command(command, 200, null);
        Files.write(directory.resolve("server-prepared.json"), receipt);
    }

    public void verify() throws Exception {
        var result = SubmissionJson.parse(Files.readAllBytes(directory.resolve("stage-result.json")));
        var manifest = SubmissionJson.parse(f.get("/handovers/" + f.source.kase + "/manifest", f.token, 200, null));
        assertThat(result.path("payloadSha256")).isEqualTo(manifest.path("recoveryPackageSha256"));
        assertThat(result.path("caseId").asText()).isEqualTo(f.source.kase.toString());
        assertThat(result.path("state").asText()).isEqualTo("STAGED");
        assertThat(result.path("inputHighWatermark").asText()).isEqualTo(manifest.path("inputHighWatermark").asText());
        assertThat(Files.readAllBytes(directory.resolve("android-original.json")))
            .isEqualTo(f.get("/handovers/" + f.source.kase + "/recovery-package", f.token, 200, null));
        assertThat(f.source.jdbc.queryForObject("SELECT COUNT(*) FROM order_assignment WHERE device_id=?", Long.class, Ids.bytes(f.device))).isZero();
        assertThat(f.source.jdbc.queryForObject("SELECT status FROM handover_case WHERE id=?", String.class, Ids.bytes(f.source.kase))).isEqualTo("TARGET_PREPARED");
        Files.writeString(directory.resolve("verified.marker"), "Actual Android own preparation/ACK and durable TEMP original-byte reception; no activation or assignment.\n");
    }

    public void verifyConfirmation() throws Exception {
        var result = SubmissionJson.parse(Files.readAllBytes(directory.resolve("confirm-result.json")));
        var row = f.source.jdbc.queryForMap("SELECT receipt_bytes,command_bytes FROM temporary_handover_confirmation WHERE case_id=?", Ids.bytes(f.source.kase));
        assertThat(Files.readAllBytes(directory.resolve("android-confirmation.json"))).isEqualTo((byte[]) row.get("receipt_bytes"));
        assertThat(Files.readAllBytes(directory.resolve("android-confirmation-command.json"))).isEqualTo((byte[]) row.get("command_bytes"));
        assertThat(result.path("state").asText()).isEqualTo("CONFIRMED");
        assertThat(f.source.jdbc.queryForObject("SELECT COUNT(*) FROM temporary_handover_confirmation WHERE case_id=?", Long.class, Ids.bytes(f.source.kase))).isEqualTo(1);
        assertThat(f.source.jdbc.queryForObject("SELECT status FROM handover_case WHERE id=?", String.class, Ids.bytes(f.source.kase))).isEqualTo("TARGET_PREPARED");
        assertThat(f.source.jdbc.queryForObject("SELECT COUNT(*) FROM order_assignment WHERE device_id=?", Long.class, Ids.bytes(f.device))).isZero();
        Files.writeString(directory.resolve("confirmation-verified.marker"), "Actual Android immutable confirmation, lost-response recovery and Room rollback; no activation.\n");
    }
}
