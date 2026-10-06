package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.Files;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "B4_RETENTION_VERIFY", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AbortedRetentionStorageIT {
    private RetentionDatabaseFixture f;

    @BeforeAll void provisionNonemptySchema() throws Exception { f = new RetentionDatabaseFixture(); }

    @Test @Order(1) void nonemptyUpgradeAndBackupRestoreKeepHistoricalFacts() {
        assertThat(RetentionDatabaseFixture.historical(f.jdbc)).isEqualTo(f.before);
        assertThat(RetentionDatabaseFixture.historical(f.restore)).isEqualTo(f.before);
        assertThat(f.restore.queryForObject("SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history", Integer.class)).isEqualTo(59);
        assertThat(f.jdbc.queryForObject("SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history", Integer.class)).isEqualTo(64);
        assertThat(RetentionDatabaseFixture.dispositionHistory(f.jdbc)).hasSize(1);
    }

    @Test @Order(2) void retentionKeepsEveryOwnerCycleAndBarcodeProjection() {
        var before = f.stock();
        f.tx.executeWithoutResult(status -> {
            var proof = f.verified(); assertThat(proof.activeBales()).hasSize(3);
            f.apply(proof, Collections.nCopies(3, "RETAIN"));
            assertThat(f.stock()).isEqualTo(before);
            assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM barcode_sync_pending", Integer.class)).isZero();
            status.setRollbackOnly();
        });
        assertThat(f.headers()).isEqualTo(1);
    }

    @Test @Order(2) void historyKeepsSourceRoundIdentity() {
        var first = RetentionDatabaseFixture.dispositionHistory(f.jdbc).getFirst();
        assertThat(first.get("work_round")).isEqualTo(1L);
        assertThat(first.get("scope")).isNotNull();
    }

    @Test @Order(2) void sameBaleCanBeRecordedForAnotherSource() {
        f.tx.executeWithoutResult(status -> {
            UUID bale = f.id("reservedBaleId");
            UUID mappedDisposition = f.header("mapped", false);
            f.item(mappedDisposition, bale, "RELEASE");
            f.jdbc.update("UPDATE submission SET status='ABORTED',receipt_text='synthetic-second-source' WHERE id=UUID_TO_BIN(?)",
                f.seed.get("mapping0SubmissionId"));
            UUID otherDisposition = f.header("mapping0", false);
            f.item(otherDisposition, bale, "RETAIN");
            var rows = RetentionDatabaseFixture.dispositionHistory(f.jdbc);
            String baleHex = HexFormat.of().withUpperCase().formatHex(Ids.bytes(bale));
            assertThat(rows.stream().filter(row -> row.get("bale").equals(baleHex)).count()).isEqualTo(2);
            status.setRollbackOnly();
        });
    }

    @Test @Order(3) void mixedDispositionChangesOnlyReleasedAndQuarantinedBales() {
        f.tx.executeWithoutResult(status -> {
            var proof = f.verified(); UUID disposition = f.apply(proof, List.of("RETAIN", "RELEASE", "QUARANTINE"));
            assertThat(f.jdbc.queryForObject("SELECT state FROM bale_commitment WHERE bale_id=?", String.class,
                Ids.bytes(proof.activeBales().get(0)))).isEqualTo("SOURCE_RESERVED");
            assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE bale_id=?", Integer.class,
                Ids.bytes(proof.activeBales().get(1)))).isZero();
            assertThat(f.jdbc.queryForObject("SELECT HEX(disposition_id) FROM bale_commitment WHERE bale_id=? AND state='QUARANTINED'",
                String.class, Ids.bytes(proof.activeBales().get(2)))).isEqualTo(HexFormat.of().withUpperCase().formatHex(Ids.bytes(disposition)));
            assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM barcode_sync_pending", Integer.class)).isEqualTo(2);
            status.setRollbackOnly();
        });
    }

    @ParameterizedTest @Order(4) @ValueSource(strings = {"REJECTED", "REVIEW_REQUIRED", "SUCCEEDED"})
    void databaseRejectsRetentionOutsideAbortedSources(String state) {
        assertThatThrownBy(() -> f.tx.executeWithoutResult(status -> {
            f.jdbc.update("UPDATE submission SET status=? WHERE id=?", state, Ids.bytes(f.id("mappedSubmissionId")));
            f.item(f.header("mapped", false), f.id("reservedBaleId"), "RETAIN");
        })).hasRootCauseInstanceOf(SQLException.class).hasStackTraceContaining("Only an aborted open source");
        assertThat(f.headers()).isEqualTo(1);
    }

    @Test @Order(5) void databaseRejectsRetentionOnAClosedDisposition() {
        assertThatThrownBy(() -> f.tx.executeWithoutResult(status ->
            f.item(f.header("mapped", true), f.id("reservedBaleId"), "RETAIN")))
            .hasRootCauseInstanceOf(SQLException.class).hasStackTraceContaining("Only an aborted open source");
        assertThat(f.headers()).isEqualTo(1);
    }

    @ParameterizedTest @Order(6) @ValueSource(strings = {
        "UPDATE temporary_physical_disposition SET manifest_version=2 WHERE id=?",
        "DELETE FROM temporary_physical_disposition WHERE id=?",
        "UPDATE temporary_physical_disposition_item SET action='RETAIN' WHERE disposition_id=?",
        "DELETE FROM temporary_physical_disposition_item WHERE disposition_id=?"})
    void historicalDispositionCannotBeRewrittenOrDeleted(String sql) {
        assertThatThrownBy(() -> f.tx.executeWithoutResult(status -> f.jdbc.update(sql, Ids.bytes(f.legacyDisposition))))
            .hasRootCauseInstanceOf(SQLException.class).hasStackTraceContaining("immutable");
        assertThat(RetentionDatabaseFixture.historical(f.jdbc)).isEqualTo(f.before);
    }

    @Test @Order(7) void dispositionHistoryCannotBeRewrittenOrDeleted() {
        var row = f.jdbc.queryForMap("SELECT disposition_id,bale_id FROM temporary_physical_disposition_history LIMIT 1");
        byte[] disposition = (byte[]) row.get("disposition_id");
        byte[] bale = (byte[]) row.get("bale_id");
        assertThatThrownBy(() -> f.tx.executeWithoutResult(status -> f.jdbc.update(
            "UPDATE temporary_physical_disposition_history SET action='RETAIN' WHERE disposition_id=? AND bale_id=?", disposition, bale)))
            .hasRootCauseInstanceOf(SQLException.class).hasStackTraceContaining("Physical disposition history is immutable");
        assertThatThrownBy(() -> f.tx.executeWithoutResult(status -> f.jdbc.update(
            "DELETE FROM temporary_physical_disposition_history WHERE disposition_id=? AND bale_id=?", disposition, bale)))
            .hasRootCauseInstanceOf(SQLException.class).hasStackTraceContaining("Physical disposition history is retained");
        assertThat(RetentionDatabaseFixture.dispositionHistory(f.jdbc)).hasSize(1);
    }

    @Test @Order(7) void lostOwnerRollsBackTheWholeDisposition() {
        var before = f.stock();
        assertThatThrownBy(() -> f.tx.executeWithoutResult(status -> {
            var proof = f.verified();
            f.jdbc.update("DELETE FROM bale_commitment WHERE bale_id=?", Ids.bytes(proof.activeBales().get(2)));
            f.apply(proof, List.of("RELEASE", "QUARANTINE", "RETAIN"));
        })).isInstanceOfSatisfying(BusinessException.class,
            error -> assertThat(error.code()).isEqualTo("DISPOSITION_RESERVATION_CHANGED"));
        assertThat(f.stock()).isEqualTo(before); assertThat(f.headers()).isEqualTo(1);
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM barcode_sync_pending", Integer.class)).isZero();
    }

    @Test @Order(8) void exactItemTriggerFailureRollsBackPriorChanges() {
        var before = f.stock();
        assertThatThrownBy(() -> f.tx.executeWithoutResult(status -> {
            f.jdbc.execute("SET @retention_test_fail=1");
            f.apply(f.verified(), List.of("RELEASE", "QUARANTINE", "RETAIN"));
        })).hasRootCauseInstanceOf(SQLException.class).hasStackTraceContaining("retention test item failure");
        assertThat(f.stock()).isEqualTo(before); assertThat(f.headers()).isEqualTo(1);
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM barcode_sync_pending", Integer.class)).isZero();
    }

    @Test @Order(8) void continuationCommandSignsVerifiesAndReplaysTheRetainedTicket() {
        var before = f.stock();
        f.tx.executeWithoutResult(status -> {
            f.apply(f.verified(), Collections.nCopies(3, "RETAIN"));
            try {
                com.acrylic.outbound.temporary.command.ContinuationDatabaseChecks.verify(f.jdbc, f.seed,
                    f.profile.attempt.resolve("private/continuation-positive"));
            } catch (Exception failure) { throw new IllegalStateException(failure); }
            assertThat(f.stock()).isEqualTo(before);
            status.setRollbackOnly();
        });
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM temporary_source_continuation", Integer.class)).isZero();
        assertThat(f.stock()).isEqualTo(before);
    }

    @ParameterizedTest @Order(9) @ValueSource(strings = {"scope", "cycle", "history"})
    void continuationRejectsAStitchedSourceOrCycle(String mode) {
        ContinuationConstraintChecks.rejectCorruptBinding(f, mode);
    }

    @Test @Order(10) void continuationFinalAuditFailureRollsBackAllWrites() {
        ContinuationConstraintChecks.finalAuditFailureRollsBackTheTicketAndReceipt(f);
    }

    @Test @Order(11) void continuationRowsAreImmutable() {
        ContinuationConstraintChecks.immutableRowsRejectRewrite(f);
    }

    @Test @Order(12) void continuationLifecycleRejectsInvalidStatusEdgesAndIncompleteReceipts() {
        ContinuationConstraintChecks.lifecycleTransitionsRequireAnIssuedTicketAndReceipt(f);
    }

    @Test @Order(99) void concurrentDispositionCommitsOnlyOnceAndPreservesRetainedOwners() throws Exception {
        var before = f.stock(); var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> work = () -> {
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                try { f.tx.executeWithoutResult(status -> f.apply(f.verified(), Collections.nCopies(3, "RETAIN"))); return true; }
                catch (org.springframework.dao.DuplicateKeyException duplicate) { return false; }
            };
            var first = workers.submit(work); var second = workers.submit(work); start.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        }
        assertThat(f.headers()).isEqualTo(2); assertThat(f.stock()).isEqualTo(before);
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM temporary_physical_disposition_item WHERE action='RETAIN'", Integer.class)).isEqualTo(3);
        Files.writeString(f.profile.attempt.resolve("test-completed.marker"), "Synthetic storage suite completed; no HTTP/device validation\n");
    }
}
