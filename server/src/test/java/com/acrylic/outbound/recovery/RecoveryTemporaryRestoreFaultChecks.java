package com.acrylic.outbound.recovery;

import com.acrylic.outbound.common.Ids;
import java.nio.file.*;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Faults are injected only in a newly imported target; every refused path is compared with all target rows/files. */
final class RecoveryTemporaryRestoreFaultChecks {
    static void verify(RecoveryTemporaryRestoreTargetChecks.Context context) throws Exception {
        missingPins(context); targetConflict(context); inventoryConflict(context); policyConflict(context); mappingConflict(context);
        conflictingPart(context); conflictingAck(context); lastSql(context); originalChange(context);
    }

    private static void missingPins(RecoveryTemporaryRestoreTargetChecks.Context context) throws Exception {
        var request = context.request();
        assertThatThrownBy(() -> context.restorer().restore(new RecoveryTemporaryPreparationRestore.Request(request.materialRoot(),null,request.original())))
            .hasMessage("RECOVERY_SOURCE_PIN_REQUIRED");
        var old = request.original(); var missing = new RecoveryOriginalTemporaryPreparation.Request(old.materialRoot(),null,old.database(),old.notAfter());
        assertThatThrownBy(() -> context.restorer().restore(new RecoveryTemporaryPreparationRestore.Request(request.materialRoot(),request.sourcePinSha256(),missing)))
            .hasMessage("RECOVERY_TEMP_ORIGINAL_PIN"); context.preserved();
    }

    private static void targetConflict(RecoveryTemporaryRestoreTargetChecks.Context context) throws Exception {
        var jdbc = context.jdbc(); byte[] id = Ids.bytes(context.source().identity.qualificationId());
        String original = jdbc.queryForObject("SELECT signed_manifest FROM temporary_authorization WHERE id=?",String.class,id);
        jdbc.update("UPDATE temporary_authorization SET signed_manifest='different-retained-original' WHERE id=?",id);
        try { assertThatThrownBy(() -> context.restorer().restore(context.request())).hasMessage("RECOVERY_TEMP_RESTORE_AUTHORIZATION_CONFLICT"); }
        finally { jdbc.update("UPDATE temporary_authorization SET signed_manifest=? WHERE id=?",original,id); }
        context.preserved();
    }

    private static void inventoryConflict(RecoveryTemporaryRestoreTargetChecks.Context context) throws Exception {
        context.jdbc().update("UPDATE inventory_snapshot_bale SET weight_g=weight_g+1");
        try { assertThatThrownBy(() -> context.restorer().restore(context.request())).hasMessage("RECOVERY_TEMP_RESTORE_INVENTORY_CONFLICT"); }
        finally { context.jdbc().update("UPDATE inventory_snapshot_bale SET weight_g=weight_g-1"); } context.preserved();
    }

    private static void policyConflict(RecoveryTemporaryRestoreTargetChecks.Context context) throws Exception {
        context.jdbc().update("UPDATE policy_version SET auto_deduct_inventory=1");
        try { assertThatThrownBy(() -> context.restorer().restore(context.request())).hasMessage("RECOVERY_TEMP_RESTORE_POLICY_CONFLICT"); }
        finally { context.jdbc().update("UPDATE policy_version SET auto_deduct_inventory=0"); } context.preserved();
    }

    private static void mappingConflict(RecoveryTemporaryRestoreTargetChecks.Context context) throws Exception {
        context.jdbc().update("UPDATE delivery_mapping SET version=version+1");
        try { assertThatThrownBy(() -> context.restorer().restore(context.request())).hasMessage("RECOVERY_TEMP_RESTORE_MAPPING_CONFLICT"); }
        finally { context.jdbc().update("UPDATE delivery_mapping SET version=version-1"); } context.preserved();
    }

    private static void conflictingPart(RecoveryTemporaryRestoreTargetChecks.Context context) throws Exception {
        byte[] id = Ids.bytes(context.source().identity.qualificationId());
        context.jdbc().update("INSERT INTO temporary_preparation_part VALUES(?,'rules',X'01')",id);
        var before = context.snapshots().capture();
        try {
            assertThatThrownBy(() -> context.restorer().restore(context.request())).hasMessage("RECOVERY_TEMP_RESTORE_PART_CONFLICT");
            assertThat(context.snapshots().capture()).isEqualTo(before);
        } finally { context.jdbc().update("DELETE FROM temporary_preparation_part WHERE authorization_id=?",id); }
        context.preserved();
    }

    private static void conflictingAck(RecoveryTemporaryRestoreTargetChecks.Context context) throws Exception {
        byte[] id = Ids.bytes(context.source().identity.qualificationId());
        context.jdbc().update("INSERT INTO temporary_preparation_ack VALUES(?,REPEAT('a',64),'2026-09-20','2026-09-23','different-signed-ack')",id);
        var before = context.snapshots().capture();
        try {
            assertThatThrownBy(() -> context.restorer().restore(context.request())).hasMessage("RECOVERY_TEMP_RESTORE_ACK_CONFLICT");
            assertThat(context.snapshots().capture()).isEqualTo(before);
        } finally { context.jdbc().update("DELETE FROM temporary_preparation_ack WHERE authorization_id=?",id); }
        context.preserved();
    }

    private static void lastSql(RecoveryTemporaryRestoreTargetChecks.Context context) throws Exception {
        String sql = "CREATE TRIGGER temp_restore_last_sql BEFORE INSERT ON temporary_preparation_ack FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='TEMP_RESTORE_LAST_SQL'";
        context.jdbc().execute(sql);
        try { assertThatThrownBy(() -> context.restorer().restore(context.request())).isInstanceOf(org.springframework.dao.DataAccessException.class)
            .hasMessageContaining("TEMP_RESTORE_LAST_SQL"); }
        finally { context.jdbc().execute("DROP TRIGGER temp_restore_last_sql"); }
        context.preserved(); Files.writeString(context.attempt().resolve("restore-tail-sql.txt"),sql+"\nPASS real last INSERT failed; all five new parts rolled back\n");
    }

    private static void originalChange(RecoveryTemporaryRestoreTargetChecks.Context context) throws Exception {
        Path file = context.source().object("origin"); byte[] original = Files.readAllBytes(file);
        var boundary = new RecoveryTemporaryRestoreMutationDataSource(context.jdbc().getDataSource(),() -> {
            try { byte[] changed = original.clone(); changed[0] ^= 1; Files.write(file,changed);
                Files.write(context.attempt().resolve("restore-before-commit-damaged-origin.bin"),changed); }
            catch(java.io.IOException error) { throw new IllegalStateException(error); }
        });
        try {
            var consumer = new RecoveryTemporaryPreparationRestore(boundary,context.supply().authority(),context.keys());
            assertThatThrownBy(() -> consumer.restore(context.request())).hasMessage("RECOVERY_SOURCE_OBJECT_MISMATCH");
            assertThat(boundary.mutated()).isTrue();
        } finally { Files.write(file,original); }
        context.preserved();
    }
}
