package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.Ids;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/**
 * R07-3 step 1 verification (equivalent of MigrationTestHelper on an isolated non-empty MySQL): the migration
 * applies, existing data survives, the new tables exist and are empty, the relaxed CHECK really accepts 0 while
 * still accepting >0, and inventory_ledger's original invariants are untouched.
 */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aCorrectionSchemaIT {
    private final StringBuilder notes = new StringBuilder();

    @Test void correctionModelMigrationAppliesAndKeepsExistingData() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34"); E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        profile.migrate(jdbc, "42");
        long orders = count(jdbc, "outbound_order"), ledgers = count(jdbc, "inventory_ledger");
        long results = count(jdbc, "order_result_version"), dispatches = count(jdbc, "dispatch_record");
        notes.append("seeded: orders=").append(orders).append(" ledgers=").append(ledgers)
            .append(" results=").append(results).append(" dispatches=").append(dispatches).append('\n');

        profile.migrate(jdbc, "43");

        // (1) existing data preserved
        assertThat(count(jdbc, "outbound_order")).as("orders preserved").isEqualTo(orders);
        assertThat(count(jdbc, "inventory_ledger")).as("ledger rows preserved").isEqualTo(ledgers);
        assertThat(count(jdbc, "order_result_version")).as("result versions preserved").isEqualTo(results);
        // (2) new tables exist and are empty
        for (String table : List.of("correction_document", "correction_line", "correction_effect",
            "correction_ledger", "cycle_reopening", "inventory_reconciliation", "order_current_result")) {
            assertThat(count(jdbc, table)).as(table + " exists and is empty").isZero();
            notes.append("new table ").append(table).append("=0\n");
        }
        // (3) the relaxed CHECK accepts both 0 and >0 (semantic change: net-zero must be storable)
        var host = jdbc.queryForMap("SELECT BIN_TO_UUID(order_id) order_id,BIN_TO_UUID(assignment_id) assignment_id,"
            + "BIN_TO_UUID(warehouse_id) warehouse_id,BIN_TO_UUID(device_id) device_id,BIN_TO_UUID(operator_id) operator_id,"
            + "CAST(recovery_epoch AS CHAR) recovery_epoch FROM order_assignment WHERE status='ACTIVE' LIMIT 1");
        UUID order = UUID.fromString(host.get("order_id").toString());
        UUID warehouse = UUID.fromString(host.get("warehouse_id").toString());
        for (long[] pair : new long[][]{{2,0,0},{3,5,100}}) {
            UUID scope = UUID.randomUUID(), submission = UUID.randomUUID();
            jdbc.update("INSERT INTO submission_scope(id,warehouse_id,source_order_id,canonical_order_id,work_round,current_submission_id)"
                + " VALUES(?,?,?,?,1,NULL)", Ids.bytes(scope), Ids.bytes(warehouse), Ids.bytes(order), Ids.bytes(order));
            jdbc.update("INSERT INTO submission(id,scope_id,assignment_id,freeze_device_id,operator_id,work_round,"
                + "source_recovery_epoch,status,issues_json,receipt_text,created_at,updated_at)"
                + " VALUES(?,?,?,?,?,1,?,'REGISTERED',NULL,NULL,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
                Ids.bytes(submission), Ids.bytes(scope), Ids.bytes(UUID.fromString(host.get("assignment_id").toString())),
                Ids.bytes(UUID.fromString(host.get("device_id").toString())), Ids.bytes(UUID.fromString(host.get("operator_id").toString())),
                Long.parseLong(host.get("recovery_epoch").toString()));
            jdbc.update("INSERT INTO order_result_version(order_id,result_version,submission_id,actual_count,weight_g,"
                + "inventory_deducted,header_snapshot,created_at) VALUES(?,?,?,?,?,0,JSON_OBJECT(),UTC_TIMESTAMP(3))",
                Ids.bytes(order), pair[0], Ids.bytes(submission), pair[1], pair[2]);
            notes.append("result v").append(pair[0]).append(" count=").append(pair[1]).append(" weight=").append(pair[2])
                .append(" accepted\n");
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()"
            + " AND TABLE_NAME='order_result_version' AND CHECK_CLAUSE LIKE '%actual_count%' AND CHECK_CLAUSE NOT LIKE '%>=%'",
            Integer.class)).as("no restrictive actual_count CHECK remains").isZero();
        // (4) inventory_ledger invariants unchanged: UNIQUE(dispatch_id) and both CHECKs still present
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()"
            + " AND TABLE_NAME='inventory_ledger' AND COLUMN_NAME='dispatch_id' AND NON_UNIQUE=0", Integer.class))
            .as("ledger dispatch_id stays UNIQUE").isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()"
            + " AND TABLE_NAME='inventory_ledger'", Integer.class)).as("ledger CHECKs still present").isGreaterThanOrEqualTo(2);
        if (dispatches > 0) {
            var dispatch = jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM dispatch_record LIMIT 1", String.class);
            var bale = jdbc.queryForObject("SELECT BIN_TO_UUID(bale_id) FROM dispatch_record LIMIT 1", String.class);
            assertThat(rejected(jdbc, "INSERT INTO inventory_ledger VALUES(?,?,?,?,?,UTC_TIMESTAMP(3))",
                Ids.bytes(Ids.next()), Ids.bytes(UUID.fromString(dispatch)), Ids.bytes(UUID.fromString(bale)), 1, -1L))
                .as("ledger still rejects quantity_delta=+1").isTrue();
            assertThat(rejected(jdbc, "INSERT INTO inventory_ledger VALUES(?,?,?,?,?,UTC_TIMESTAMP(3))",
                Ids.bytes(Ids.next()), Ids.bytes(UUID.fromString(dispatch)), Ids.bytes(UUID.fromString(bale)), -1, 1L))
                .as("ledger still rejects weight_delta_g>0").isTrue();
            notes.append("ledger functional invariants re-verified on a real dispatch\n");
        } else {
            notes.append("ledger: no seeded dispatch, definition-level assertions only\n");
        }
        Files.writeString(profile.attempt.resolve("correction-schema.txt"), notes.toString());
        Files.writeString(profile.attempt.resolve("test-completed.marker"), notes.toString());
    }

    private boolean rejected(JdbcTemplate jdbc, String sql, Object... args) {
        try { jdbc.update(sql, args); return false; }
        catch (DataAccessException expected) { return true; }
    }

    private long count(JdbcTemplate jdbc, String table) {
        Long value = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return value == null ? -1 : value;
    }
}
