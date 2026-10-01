package com.acrylic.outbound.e2averify;

import java.nio.file.*;
import java.sql.SQLException;
import java.util.*;
import org.springframework.dao.DataAccessException;
import static org.assertj.core.api.Assertions.*;

/** Exact constraint attribution and unchanged readback for every refused insert or mutation. */
final class CorrectionAccountingMigrationChecks {
    private final CorrectionAccountingMigrationFixture f;
    private final Path attempt;
    private final CorrectionAttachmentSqlChecks snapshots;
    private int denial;
    private record Error(String state, int code, String message) { }

    CorrectionAccountingMigrationChecks(CorrectionAccountingMigrationFixture fixture, Path attempt) {
        f = fixture; this.attempt = attempt; snapshots = new CorrectionAttachmentSqlChecks(f.jdbc, attempt);
    }

    void references() throws Exception {
        String original = f.insert("REVERSAL", null, f.inventory, null);
        String restored = f.insert("WITHDRAWAL_RESTORE", null, null, original);
        f.insert("REVERSAL", f.oldWeight, null, null);
        denied(() -> f.insert("REVERSAL", null, f.inventory, null), new Error("23000", 1062, "uq_correction_original_reversal"));
        denied(() -> f.insert("WITHDRAWAL_RESTORE", null, null, original), new Error("23000", 1062, "uq_correction_withdrawal_restore"));
        denied(() -> f.insert("REVERSAL", null, UUID.randomUUID().toString(), null), new Error("23000", 1452, "fk_correction_original_reversal"));
        denied(() -> f.insert("WITHDRAWAL_RESTORE", null, null, UUID.randomUUID().toString()), new Error("23000", 1452, "fk_correction_withdrawal_restore"));
        kinds(); guards(original, restored);
    }

    private void kinds() throws Exception {
        denied(() -> f.insert("UNKNOWN", null, null, null), new Error("HY000", 3819, "ck_correction_posting_kind"));
        denied(() -> f.insert("REVERSAL", f.oldReversal, f.inventory, null), new Error("HY000", 3819, "ck_correction_single_reference"));
        denied(() -> f.insert("WEIGHT_DIFF", null, f.inventory, null), new Error("HY000", 3819, "ck_correction_reference_kind"));
        denied(() -> f.insert("WEIGHT_DIFF", f.oldReversal, null, null), new Error("HY000", 3819, "ck_correction_reference_kind"));
        denied(() -> f.insert("REVERSAL", null, null, f.oldReversal), new Error("HY000", 3819, "ck_correction_reference_kind"));
        denied(() -> f.insert("WITHDRAWAL_RESTORE", null, null, null), new Error("HY000", 3819, "ck_correction_reference_kind"));
    }

    private void guards(String original, String restored) throws Exception {
        var update = new Error("45000", 1644, "Correction ledger is append-only");
        var delete = new Error("45000", 1644, "Correction ledger is retained");
        denied(() -> f.jdbc.update("UPDATE correction_ledger SET reverses_inventory_entry_id=NULL WHERE id=UUID_TO_BIN(?)", original), update);
        denied(() -> f.jdbc.update("UPDATE correction_ledger SET restores_entry_id=NULL WHERE id=UUID_TO_BIN(?)", restored), update);
        for (String id : List.of(original, restored, f.oldWeight, f.oldReversal)) {
            denied(() -> f.jdbc.update("UPDATE correction_ledger SET id=UUID_TO_BIN(UUID()) WHERE id=UUID_TO_BIN(?)", id), update);
            denied(() -> f.jdbc.update("DELETE FROM correction_ledger WHERE id=UUID_TO_BIN(?)", id), delete);
        }
    }

    private void denied(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, Error expected) throws Exception {
        String label = "accounting-denial-" + ++denial;
        var tables = List.of("correction_ledger"); var before = snapshots.snapshot(tables, label + "-before");
        Throwable failure = catchThrowable(action);
        assertThat(failure).isInstanceOf(DataAccessException.class);
        Throwable cause = failure;
        while (cause != null && !(cause instanceof SQLException)) cause = cause.getCause();
        assertThat(cause).isInstanceOf(SQLException.class); var error = (SQLException) cause;
        assertThat(error.getSQLState()).isEqualTo(expected.state());
        assertThat(error.getErrorCode()).isEqualTo(expected.code());
        assertThat(error.getMessage()).contains(expected.message());
        assertThat(snapshots.snapshot(tables, label + "-after")).isEqualTo(before);
        Files.writeString(attempt.resolve("accounting-sql-denials.log"), label + " SQLSTATE=" + error.getSQLState()
            + " code=" + error.getErrorCode() + " " + error.getMessage() + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
