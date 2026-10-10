package com.acrylic.outbound.recovery;

import com.acrylic.outbound.common.Ids;
import java.sql.Timestamp;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.require;

/** Insert-only native part/ACK persistence; a conflicting original is never overwritten. */
final class RecoveryTemporaryRestoreRows {
    private final JdbcTemplate jdbc;
    RecoveryTemporaryRestoreRows(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    RecoveryTemporaryPreparationRestore.Result restore(RecoveryTemporaryRestoreMaterial material) {
        var existing = parts(material); int inserted = 0;
        for (String name : material.content().keySet().stream().sorted().toList()) {
            byte[] bytes = material.compressed(name);
            if (existing.containsKey(name)) require(Arrays.equals(existing.get(name), bytes), "RECOVERY_TEMP_RESTORE_PART_CONFLICT");
            else {
                require(jdbc.update("INSERT INTO temporary_preparation_part(authorization_id,part_name,compressed) VALUES(?,?,?)",
                    Ids.bytes(material.identity().qualificationId()), name, bytes) == 1, "RECOVERY_TEMP_RESTORE_PART_WRITE");
                inserted++;
            }
        }
        boolean insertedAck = ack(material, true); requireComplete(material);
        return new RecoveryTemporaryPreparationRestore.Result(inserted, insertedAck);
    }

    void requireComplete(RecoveryTemporaryRestoreMaterial material) {
        var actual = parts(material);
        require(actual.keySet().equals(material.content().keySet()), "RECOVERY_TEMP_RESTORE_PART_SET");
        for (String name : actual.keySet())
            require(Arrays.equals(actual.get(name), material.compressed(name)), "RECOVERY_TEMP_RESTORE_PART_CONFLICT");
        ack(material, false);
    }

    private Map<String, byte[]> parts(RecoveryTemporaryRestoreMaterial material) {
        var result = new HashMap<String, byte[]>();
        jdbc.query("SELECT part_name,compressed FROM temporary_preparation_part WHERE authorization_id=? FOR UPDATE", rs -> {
            String name = rs.getString(1); byte[] bytes = rs.getBytes(2);
            require(material.content().containsKey(name) && bytes != null && result.put(name, bytes) == null,
                "RECOVERY_TEMP_RESTORE_PART_SET");
        }, Ids.bytes(material.identity().qualificationId()));
        return result;
    }

    private boolean ack(RecoveryTemporaryRestoreMaterial material, boolean mayInsert) {
        var original = new Ack(material.activation().commandSha256(),
            Timestamp.from(material.activation().activatedAt().truncatedTo(ChronoUnit.MILLIS)),
            Timestamp.from(material.activation().acknowledgedAt()), material.signedAck());
        byte[] id = Ids.bytes(material.identity().qualificationId());
        var actual = jdbc.query("SELECT command_sha256,activated_at,acknowledged_at,signed_ack FROM temporary_preparation_ack WHERE authorization_id=? FOR UPDATE",
            (rs, n) -> new Ack(rs.getString(1), rs.getTimestamp(2), rs.getTimestamp(3), rs.getString(4)), id);
        if (!actual.isEmpty()) {
            require(actual.size() == 1 && actual.getFirst().equals(original), "RECOVERY_TEMP_RESTORE_ACK_CONFLICT"); return false;
        }
        require(mayInsert, "RECOVERY_TEMP_RESTORE_ACK_MISSING");
        require(jdbc.update("INSERT INTO temporary_preparation_ack(authorization_id,command_sha256,activated_at,acknowledged_at,signed_ack) VALUES(?,?,?,?,?)",
            id, original.digest(), original.activated(), original.acknowledged(), original.signed()) == 1, "RECOVERY_TEMP_RESTORE_ACK_WRITE");
        return true;
    }
    private record Ack(String digest, Timestamp activated, Timestamp acknowledged, String signed) { }
}
