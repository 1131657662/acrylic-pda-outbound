package com.acrylic.outbound.e2averify;

import java.nio.file.*;
import java.sql.SQLException;
import java.util.List;
import org.springframework.dao.DataAccessException;
import static org.assertj.core.api.Assertions.*;

/** Exact trigger signals plus readback, including existing retained facts and allowed SQL lifecycle. */
final class CorrectionIdentityChecks {
    private final CorrectionIdentityFixture fixture;
    private final Path attempt;
    private final CorrectionAttachmentSqlChecks snapshots;
    private int denial;

    CorrectionIdentityChecks(CorrectionIdentityFixture fixture, Path attempt) {
        this.fixture = fixture; this.attempt = attempt;
        snapshots = new CorrectionAttachmentSqlChecks(fixture.jdbc, attempt);
    }

    void identities() throws Exception {
        denied("UPDATE correction_document SET id=UUID_TO_BIN(UUID()) WHERE id=UUID_TO_BIN(?)",
            "Correction document identity is immutable", fixture.document);
        denied("UPDATE inventory_reconciliation SET id=UUID_TO_BIN(UUID()) WHERE id=UUID_TO_BIN(?)",
            "Inventory reconciliation identity is immutable", fixture.reconciliation);
        assertThat(fixture.jdbc.update("UPDATE correction_document SET id=id WHERE id=UUID_TO_BIN(?)", fixture.document)).isEqualTo(1);
        assertThat(fixture.jdbc.update("UPDATE inventory_reconciliation SET id=id WHERE id=UUID_TO_BIN(?)", fixture.reconciliation)).isEqualTo(1);
        denied("UPDATE correction_document SET reason='changed' WHERE id=UUID_TO_BIN(?)",
            "Correction document facts are immutable", fixture.document);
        denied("UPDATE inventory_reconciliation SET kind='OTHER' WHERE id=UUID_TO_BIN(?)",
            "Inventory reconciliation facts are immutable", fixture.reconciliation);
        denied("DELETE FROM correction_document WHERE id=UUID_TO_BIN(?)", "Correction document is retained", fixture.document);
        denied("DELETE FROM inventory_reconciliation WHERE id=UUID_TO_BIN(?)", "Inventory reconciliation is retained", fixture.reconciliation);
    }

    void lifecycle() throws Exception {
        document("status='APPROVED',approved_by=requested_by,approved_at=UTC_TIMESTAMP(3)");
        document("status='EFFECTIVE',effective_result_version=2");
        document("status='WITHDRAW_PENDING',withdrawal_requested_by=approved_by,withdrawal_requested_at=UTC_TIMESTAMP(3)");
        denied("UPDATE correction_document SET status='EFFECTIVE' WHERE id=UUID_TO_BIN(?)",
            "Pending correction withdrawal can only be confirmed", fixture.document);
        document("status='WITHDRAWN',withdrawal_confirmed_by=requested_by,withdrawal_confirmed_at=UTC_TIMESTAMP(3)");
        for (var pair : List.of(
                new String[]{"approved_by", "Correction approval is append-only"},
                new String[]{"approved_at", "Correction approval time is append-only"},
                new String[]{"effective_result_version", "Correction effective version is append-only"},
                new String[]{"withdrawal_requested_by", "Correction withdrawal requester is append-only"},
                new String[]{"withdrawal_requested_at", "Correction withdrawal request time is append-only"},
                new String[]{"withdrawal_confirmed_by", "Correction withdrawal confirmer is append-only"},
                new String[]{"withdrawal_confirmed_at", "Correction withdrawal confirmation time is append-only"})) {
            denied("UPDATE correction_document SET " + pair[0] + "=NULL WHERE id=UUID_TO_BIN(?)", pair[1], fixture.document);
        }
        denied("UPDATE correction_document SET status='SUBMITTED' WHERE id=UUID_TO_BIN(?)",
            "Withdrawn correction cannot be reopened", fixture.document);
        for (String state : List.of("EVIDENCE_PENDING", "REVIEW_READY", "RESOLVED")) {
            assertThat(fixture.jdbc.update("UPDATE inventory_reconciliation SET status=?,conclusion='checked',"
                + "row_version=row_version+1,updated_at=UTC_TIMESTAMP(3) WHERE id=UUID_TO_BIN(?)", state, fixture.reconciliation)).isEqualTo(1);
        }
    }

    private void document(String changes) {
        assertThat(fixture.jdbc.update("UPDATE correction_document SET " + changes
            + ",row_version=row_version+1 WHERE id=UUID_TO_BIN(?)", fixture.document)).isEqualTo(1);
    }

    private void denied(String sql, String message, String id) throws Exception {
        String label = "guard-" + ++denial;
        var tables = List.of("correction_document", "inventory_reconciliation");
        var before = snapshots.snapshot(tables, label + "-before");
        Throwable failure = catchThrowable(() -> fixture.jdbc.update(sql, id));
        assertThat(failure).isInstanceOf(DataAccessException.class);
        Throwable cause = failure;
        while (cause != null && !(cause instanceof SQLException)) cause = cause.getCause();
        assertThat(cause).isInstanceOf(SQLException.class);
        var error = (SQLException) cause;
        assertThat(error.getSQLState()).isEqualTo("45000");
        assertThat(error.getErrorCode()).isEqualTo(1644);
        assertThat(error.getMessage()).isEqualTo(message);
        assertThat(snapshots.snapshot(tables, label + "-after")).isEqualTo(before);
        Files.writeString(attempt.resolve("identity-denials.log"), label + " SQLSTATE=45000 code=1644 " + message + "\n",
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
