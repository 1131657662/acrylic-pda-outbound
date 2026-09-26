package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.devices.DeviceCrypto;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class SourceEvidenceIntakeDeviceKeys {
    private static final String SELECT = """
        SELECT d.warehouse_id,d.public_key AS device_key,d.key_sha256 AS device_hash,
               p.id AS pairing_id,p.status AS pairing_status,p.public_key AS pairing_key,
               p.key_sha256 AS pairing_hash,p.approved_by AS pairing_approver,
               p.approval_json AS pairing_approval,p.approval_sha256 AS pairing_approval_hash
        FROM device d LEFT JOIN device_pairing p ON p.device_id=d.id
        WHERE d.id=?
        """;
    private final JdbcTemplate jdbc;

    public SourceEvidenceIntakeDeviceKeys(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public PublicKey trusted(UUID warehouseId, UUID deviceId) {
        if (warehouseId == null || deviceId == null) throw new IllegalArgumentException("E2A_DEVICE_KEY_BINDING");
        var rows = jdbc.queryForList(SELECT, Ids.bytes(deviceId));
        if (rows.size() != 1) throw unverifiable();
        var row = rows.getFirst();
        if (!(row.get("warehouse_id") instanceof byte[] warehouse)
            || !Arrays.equals(warehouse, Ids.bytes(warehouseId))
            || !(row.get("device_key") instanceof String registered)
            || !DeviceCrypto.sha(registered).equals(row.get("device_hash"))) throw unverifiable();
        if (row.get("pairing_id") != null && (!(row.get("pairing_id") instanceof byte[] pairingId)
            || pairingId.length != 16 || !"COMPLETED".equals(row.get("pairing_status"))
            || !(row.get("pairing_approver") instanceof byte[] approver) || approver.length != 16
            || row.get("pairing_approval") == null || row.get("pairing_approval_hash") == null
            || !(row.get("pairing_key") instanceof String paired) || !registered.equals(paired)
            || !DeviceCrypto.sha(paired).equals(row.get("pairing_hash")))) throw unverifiable();
        try { return DeviceCrypto.publicKey(registered); }
        catch (BusinessException malformed) { throw unverifiable(); }
    }

    private static BusinessException unverifiable() {
        return new BusinessException(409, "E2A_STOP_KEY_UNVERIFIABLE", "原设备登记公钥不可核验");
    }
}
