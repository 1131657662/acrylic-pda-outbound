package com.acrylic.outbound.foundationmigration;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** New-column/backfill assertions are distinct from unchanged original-column comparisons. */
final class FoundationBackfill {
    private final JdbcTemplate jdbc;
    FoundationBackfill(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    void verify() {
        assertThat(count("SELECT COUNT(*) FROM submission_scope WHERE creator_device_id IS NOT NULL AND mapping_version<>IF(canonical_order_id IS NULL,0,1)"))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM submission_scope WHERE creator_device_id IS NULL AND mapping_version<>0")).isZero();
        long sources = count("SELECT COUNT(*) FROM submission_scope WHERE creator_device_id IS NOT NULL");
        assertThat(count("SELECT COUNT(*) FROM temporary_source_mapping WHERE origin='MIGRATED_BASELINE'")).isEqualTo(sources);
        assertThat(count("SELECT COUNT(*) FROM temporary_decision_state WHERE origin='MIGRATED_BASELINE' AND decision_version=0")).isEqualTo(sources);
        assertThat(count("SELECT COUNT(*) FROM temporary_source_mapping m JOIN submission_scope c ON c.id=m.scope_id "
                + "WHERE m.mapping_version<>c.mapping_version OR NOT(m.canonical_order_id<=>c.canonical_order_id)")).isZero();
        assertThat(count("SELECT COUNT(*) FROM order_submission_slot WHERE slot_version<>1")).isZero();
        commitments();
        for (String table : List.of("source_evidence_object", "source_evidence_set", "source_evidence_item", "source_evidence_verification",
                "temporary_command_receipt", "temporary_decision_event"))
            assertThat(count("SELECT COUNT(*) FROM " + LegacyRows.name(table))).as("No fabricated historical facts: %s", table).isZero();
    }

    private void commitments() {
        assertThat(count("SELECT COUNT(*) FROM bale_commitment k JOIN submission s ON s.id=k.submission_id "
                + "JOIN submission_scope c ON c.id=s.scope_id WHERE c.creator_device_id IS NOT NULL AND "
                + "(NOT(k.source_scope_id<=>c.id) OR NOT(k.source_warehouse_id<=>c.warehouse_id))")).isZero();
        assertThat(count("SELECT COUNT(*) FROM bale_commitment k LEFT JOIN submission s ON s.id=k.submission_id "
                + "LEFT JOIN submission_scope c ON c.id=s.scope_id WHERE c.creator_device_id IS NULL "
                + "AND (k.source_scope_id IS NOT NULL OR k.source_warehouse_id IS NOT NULL)")).isZero();
        assertThat(count("SELECT COUNT(*) FROM bale_commitment WHERE source_evidence_set_id IS NOT NULL OR order_id IS NULL")).isZero();
    }

    private long count(String sql) { return jdbc.queryForObject(sql, Long.class); }
}
