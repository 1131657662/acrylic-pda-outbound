package com.acrylic.outbound.recovery;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class RecoveryEffectBatchIT {
    @BeforeAll static void environment() throws Exception { EffectRealEnvironment.requireEnvironment(); }

    @Test void s20StatementBatchRechecksAndClearDropsStoredEffects() throws Exception {
        try (var f = new EffectRealFixture(true, false)) {
            var statement = f.connection.createStatement();
            statement.addBatch(f.insert(1)); statement.addBatch(f.insert(2));
            assertThat(statement.executeBatch()).containsExactly(1, 1);
            assertThat(f.count()).isEqualTo(2);
            statement.addBatch(f.insert(3)); f.deny();
            assertThatThrownBy(statement::executeLargeBatch).hasMessage("RECOVERY_LEASE_OUTSTANDING");
            assertThat(f.calls.count("Statement.executeLargeBatch")).isZero();
            assertThat(f.count()).isEqualTo(2);
            statement.clearBatch();
            assertThat(statement.executeLargeBatch()).isEmpty();
            assertThat(f.count()).isEqualTo(2);
            f.record("S20-statement-batch|deniedDelegateDelta=0|clearThenEmpty=true");
        }
    }

    @Test void s20PreparedNoArgumentBatchRechecksAtLargeExecution() throws Exception {
        try (var f = new EffectRealFixture(true, false)) {
            var statement = f.connection.prepareStatement("INSERT INTO " + f.table + "(id,val) VALUES(?,?)");
            statement.setString(2, "batch");
            for (int id = 1; id <= 3; id++) { statement.setInt(1, id); statement.addBatch(); }
            assertThat(statement.executeLargeBatch()).containsExactly(1L, 1L, 1L);
            assertThat(f.count()).isEqualTo(3);
            statement.setInt(1, 4); statement.addBatch(); f.deny();
            int before = f.calls.count("PreparedStatement.executeBatch");
            assertThatThrownBy(statement::executeBatch).hasMessage("RECOVERY_LEASE_OUTSTANDING");
            assertThat(f.calls.count("PreparedStatement.executeBatch")).isEqualTo(before);
            assertThat(f.count()).isEqualTo(3);
            statement.clearBatch(); assertThat(statement.executeBatch()).isEmpty();
            f.record("S20-prepared-batch|deniedDelegateDelta=0|allowedRows=3");
        }
    }

    @Test void s20AddBatchDenialPreventsEnqueueAndCommit() throws Exception {
        try (var f = new EffectRealFixture(true, false)) {
            var statement = f.connection.createStatement(); f.deny();
            assertThatThrownBy(() -> statement.addBatch(f.insert(1))).hasMessage("RECOVERY_LEASE_OUTSTANDING");
            assertThat(f.calls.count("Statement.addBatch")).isZero();
            statement.clearBatch(); assertThat(statement.executeBatch()).isEmpty();
            assertThat(f.count()).isZero();
            f.record("S20-addBatch-denied|delegateDelta=0");
        }
    }
}
