package com.acrylic.outbound.e2averify;

import java.nio.file.Files;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;
import static org.assertj.core.api.Assertions.*;

/**
 * R03 round 1: build a FORMAL REGISTERED source with plain SQL and find out which implicit constraint rejects it.
 * It deliberately bypasses the freeze chain, the receipt chain and the capture-sequence chain; whatever fails first
 * is the finding. The seeded database is probed for the publication prerequisites before anything is written.
 */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aFormalRegisteredSourceIT {
    @Test void registeredFormalSourceCanBeBuiltWithSql() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34"); E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        profile.migrate(jdbc, "42");
        var notes = new StringBuilder();

        for (String table : new String[]{"order_publication","order_assignment","inventory_snapshot","policy_version","outbound_order"})
            notes.append(table).append('=').append(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class)).append(' ');
        Files.writeString(profile.attempt.resolve("seed-shape.txt"), notes.toString());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_publication", Integer.class))
            .as("V34 seed publication count; seed shape: " + notes).isGreaterThan(0);

        // A published, assigned order from the seed is the cheapest host for a REGISTERED source.
        var host = jdbc.queryForMap("""
            SELECT BIN_TO_UUID(a.id) assignment_id,BIN_TO_UUID(a.order_id) order_id,BIN_TO_UUID(a.warehouse_id) warehouse_id,
              BIN_TO_UUID(a.device_id) device_id,BIN_TO_UUID(a.operator_id) operator_id,a.revision,CAST(a.epoch AS CHAR) epoch,
              CAST(a.recovery_epoch AS CHAR) recovery_epoch
            FROM order_assignment a WHERE a.status='ACTIVE' LIMIT 1
            """);
        UUID warehouse = UUID.fromString(host.get("warehouse_id").toString());
        UUID order = UUID.fromString(host.get("order_id").toString());
        UUID assignment = UUID.fromString(host.get("assignment_id").toString());
        UUID device = UUID.fromString(host.get("device_id").toString());
        UUID operator = UUID.fromString(host.get("operator_id").toString());

        // REGISTERED is the "registered and never frozen" state: no receipt_text is required for it.
        UUID submission = UUID.randomUUID();
        UUID scope = UUID.randomUUID();
        assertThat(jdbc.update("""
            INSERT INTO submission_scope(id,warehouse_id,source_order_id,work_round,current_submission_id)
            VALUES(?,?,?,1,NULL)
            """, com.acrylic.outbound.common.Ids.bytes(scope), com.acrylic.outbound.common.Ids.bytes(warehouse),
            com.acrylic.outbound.common.Ids.bytes(order))).as("scope insert").isEqualTo(1);
        assertThat(jdbc.update("""
            INSERT INTO submission(id,scope_id,assignment_id,freeze_device_id,operator_id,work_round,source_recovery_epoch,
              status,issues_json,receipt_text,created_at,updated_at)
            VALUES(?,?,?,?,?,1,?, 'REGISTERED',NULL,NULL,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))
            """, com.acrylic.outbound.common.Ids.bytes(submission), com.acrylic.outbound.common.Ids.bytes(scope),
            com.acrylic.outbound.common.Ids.bytes(assignment), com.acrylic.outbound.common.Ids.bytes(device),
            com.acrylic.outbound.common.Ids.bytes(operator), Long.parseLong(host.get("recovery_epoch").toString()))).as("submission insert").isEqualTo(1);
        jdbc.update("UPDATE submission_scope SET current_submission_id=? WHERE id=?",
            com.acrylic.outbound.common.Ids.bytes(submission), com.acrylic.outbound.common.Ids.bytes(scope));
        int slot = jdbc.update("""
            INSERT INTO order_submission_slot(order_id,scope_id,work_round,current_submission_id,phase)
            VALUES(?,?,1,?,'FROZEN')
            """, com.acrylic.outbound.common.Ids.bytes(order), com.acrylic.outbound.common.Ids.bytes(scope),
            com.acrylic.outbound.common.Ids.bytes(submission));
        notes.append("slot=").append(slot).append(' ');

        // The binding read is the first production-shaped consumer of such a row.
        var bindings = jdbc.queryForList("""
            SELECT BIN_TO_UUID(c.id) scope_id,IF(c.source_order_id IS NULL,'TEMP','FORMAL') kind,
              BIN_TO_UUID(c.canonical_order_id) canonical,CAST(c.mapping_version AS CHAR) mapping_version
            FROM submission_scope c WHERE c.id=?
            """, com.acrylic.outbound.common.Ids.bytes(scope));
        Files.writeString(profile.attempt.resolve("formal-registered-source.txt"),
            "scope=" + scope + " submission=" + submission + " order=" + order + "\n" + bindings + "\n" + notes);
        assertThat(bindings).hasSize(1);
        assertThat(bindings.getFirst().get("kind")).isEqualTo("FORMAL");
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "SQL-built REGISTERED FORMAL source accepted: " + notes + "\n");
    }
}
