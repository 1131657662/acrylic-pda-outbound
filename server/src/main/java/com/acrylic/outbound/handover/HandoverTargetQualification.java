package com.acrylic.outbound.handover;

import com.acrylic.outbound.auth.IdentityStore;
import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Reads target eligibility after the handover write path has locked identity, warehouse and device. */
@Component
final class HandoverTargetQualification {
    private static final List<String> PDA_PERMISSIONS = List.of("pda:login", "pda:scan", "pda:submit");
    private final IdentityStore identities;
    private final JdbcTemplate jdbc;

    HandoverTargetQualification(IdentityStore identities, JdbcTemplate jdbc) {
        this.identities = identities;
        this.jdbc = jdbc;
    }

    Target require(UUID deviceId, UUID userId, UUID warehouseId) {
        if (deviceId == null || userId == null)
            throw new BusinessException(400, "HANDOVER_TARGET_REQUIRED", "必须指定目标设备与人员");
        var devices = jdbc.query("""
            SELECT row_version FROM device WHERE id=? AND warehouse_id=? AND status='ACTIVE'
            """, (rs, n) -> rs.getString(1), Ids.bytes(deviceId), Ids.bytes(warehouseId));
        if (devices.isEmpty())
            throw new BusinessException(409, "HANDOVER_TARGET_DEVICE_INVALID", "目标设备无效、已停用或不属于本仓");
        var users = jdbc.query("""
            SELECT username, CAST(auth_version AS CHAR) FROM app_user WHERE id=? AND status='ACTIVE'
            """, (rs, n) -> new User(rs.getString(1), rs.getString(2)), Ids.bytes(userId));
        if (users.isEmpty() || !identities.permissions(userId).containsAll(PDA_PERMISSIONS)
                || identities.warehouses(userId).stream().noneMatch(w -> w.id().equals(warehouseId)))
            throw new BusinessException(409, "HANDOVER_TARGET_USER_INVALID", "目标人员无效或没有本仓 PDA 作业资格");
        return new Target(users.getFirst().username(), users.getFirst().authVersion(), devices.getFirst());
    }

    private record User(String username, String authVersion) { }
    record Target(String username, String authVersion, String deviceVersion) { }
}
