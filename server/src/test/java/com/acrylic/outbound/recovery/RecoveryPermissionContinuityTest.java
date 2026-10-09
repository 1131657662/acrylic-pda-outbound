package com.acrylic.outbound.recovery;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.assertj.core.api.Assertions.*;

class RecoveryPermissionContinuityTest {
    @Test void refusesSameNamedReplacementEvenWithNewValidEndPin() {
        var f = new RecoveryPermissionLogFixture(); var original = f.request(); byte[] log = original.binlog();
        log[25] = '9';
        f.end.put("binlogPrefixSha256", RecoveryControlFormat.sha256(log));
        byte[] end = RecoveryControlJson.encode(f.end);
        var request = new RecoveryPermissionContinuity.Request(f.expected, original.begin(), original.beginSha256(), end,
            RecoveryControlFormat.sha256(end), log, RecoveryControlFormat.sha256(log));
        assertThatThrownBy(() -> RecoveryPermissionContinuity.verify(request)).hasMessage("RECOVERY_PERMISSION_LOG_PREFIX_CHANGED");
    }

    @Test void refusesMissingIndependentPin() {
        var f = new RecoveryPermissionLogFixture(); var input = f.request();
        var request = new RecoveryPermissionContinuity.Request(f.expected, input.begin(), null, input.end(), input.endSha256(), input.binlog(), input.binlogSha256());
        assertThatThrownBy(() -> RecoveryPermissionContinuity.verify(request)).hasMessage("RECOVERY_PERMISSION_PIN");
    }

    @Test void verifiesCompleteLoggedRowIntervalWithoutGrantingHistoricalAuthority() {
        var f = new RecoveryPermissionLogFixture();
        var proof = RecoveryPermissionContinuity.verify(f.request());
        assertThat(proof.committedTransactions()).isEqualTo(1); assertThat(proof.rowEvents()).isEqualTo(1);
        assertThat(proof.historicalAuthorityEstablished()).isFalse(); assertThat(proof.ordinaryWritesAllowed()).isFalse();
        assertThat(proof.localDeletionAllowed()).isFalse();
    }

    @Test void acceptsEmptyLoggedIntervalAtClosedTransactionBoundary() {
        var f = new RecoveryPermissionLogFixture(); byte[] log = Arrays.copyOf(f.log.toByteArray(), f.start);
        f.end.put("binlogPosition", Integer.toString(f.start));
        assertThat(RecoveryPermissionContinuity.verify(f.request(log)).committedTransactions()).isZero();
    }

    @Test void verifiesMysqlMutableBinlogInUseFlagWithoutChangingTheOriginalBytes() {
        var f = new RecoveryPermissionLogFixture(); byte[] log = f.log.toByteArray(); log[4 + 17] = 1;
        byte[] original = log.clone();
        assertThat(RecoveryPermissionContinuity.verify(f.request(log)).committedTransactions()).isEqualTo(1);
        assertThat(log).isEqualTo(original);
    }

    @Test void refusesUnknownFormatFlag() {
        var f = new RecoveryPermissionLogFixture(); byte[] log = f.log.toByteArray(); log[4 + 17] = 2;
        assertThatThrownBy(() -> RecoveryPermissionContinuity.verify(f.request(log))).hasMessage("RECOVERY_BINLOG_FORMAT_FLAGS");
    }

    @ParameterizedTest @ValueSource(strings={"app_user", "device", "role", "permission", "user_role", "role_permission", "user_warehouse", "warehouse", "server_state", "pda_session", "unknown"})
    void rejectsEveryAuthorityTableAndUnknownTableEvenWithIdenticalSnapshots(String table) {
        var f = new RecoveryPermissionLogFixture(); f.replaceTransaction(table);
        assertThatThrownBy(() -> RecoveryPermissionContinuity.verify(f.request())).hasMessage("RECOVERY_BINLOG_AUTHORITY_OR_UNKNOWN_TABLE");
    }

