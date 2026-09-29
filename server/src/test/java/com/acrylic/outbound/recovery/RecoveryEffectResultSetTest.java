package com.acrylic.outbound.recovery;

import java.sql.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecoveryEffectResultSetTest {
    @ParameterizedTest @ValueSource(strings = {"insertRow", "updateRow", "deleteRow", "moveToInsertRow"})
    void s20CursorMutatorCannotReachDriverAfterDenial(String method) throws Exception {
        try (var f = new EffectUnitFixture(true)) {
            var raw = mock(ResultSet.class);
            var statement = mock(Statement.class);
            when(f.physical.createStatement()).thenReturn(statement);
            when(statement.executeQuery("SELECT id FROM t")).thenReturn(raw);
            var result = f.connection.createStatement().executeQuery("SELECT id FROM t");
            f.deny();
            assertThatThrownBy(() -> ResultSet.class.getMethod(method).invoke(result))
                .hasCauseInstanceOf(SQLException.class).hasRootCauseMessage("RECOVERY_LEASE_OUTSTANDING");
            verifyNoInteractions(raw);
        }
    }

    @Test void s20uUnknownCursorMethodIsNeverDelegated() throws Exception {
        try (var f = new EffectUnitFixture(true)) {
            var raw = mock(ResultSet.class);
            var statement = mock(Statement.class);
            when(f.physical.createStatement()).thenReturn(statement);
            when(statement.executeQuery("SELECT id FROM t")).thenReturn(raw);
            var result = f.connection.createStatement().executeQuery("SELECT id FROM t");
            assertThatThrownBy(result::refreshRow).hasMessage("CONTROL_EFFECT_UNCLASSIFIED");
            verifyNoInteractions(raw);
            result.next(); result.getInt(1);
            assertThat(f.authority.checks).isZero();
        }
    }
}
