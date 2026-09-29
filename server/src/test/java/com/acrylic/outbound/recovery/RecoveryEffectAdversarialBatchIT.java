package com.acrylic.outbound.recovery;

import java.sql.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class RecoveryEffectAdversarialBatchIT {
    @BeforeAll static void environment() throws Exception { EffectRealEnvironment.requireEnvironment(); }

    @RepeatedTest(3) void partialFailureCannotRetryWithoutSuccessfulClear() throws Exception {
        try (var f = new EffectAdversarialFixture(true, false); var s = f.connection.createStatement()) {
            s.addBatch(f.writeSql());
            s.addBatch("INSERT INTO " + f.table + " VALUES(1,'b','duplicate')");
            assertThatThrownBy(s::executeBatch).isInstanceOf(BatchUpdateException.class);
            assertThat(f.observed()).isEqualTo("1:62:original;2:62:new;");
            assertThatThrownBy(s::executeBatch).hasMessage("CONTROL_EFFECT_UNCLASSIFIED");
            assertThat(f.calls.count("Statement.executeBatch")).isEqualTo(1);
            s.clearBatch();
            assertThat(s.executeBatch()).isEmpty();
            assertThat(f.observed()).isEqualTo("1:62:original;2:62:new;");
            f.record("partial-batch|firstPartialCommit=1|retryDelta=0|clearThenEmpty=true");
        }
    }

    @RepeatedTest(3) void failedClearDoesNotEraseRememberedRisk() throws Exception {
        try (var f = new EffectAdversarialFixture(true, false); var s = f.connection.createStatement()) {
            s.addBatch(f.writeSql());
            f.calls.failNext("Statement.clearBatch", new SQLException("MODEL-clear-failure", "42000"));
            assertThatThrownBy(s::clearBatch).hasMessage("MODEL-clear-failure");
            assertThatThrownBy(s::executeBatch).hasMessage("CONTROL_EFFECT_UNCLASSIFIED");
            assertThat(f.calls.count("Statement.executeBatch")).isZero();
            assertThat(f.observed()).isEqualTo("1:62:original;");
            EffectRealEnvironment.record("MODEL|injected clearBatch SQLState=42000; not a driver failure claim");
            f.record("clear-fault-guard|executeDelta=0");
            s.clearBatch();
            assertThat(s.executeBatch()).isEmpty();
        }
    }

    @RepeatedTest(3) void clearParametersDoesNotClearPreviouslyAddedBatch() throws Exception {
        try (var f = new EffectAdversarialFixture(true, false)) {
            var s = f.connection.prepareStatement("INSERT INTO " + f.table + " VALUES(?,'b','new')");
            s.setInt(1, 2); s.addBatch(); s.clearParameters();
            f.gate.fence();
            assertThatThrownBy(s::executeBatch).hasMessage("RECOVERY_LEASE_FENCED");
            assertThat(f.calls.count("PreparedStatement.executeBatch")).isZero();
            assertThat(f.observed()).isEqualTo("1:62:original;");
            f.record("clearParameters-retains-batch|executeDelta=0");
        }
    }

    @RepeatedTest(3) void verifiedEmptyBatchHasNoDatabaseEffectEvenFenced() throws Exception {
        try (var f = new EffectAdversarialFixture(true, false); var s = f.connection.createStatement()) {
            assertThatThrownBy(s::executeBatch).hasMessage("CONTROL_EFFECT_UNCLASSIFIED");
            s.clearBatch(); f.gate.fence();
            assertThat(s.executeBatch()).isEmpty();
            assertThat(s.executeLargeBatch()).isEmpty();
            assertThat(f.observed()).isEqualTo("1:62:original;");
            f.record("empty-batch|unboundDenied=true|confirmedEmptyDelegated=true|noCommit=true");
        }
    }
}
