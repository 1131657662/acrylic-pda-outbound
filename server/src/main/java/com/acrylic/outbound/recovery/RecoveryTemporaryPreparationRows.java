package com.acrylic.outbound.recovery;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.common.JsonText;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.TemporaryCommands;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Preserves native manifest/token/compressed bytes; missing ACK commands cannot be derived from rounded columns. */
final class RecoveryTemporaryPreparationRows {
    private final JdbcTemplate jdbc;
    RecoveryTemporaryPreparationRows(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    ObjectNode read(RecoveryTemporaryPreparationCapture.Request request) {
        var rows = jdbc.query("""
            SELECT BIN_TO_UUID(id) authorizationId,BIN_TO_UUID(bundle_id) bundleId,BIN_TO_UUID(warehouse_id) warehouseId,
             BIN_TO_UUID(device_id) deviceId,BIN_TO_UUID(user_id) userId,CAST(recovery_epoch AS CHAR) serverRecoveryEpoch,
             CAST(authorization_epoch AS CHAR) authorizationEpoch,signed_token,manifest_text,manifest_sha256,signed_manifest
            FROM temporary_authorization WHERE id=?
            """, (rs, index) -> {
                var value = SubmissionJson.object();
                for (String field : List.of("authorizationId", "bundleId", "warehouseId", "deviceId", "userId", "serverRecoveryEpoch", "authorizationEpoch",
                        "signed_token", "manifest_text", "manifest_sha256", "signed_manifest")) value.put(field, rs.getString(field));
                return value;
            }, Ids.bytes(request.identity().qualificationId()));
        require(rows.size() == 1, "RECOVERY_TEMP_CAPTURE_UNKNOWN"); var value = rows.getFirst(); bind(value, request.identity());
        require(text(value, "manifest_text").length() <= 1048576 && text(value, "signed_token").length() <= 131072
            && text(value, "signed_manifest").length() <= 131072, "RECOVERY_TEMP_CAPTURE_SIZE");
        var receipt = SubmissionJson.object().put("authorizationId", text(value, "authorizationId")).put("bundleId", text(value, "bundleId"))
            .put("offlineToken", text(value, "signed_token")).put("manifest", text(value, "manifest_text"))
            .put("manifestSha256", text(value, "manifest_sha256")).put("signedManifest", text(value, "signed_manifest"));
        var origin = SubmissionJson.object().put("offlineToken", text(value, "signed_token")); origin.set("preparationReceipt", receipt);
        origin.set("parts", parts(request.identity().qualificationId()));
        origin.set("activation", activation(value, request)); return origin;
    }

    private void bind(JsonNode value, RecoverySourceIdentity id) {
        require(uuid(value, "authorizationId").equals(id.qualificationId()) && uuid(value, "warehouseId").equals(id.warehouseId())
            && uuid(value, "deviceId").equals(id.deviceId()) && uuid(value, "userId").equals(id.operatorId())
            && text(value, "serverRecoveryEpoch").equals(id.sourceRecoveryEpoch()), "RECOVERY_TEMP_CAPTURE_IDENTITY");
        require(sha256(text(value, "manifest_text").getBytes(java.nio.charset.StandardCharsets.UTF_8)).equals(text(value, "manifest_sha256")),
            "RECOVERY_TEMP_CAPTURE_MANIFEST");
    }

    private ObjectNode parts(UUID authorization) {
        var result = SubmissionJson.object(); long[] total = {0};
        jdbc.query("SELECT part_name,compressed FROM temporary_preparation_part WHERE authorization_id=?", rs -> {
            String name = rs.getString(1); byte[] bytes = rs.getBytes(2);
            require(Set.of("inventory", "mapping", "policy", "rules", "availability").contains(name)
                && !result.has(name) && bytes != null && bytes.length > 0 && bytes.length <= 16777216, "RECOVERY_TEMP_CAPTURE_PART");
            total[0] += bytes.length; require(total[0] <= 20971520, "RECOVERY_TEMP_CAPTURE_SIZE");
            result.put(name, Base64.getEncoder().encodeToString(bytes));
        }, Ids.bytes(authorization));
        require(result.size() == 5, "RECOVERY_TEMP_CAPTURE_PART_SET"); return result;
    }

    private ObjectNode activation(JsonNode authorization, RecoveryTemporaryPreparationCapture.Request request) {
        var command = SubmissionJson.parse(request.originalAckCommand()); fields(command, "bundleId manifestSha256 authorizationEpoch activatedAt");
        require(command.path("bundleId").equals(authorization.path("bundleId"))
            && command.path("manifestSha256").equals(authorization.path("manifest_sha256"))
            && command.path("authorizationEpoch").equals(authorization.path("authorizationEpoch")), "RECOVERY_TEMP_CAPTURE_COMMAND");
        var nativeCommand = new TemporaryCommands.Ack(uuid(command, "bundleId"), text(command, "manifestSha256"),
            text(command, "authorizationEpoch"), RecoveryPreparationBinding.time(command, "activatedAt"));
        var serializer = new JsonText(new ObjectMapper().registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
        var rows = jdbc.query("SELECT command_sha256,activated_at,acknowledged_at,signed_ack FROM temporary_preparation_ack WHERE authorization_id=?",
            (rs, index) -> {
                require(serializer.digest(nativeCommand).equals(rs.getString(1)), "RECOVERY_TEMP_CAPTURE_COMMAND_DIGEST");
                require(rs.getString(4) != null && rs.getString(4).length() <= 131072, "RECOVERY_TEMP_CAPTURE_ACK_REQUIRED");
                return SubmissionJson.object().put("authorizationId", request.identity().qualificationId().toString())
                    .put("bundleId", text(authorization, "bundleId")).put("activatedAt", rs.getTimestamp(2).toInstant().toString())
                    .put("acknowledgedAt", rs.getTimestamp(3).toInstant().toString()).put("signedAck", rs.getString(4));
            }, Ids.bytes(request.identity().qualificationId()));
        require(rows.size() == 1, "RECOVERY_TEMP_CAPTURE_ACK_REQUIRED");
        return SubmissionJson.object().put("command", request.originalAckCommand()).put("receipt", rows.getFirst().toString());
    }
}
