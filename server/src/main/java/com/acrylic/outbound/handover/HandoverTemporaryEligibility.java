package com.acrylic.outbound.handover;

import com.acrylic.outbound.auth.IdentityStore;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.pda.PdaSessions;
import com.acrylic.outbound.temporary.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Current recipient identity and TEMP admission, read under the command's identity/warehouse/device locks. */
@Component
final class HandoverTemporaryEligibility {
    private final HandoverTargetQualification targets;
    private final TemporaryStore authorizations;
    private final TemporaryAccess access;
    private final IdentityStore identities;
    private final JdbcTemplate jdbc;

    HandoverTemporaryEligibility(HandoverTargetQualification targets, TemporaryStore authorizations,
            TemporaryAccess access, IdentityStore identities, JdbcTemplate jdbc) {
        this.targets = targets; this.authorizations = authorizations; this.access = access;
        this.identities = identities; this.jdbc = jdbc;
    }

    TemporaryStore.Stored require(JsonNode command, JsonNode facts, String epoch) {
        var device = uuid(command, "targetDeviceId"); var user = uuid(command, "targetUserId"); var warehouse = uuid(facts, "warehouseId");
        var target = targets.require(device, user, warehouse);
        var row = authorizations.get(authorization(command));
        var principal = new PdaSessions.Principal(null, user, target.username(), device, warehouse, target.authVersion(), target.deviceVersion());
        access.usable(row, principal, epoch); access.unexpired(row);
        authorizations.requireInitialSubmission(row.id());
        boolean enabled = Boolean.TRUE.equals(jdbc.queryForObject("SELECT temporary_order_enabled FROM device WHERE id=?",
            Boolean.class, Ids.bytes(device)));
        if (!identities.permissions(user).contains("temporary_order:create") || !enabled)
            throw new BusinessException(409, "HANDOVER_TARGET_TEMP_DISABLED", "接收人或设备没有临时作业准入");
        return row;
    }

    static java.util.UUID authorization(JsonNode command) {
        var allowed = java.util.Set.of("commandVersion", "commandType", "commandId", "warehouseId", "caseId",
            "transportRecoveryEpoch", "targetDeviceId", "targetUserId", "targetAuthorizationId", "expected", "reason");
        var actual = new java.util.HashSet<String>(); command.fieldNames().forEachRemaining(actual::add);
        if (!allowed.containsAll(actual) || !command.path("commandVersion").isIntegralNumber()
                || command.path("commandVersion").intValue() != 1 || !"PREPARE".equals(command.path("commandType").asText()))
            throw new BusinessException(400, "INVALID_HANDOVER_COMMAND", "临时交接准备命令字段无效");
        try { return uuid(command, "targetAuthorizationId"); }
        catch (BusinessException invalid) {
            throw new BusinessException(400, "HANDOVER_TARGET_AUTHORIZATION_REQUIRED", "必须指定接收人本机的有效临时授权");
        }
    }
}