    @ParameterizedTest @ValueSource(ints={3,4,15,26,27,28,29,36,39,40,41})
    void rejectsUnsupportedEventTypes(int type) {
        var f = new RecoveryPermissionLogFixture(); f.event(type, new byte[10]); f.end.put("binlogPosition", Integer.toString(f.log.size()));
        assertThatThrownBy(() -> RecoveryPermissionContinuity.verify(f.request())).hasMessage("RECOVERY_BINLOG_EVENT_UNSUPPORTED");
    }

    @Test void rejectsChecksumCorruptionEvenWhenOuterPinMatchesChangedBytes() {
        var f = new RecoveryPermissionLogFixture(); byte[] log = f.log.toByteArray(); log[f.start + 20] ^= 1;
        assertThatThrownBy(() -> RecoveryPermissionContinuity.verify(f.request(log))).hasMessage("RECOVERY_BINLOG_CHECKSUM");
    }

    @Test void rejectsBrokenEventPositionWithValidChecksum() {
        var f = new RecoveryPermissionLogFixture(); byte[] log = f.log.toByteArray();
        byte[] event = Arrays.copyOfRange(log, f.start, f.start + 79); RecoveryPermissionLogFixture.put(event, 13, f.start + 80, 4);
        RecoveryPermissionLogFixture.crc(event); System.arraycopy(event, 0, log, f.start, event.length);
        assertThatThrownBy(() -> RecoveryPermissionContinuity.verify(f.request(log))).hasMessage("RECOVERY_BINLOG_POSITION");
    }

    @Test void rejectsTruncatedXidAndOpenTransaction() {
        var f = new RecoveryPermissionLogFixture(); byte[] log = Arrays.copyOf(f.log.toByteArray(), f.finish - 31);
        f.end.put("binlogPosition", Integer.toString(log.length));
        assertThatThrownBy(() -> RecoveryPermissionContinuity.verify(f.request(log))).hasMessage("RECOVERY_BINLOG_BOUNDARY");
    }

    @Test void rejectsBoundaryInsideTransaction() {
        var f = new RecoveryPermissionLogFixture(); f.begin.put("binlogPosition", Integer.toString(f.start + 79));
        assertThatThrownBy(() -> RecoveryPermissionContinuity.verify(f.request())).hasMessage("RECOVERY_BINLOG_START_TRANSACTION");
    }

    @Test void rejectsDdlInWindow() {
        var f = new RecoveryPermissionLogFixture(); f.query("ALTER TABLE submission ADD unsafe INT");
        f.end.put("binlogPosition", Integer.toString(f.log.size()));
        assertThatThrownBy(() -> RecoveryPermissionContinuity.verify(f.request())).hasMessage("RECOVERY_BINLOG_STATEMENT");
    }

    @ParameterizedTest @ValueSource(longs={-1,1000000})
    void rejectsCommitOutsideHalfOpenBoundary(long offset) {
        var f = new RecoveryPermissionLogFixture(); f.micros = java.time.Instant.parse("2026-10-10T01:00:00Z").getEpochSecond() * 1000000 + offset;
        f.replaceTransaction("submission");
        assertThatThrownBy(() -> RecoveryPermissionContinuity.verify(f.request())).hasMessage("RECOVERY_BINLOG_COMMIT_TIME");
    }

    @Test void rejectsRotation() {
        var f = new RecoveryPermissionLogFixture(); f.end.put("binlogFile", "binlog.000002");
        assertThatThrownBy(() -> RecoveryPermissionContinuity.verify(f.request())).hasMessage("RECOVERY_PERMISSION_ROTATION_UNSUPPORTED");
    }

    @Test void rejectsChangedEffectiveState() {
        var f = new RecoveryPermissionLogFixture(); ((com.fasterxml.jackson.databind.node.ObjectNode)f.end.path("effective")).put("userAuthVersion", "2");
        assertThatThrownBy(() -> RecoveryPermissionContinuity.verify(f.request())).hasMessage("RECOVERY_PERMISSION_BOUNDARY_CHANGED");
    }
}
