package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.pda.AssignmentStore;
import com.acrylic.outbound.temporary.TemporaryStore;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Original credentials are retained after expiry; current renewals cannot replace them. */
@Component
public class UnsubmittedSourceAuthority {
    private final AssignmentStore assignments;
    private final TemporaryStore temporary;
    private final UnsubmittedSourcePreparation preparation;
    private final JdbcTemplate jdbc;

    public UnsubmittedSourceAuthority(AssignmentStore assignments, TemporaryStore temporary,
        UnsubmittedSourcePreparation preparation, JdbcTemplate jdbc) {
        this.assignments = assignments; this.temporary = temporary; this.preparation = preparation; this.jdbc = jdbc;
    }

    Origin resolve(SourceEvidenceIntakePageObjects.Binding binding, JsonNode capture,
        Map<String, UnsubmittedSourcePreparation.Parts> preparations) {
        equal(uuid(capture, "originDeviceId"), binding.creatorDeviceId(), "原输入设备不符");
        require(capture.has("clientOrderId") != capture.has("assignmentId"), "原输入来源歧义");
        var origin = capture.has("clientOrderId") ? temporary(binding, capture, preparations) : formal(binding, capture, preparations);
        require(origin.expires().isAfter(origin.issued()), "原授权期限无效");
        return origin;
    }

    private Origin formal(SourceEvidenceIntakePageObjects.Binding binding, JsonNode capture,
        Map<String, UnsubmittedSourcePreparation.Parts> preparations) {
        var row = assignments.getByAuthorization(uuid(capture, "authorizationId"));
        var claims = preparation.claims(row.offlineToken(), "acrylic-pda-offline");
        equal(text(claims, "jti"), text(capture, "authorizationId"), "原授权ID不符");
        equal(text(claims, "warehouseId"), binding.warehouseId().toString(), "原授权仓库不符");
        equal(uuid(capture, "warehouseId"), binding.warehouseId(), "原输入仓库不符");
        equal(text(claims, "scope"), "pda:scan", "原授权用途不符");
        equal(text(claims, "planSha256"), row.planSha256(), "原授权计划摘要不符");
        equal(text(claims, "inventorySha256"), row.inventorySha256(), "原授权库存摘要不符");
        Map.of("assignmentId", row.id(), "orderId", row.orderId(), "originDeviceId", row.deviceId(),
            "operatorId", row.operatorId(), "assignmentEpoch", row.epoch(), "snapshotId", row.inventorySnapshotId(),
            "sourceRecoveryEpoch", row.serverRecoveryEpoch()).forEach((field, expected) -> equal(text(capture, field), expected, "原输入授权绑定不符"));
        Map.of("assignmentId", row.id(), "orderId", row.orderId(), "deviceId", row.deviceId(),
            "sub", row.operatorId(), "assignmentEpoch", row.epoch(), "inventorySnapshotId", row.inventorySnapshotId(),
            "serverRecoveryEpoch", row.serverRecoveryEpoch()).forEach((field, expected) -> equal(text(claims, field), expected, "原签名授权绑定不符"));
        require(capture.path("orderRevision").isInt() && capture.path("orderRevision").intValue() == row.revision()
            && claims.path("revision").isInt() && claims.path("revision").intValue() == row.revision(), "原订单版本不符");
        var parts = preparations.computeIfAbsent("FORMAL/" + row.offlineAuthorizationId(), ignored -> preparation.formal(row));
        return origin(row.issuedAt(), row.expiresAt(), claims, parts);
    }

    private Origin temporary(SourceEvidenceIntakePageObjects.Binding binding, JsonNode capture,
        Map<String, UnsubmittedSourcePreparation.Parts> preparations) {
        var row = temporary.get(uuid(capture, "authorizationId"));
        equal(row.warehouseId(), binding.warehouseId(), "原资格仓库不符");
        equal(row.deviceId(), binding.creatorDeviceId(), "原资格设备不符");
        equal(row.userId(), uuid(capture, "operatorId"), "原资格人员不符");
        equal(row.authorizationEpoch(), text(capture, "authorizationEpoch"), "原资格代次不符");
        equal(row.recoveryEpoch(), text(capture, "sourceRecoveryEpoch"), "原恢复代次不符");
        require(number(capture, "orderRevision") > 0, "原计划版本无效");
        var own = jdbc.queryForList("SELECT BIN_TO_UUID(client_order_id) FROM submission_scope WHERE id=?",
            String.class, Ids.bytes(binding.scopeId()));
        require(own.size() == 1 && !text(capture, "clientOrderId").equals(own.getFirst()), "本来源不得伪装其他来源");
        var parts = preparations.computeIfAbsent("TEMP/" + row.id(), ignored -> preparation.temporary(row));
        var claims = preparation.claims(row.receipt().offlineToken(), "acrylic-pda-temporary-work");
        equal(text(claims, "jti"), row.id().toString(), "原资格签名ID不符");
        equal(text(claims, "manifestSha256"), row.receipt().manifestSha256(), "原资格清单不符");
        equal(text(claims, "purpose"), "TEMP_WORK", "原资格用途不符");
        equal(text(claims, "scope"), "temporary_order:create pda:scan pda:submit", "原资格权限不符");
        equal(claims.path("identity"), parts.manifest().path("identity"), "原资格身份不符");
        temporaryIdentity(row, parts.manifest().path("identity"));
        equal(text(capture, "snapshotId"), text(parts.manifest(), "inventorySnapshotId"), "原资格库存不符");
        temporarySnapshot(row, capture);
        return origin(row.issuedAt().toString(), row.expiresAt().toString(), claims, parts);
    }

    private void temporarySnapshot(TemporaryStore.Stored row, JsonNode capture) {
        var snapshots = jdbc.queryForList("""
            SELECT BIN_TO_UUID(a.inventory_snapshot_id) FROM temporary_authorization a
            JOIN inventory_snapshot s ON s.id=a.inventory_snapshot_id AND s.warehouse_id=a.warehouse_id WHERE a.id=?
            """, String.class, Ids.bytes(row.id()));
        require(snapshots.size() == 1 && text(capture, "snapshotId").equals(snapshots.getFirst()), "原资格库存索引不符");
    }

    private void temporaryIdentity(TemporaryStore.Stored row, JsonNode identity) {
        Map.of("authorizationId", row.id().toString(), "bundleId", row.receipt().bundleId(),
            "deviceId", row.deviceId().toString(), "userId", row.userId().toString(),
            "warehouseId", row.warehouseId().toString(), "authorizationEpoch", row.authorizationEpoch(),
            "serverRecoveryEpoch", row.recoveryEpoch(), "userAuthVersion", row.userVersion(), "deviceVersion", row.deviceVersion())
            .forEach((field, expected) -> equal(text(identity, field), expected, "原资格固定身份不符"));
    }

    private Origin origin(String issued, String expires, JsonNode claims, UnsubmittedSourcePreparation.Parts parts) {
        var start = Instant.parse(issued); var end = Instant.parse(expires);
        require(claims.path("iat").isIntegralNumber() && claims.path("exp").isIntegralNumber()
            && claims.path("iat").longValue() == start.getEpochSecond()
            && claims.path("exp").longValue() == end.getEpochSecond(), "原签名期限不符");
        return new Origin(start, end, parts);
    }

    record Origin(Instant issued, Instant expires, UnsubmittedSourcePreparation.Parts parts) { }
}
