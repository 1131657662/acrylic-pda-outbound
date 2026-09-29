package com.acrylic.outbound.recovery;

import java.sql.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RecoveryEffectCommitIT {
    @BeforeAll static void environment() throws Exception { EffectRealEnvironment.requireEnvironment(); }

    @ParameterizedTest @ValueSource(strings = {"commit", "auto", "raw-commit", "raw-auto", "ddl", "begin"})
    void s19DeniedCommitClassNeverReachesDriverOrCommitsT2(String kind) throws Exception {
        try (var f = new EffectRealFixture(true, false)) {
            f.write(1); f.connection.setAutoCommit(false); f.write(2);
            assertThat(f.count()).isEqualTo(1);
            int before = f.calls.count(key(kind)); f.deny();
            assertThatThrownBy(() -> effect(f, kind)).hasMessage("RECOVERY_LEASE_OUTSTANDING");
            assertThat(f.calls.count(key(kind))).isEqualTo(before);
            assertThat(f.count()).isEqualTo(1);
            f.connection.rollback();
            assertThat(f.count()).isEqualTo(1);
            f.record("S19-denied-" + kind + "|delegateDelta=0|T1=retained|T2=absent");
        }
    }

    @ParameterizedTest @ValueSource(strings = {"commit", "auto", "raw-commit", "raw-auto", "ddl", "begin"})
    void s20AllowedCommitClassHasIndependentlyVisibleEffect(String kind) throws Exception {
        try (var f = new EffectRealFixture(true, false)) {
            f.connection.setAutoCommit(false); f.write(1);
            assertThat(f.count()).isZero();
            int before = f.calls.count(key(kind)); int checks = f.authority.checks;
            effect(f, kind);
            assertThat(f.calls.count(key(kind))).isEqualTo(before + 1);
            assertThat(f.authority.checks).isEqualTo(checks + 1);
            assertThat(f.count()).isEqualTo(1);
            if (kind.equals("auto")) f.connection.setAutoCommit(false);
            f.connection.rollback();
            assertThat(f.count()).isEqualTo(1);
            f.record("S20-allowed-" + kind + "|delegateDelta=1|rollbackCannotUndo=true");
        }
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void s19ConfirmedTerminalAllowsCleanupResetWithUnavailableAuthority(boolean commit) throws Exception {
        try (var f = new EffectRealFixture(true, false)) {
            f.connection.setAutoCommit(false); f.write(1);
            if (commit) f.connection.commit(); else f.connection.rollback();
            int checks = f.authority.checks; f.gate.fence();
            f.connection.setAutoCommit(true);
            assertThat(f.authority.checks).isEqualTo(checks);
            assertThat(f.count()).isEqualTo(commit ? 1 : 0);
            assertThat(f.connection.createStatement().executeQuery("SELECT 1").next()).isTrue();
            f.record("S19-cleanup-" + (commit ? "commit" : "rollback") + "|newGateChecks=0");
        }
    }

    private static void effect(EffectRealFixture f, String kind) throws SQLException {
        switch (kind) {
            case "commit" -> f.connection.commit();
            case "auto" -> f.connection.setAutoCommit(true);
            case "raw-commit" -> f.connection.createStatement().execute("COMMIT");
            case "raw-auto" -> f.connection.createStatement().execute("SET AUTOCOMMIT=1");
            case "ddl" -> f.connection.createStatement().execute("CREATE TABLE " + f.table + "_ddl(id INT PRIMARY KEY)");
            case "begin" -> f.connection.createStatement().execute("START TRANSACTION");
            default -> throw new IllegalArgumentException(kind);
        }
    }

    private static String key(String kind) {
        return switch (kind) {
            case "commit" -> "Connection.commit";
            case "auto" -> "Connection.setAutoCommit";
            default -> "Statement.execute";
        };
    }
}
