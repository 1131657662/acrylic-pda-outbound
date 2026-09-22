package com.acrylic.outbound.e2averify;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.ArrayList;
import java.util.List;

/** W2-owned negative-check entry point; each check must assert rollback independently. */
public final class E2aV35NegativeChecks {
    private E2aV35NegativeChecks() { }
    public static void verify(JdbcTemplate jdbc) {
        requireTable(jdbc, "source_evidence_intake_receipt");
        requireTable(jdbc, "source_evidence_object");
        requireTable(jdbc, "source_evidence_set");
        requireTable(jdbc, "source_evidence_verification");
        Integer before = jdbc.queryForObject("SELECT COUNT(*) FROM source_evidence_intake_receipt", Integer.class);
        if (before == null || before < 1) throw new IllegalStateException("Negative checks require a valid intake receipt");
        List<String> evidence = new ArrayList<>();
        var tx = new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource());
        expectFailure(tx, jdbc, "UPDATE source_evidence_intake_receipt SET receipt_version=2", "immutable update", evidence);
        expectFailure(tx, jdbc, "DELETE FROM source_evidence_intake_receipt", "immutable delete", evidence);
        expectFailure(tx, jdbc, "UPDATE source_evidence_intake_receipt SET operation_kind='OPEN' WHERE operation_kind='VERIFY'", "stage mismatch", evidence);
        Integer after = jdbc.queryForObject("SELECT COUNT(*) FROM source_evidence_intake_receipt", Integer.class);
        if (!before.equals(after)) throw new IllegalStateException("Negative case changed intake count");
    }

    private static void expectFailure(org.springframework.transaction.PlatformTransactionManager tx, JdbcTemplate jdbc, String sql, String label, List<String> evidence) {
        try {
            new TransactionTemplate(tx).executeWithoutResult(status -> jdbc.update(sql));
            throw new IllegalStateException("Negative case unexpectedly succeeded: " + label);
        } catch (org.springframework.dao.DataAccessException expected) {
            String state = expected.getMostSpecificCause() instanceof java.sql.SQLException sqlError ? sqlError.getSQLState() : "";
            int code = expected.getMostSpecificCause() instanceof java.sql.SQLException sqlError ? sqlError.getErrorCode() : -1;
            evidence.add(label + "|" + sql + "|sqlState=" + state + "|vendorCode=" + code);
            if (!"45000".equals(state) && !"23000".equals(state)) throw new IllegalStateException("Unexpected SQLState for " + label + ": " + state, expected);
        }
    }

    private static void requireTable(JdbcTemplate jdbc, String table) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?", Integer.class, table);
        if (!Integer.valueOf(1).equals(count)) throw new IllegalStateException("Missing V35 table: " + table);
    }
}
