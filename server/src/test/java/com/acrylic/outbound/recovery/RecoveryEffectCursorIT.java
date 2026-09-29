package com.acrylic.outbound.recovery;

import java.sql.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;

class RecoveryEffectCursorIT {
    @BeforeAll static void environment() throws Exception { EffectRealEnvironment.requireEnvironment(); }

    @ParameterizedTest @CsvSource({"insert,false", "update,false", "delete,false", "insert,true", "update,true", "delete,true"})
    void s20UpdatableCursorHasBidirectionalDriverAndCommitAssertions(String operation, boolean deny) throws Exception {
        try (var f = new EffectRealFixture(true, false)) {
            f.write(1);
            var statement = f.connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
            try (var rows = statement.executeQuery("SELECT id,val FROM " + f.table)) {
                prepare(rows, operation);
                if (deny) {
                    f.deny();
                    assertThatThrownBy(() -> change(rows, operation)).hasMessage("RECOVERY_LEASE_OUTSTANDING");
                    assertThat(f.calls.count("ResultSet." + operation + "Row")).isZero();
                    assertThat(f.count()).isEqualTo(1); assertThat(f.value(1)).isEqualTo("value");
                } else {
                    change(rows, operation);
                    assertThat(f.calls.count("ResultSet." + operation + "Row")).isEqualTo(1);
                    assertThat(f.count()).isEqualTo(operation.equals("insert") ? 2 : operation.equals("delete") ? 0 : 1);
                    if (operation.equals("update")) assertThat(f.value(1)).isEqualTo("updated");
                }
                f.record("S20-resultset-" + operation + "|denied=" + deny);
            }
        }
    }

    @Test void s20uUnknownCursorMethodNeverReachesDriver() throws Exception {
        try (var f = new EffectRealFixture(true, false)) {
            f.write(1);
            var statement = f.connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
            try (var rows = statement.executeQuery("SELECT id,val FROM " + f.table)) {
                assertThat(rows.next()).isTrue();
                assertThatThrownBy(rows::refreshRow).hasMessage("CONTROL_EFFECT_UNCLASSIFIED");
                assertThat(f.calls.count("ResultSet.refreshRow")).isZero();
                assertThat(f.count()).isEqualTo(1);
                f.record("S20-u-resultset-refresh|delegateDelta=0");
            }
        }
    }

    private static void prepare(ResultSet rows, String operation) throws SQLException {
        if (operation.equals("insert")) {
            rows.moveToInsertRow(); rows.updateInt(1, 2); rows.updateString(2, "inserted");
        } else {
            assertThat(rows.next()).isTrue();
            if (operation.equals("update")) rows.updateString(2, "updated");
        }
    }

    private static void change(ResultSet rows, String operation) throws SQLException {
        switch (operation) {
            case "insert" -> rows.insertRow();
            case "update" -> rows.updateRow();
            case "delete" -> rows.deleteRow();
            default -> throw new IllegalArgumentException(operation);
        }
    }
}
