package com.acrylic.outbound.foundationmigration;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = "spring.flyway.enabled=false")
@Transactional
@EnabledIfEnvironmentVariable(named = "FOUNDATION_SCHEMA", matches = "true")
class FoundationSchemaIT {
    @Autowired JdbcTemplate jdbc;
    @DynamicPropertySource static void guard(DynamicPropertyRegistry properties) {
        var jdbc = FoundationEnvironment.jdbc();
        assertThat(FoundationEnvironment.database()).isEqualTo(FoundationEnvironment.DATABASE);
        assertThat(jdbc.queryForObject("SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success=1", Integer.class)).isEqualTo(34);
    }

    @Test void sourceReservedAllowsNullOrderWithoutInventingOldOwner() {
        var f = new SourceConstraintData(jdbc, 0); f.evidence();
        f.reserve(f.bale(), null, (byte[]) f.source.get("scope"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE state='SOURCE_RESERVED' AND order_id IS NULL "
                + "AND submission_id IS NULL AND recovery_package_id IS NULL AND continuation_id IS NULL AND pc_work_id IS NULL", Integer.class)).isEqualTo(1);
    }

    @Test void oldOwnerCannotBorrowNullableOrderBranch() {
        assertThatThrownBy(() -> jdbc.update("UPDATE bale_commitment SET order_id=NULL WHERE state='REVIEW_RESERVED' LIMIT 1"))
                .isInstanceOf(DataAccessException.class).hasRootCauseMessage("Check constraint 'ck_commitment_owner' is violated.");
    }

    @Test void sourceCannotReserveForeignWarehouseBale() {
        var f = new SourceConstraintData(jdbc, 0); f.evidence();
        var other = new SourceConstraintData(jdbc, 1);
        assertThat((byte[]) other.source.get("warehouse")).isNotEqualTo((byte[]) f.source.get("warehouse"));
        assertThatThrownBy(() -> f.reserve(other.bale(), null, (byte[]) f.source.get("scope")))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("foreign key constraint fails");
    }

    @Test void sourceCannotReferenceForeignWarehouseOrder() {
        var f = new SourceConstraintData(jdbc, 0); f.evidence();
        byte[] wrong = jdbc.queryForObject("SELECT id FROM outbound_order WHERE warehouse_id<>? ORDER BY id LIMIT 1", byte[].class, f.source.get("warehouse"));
        assertThatThrownBy(() -> f.reserve(f.bale(), wrong, (byte[]) f.source.get("scope")))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("foreign key constraint fails");
    }

    @Test void sourceCannotBorrowEvidenceSetFromAnotherScope() {
        var f = new SourceConstraintData(jdbc, 0); f.evidence();
        var other = new SourceConstraintData(jdbc, 1); other.evidence();
        assertThat((byte[]) other.source.get("scope")).isNotEqualTo((byte[]) f.source.get("scope"));
        byte[] bale = f.bale(); f.reserve(bale, null, (byte[]) f.source.get("scope"));
        assertThatThrownBy(() -> jdbc.update("UPDATE bale_commitment SET source_evidence_set_id=? WHERE bale_id=?", other.set, bale))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("foreign key constraint fails");
    }

    @Test void formalScopeCannotBecomeTemporaryEvidenceOwner() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO source_evidence_object(id,warehouse_id,scope_id,submission_id,work_round,kind,original_object_id,"
                + "bytes_sha256,byte_count,file_key,origin_device_id,uploaded_by,received_at) "
                + "SELECT UUID_TO_BIN(UUID()),c.warehouse_id,c.id,s.id,s.work_round,'STOP_PROOF',UUID_TO_BIN(UUID()),REPEAT('a',64),2,"
                + "'synthetic',s.freeze_device_id,s.operator_id,UTC_TIMESTAMP(3) FROM submission_scope c JOIN submission s ON s.scope_id=c.id "
                + "WHERE c.creator_device_id IS NULL ORDER BY c.id LIMIT 1")).isInstanceOf(DataAccessException.class).hasMessageContaining("foreign key constraint fails");
    }

    @Test void evidenceScanCannotReferenceForeignWarehouseBale() {
        var f = new SourceConstraintData(jdbc, 0); f.evidence();
        var other = new SourceConstraintData(jdbc, 1);
        assertThat((byte[]) other.source.get("warehouse")).isNotEqualTo((byte[]) f.source.get("warehouse"));
        assertThatThrownBy(() -> f.item(other.bale())).isInstanceOf(DataAccessException.class).hasMessageContaining("foreign key constraint fails");
    }

    @Test void copiedSourceColumnsCannotBeNullToBypassCompositeForeignKeys() {
        var f = new SourceConstraintData(jdbc, 0); f.evidence();
        f.reserve(f.bale(), null, (byte[]) f.source.get("scope"));
        assertThatThrownBy(() -> jdbc.update("UPDATE bale_commitment SET source_warehouse_id=NULL WHERE source_evidence_set_id=?", f.set))
                .isInstanceOf(DataAccessException.class).hasRootCauseMessage("Check constraint 'ck_commitment_source_warehouse' is violated.");
    }
}
