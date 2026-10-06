package com.acrylic.outbound.e2averify;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.assertThat;

/** Focused V64 proof that lifecycle edges require append-only activation/closure rows. */
@EnabledIfEnvironmentVariable(named = "B4_RETENTION_VERIFY", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TemporaryContinuationLifecycleMigrationIT {
    private RetentionDatabaseFixture fixture;

    @BeforeAll void provision() throws Exception { fixture = new RetentionDatabaseFixture(); }

    @Test void migrationCreatesClosureRelationAndKeepsProductionCapabilitiesOutOfScope() {
        assertThat(fixture.jdbc.queryForObject("SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history", Integer.class))
            .isEqualTo(64);
        assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='temporary_source_continuation_closure'", Integer.class))
            .isEqualTo(1);
        assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND trigger_name IN ('temp_source_continuation_state_update','temp_source_continuation_closure_immutable_update','temp_source_continuation_closure_immutable_delete')", Integer.class))
            .isEqualTo(3);
    }

    @Test void lifecycleEdgesRejectMissingProofAndRetainImmutableRows() {
        ContinuationConstraintChecks.lifecycleTransitionsRequireAnIssuedTicketAndReceipt(fixture);
    }
}
