package com.acrylic.outbound.recovery;

import java.nio.file.Files;
import java.sql.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecoveryClosedConnectionAdmissionTest {
    @Test void missingAuthorityRefusesWithoutTouchingOrClosingTheBorrow() throws Exception {
        var fixture = RecoveryControlFixture.create();
        Files.move(fixture.root.resolve("current.json"), fixture.root.getParent().resolve("retained-current.json"));
        var physical = mock(Connection.class);

        assertRefused(fixture, physical, "AUTHORITY_UNAVAILABLE");

        verifyNoInteractions(physical);
    }

    @Test void corruptIntentRefusesWithoutTouchingOrClosingTheBorrow() throws Exception {
        var fixture = RecoveryControlFixture.create();
        fixture.toReleaseReady();
        RecoveryControlFixture.normal(fixture, "model_schema").write();
        Files.move(fixture.root.resolve(RecoveryNormalIntent.FILE),
            fixture.root.getParent().resolve("retained-normal-intent.json"));
        Files.writeString(fixture.root.resolve(RecoveryNormalIntent.FILE), "{}");
        var physical = mock(Connection.class);

        assertRefused(fixture, physical, "DURABLE_INTENT_UNAVAILABLE");

        verifyNoInteractions(physical);
    }

    @Test void unavailableDatabaseEvidenceRefusesAndLeavesCloseToTheAdapter() throws Exception {
        var fixture = RecoveryControlFixture.create();
        fixture.toReleaseReady();
        RecoveryControlFixture.normal(fixture, "model_schema").write();
        var physical = mock(Connection.class);
        when(physical.createStatement()).thenThrow(new SQLException("MODEL-query-unavailable"));

        assertRefused(fixture, physical, "DATABASE_EVIDENCE_UNAVAILABLE");

        verify(physical).createStatement();
        verify(physical, never()).close();
        verify(physical, never()).commit();
        verify(physical, never()).rollback();
    }

    private static void assertRefused(RecoveryControlFixture fixture, Connection physical, String gap) {
        var check = new RecoveryClosedConnectionAdmission(fixture.location(), fixture.checkpoint, 1);
        assertThatThrownBy(() -> check.verify(physical))
            .isInstanceOfSatisfying(SQLNonTransientConnectionException.class,
                denied -> assertThat(denied.getSQLState()).isEqualTo("08004"))
            .hasMessage("RECOVERY_DATABASE_ADMISSION_REFUSED:" + gap);
    }
}
