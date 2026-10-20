package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

/** Only first-round same-device facts; installed continuation tables stay empty. */
@EnabledIfEnvironmentVariable(named = "AUDIT_SCHEMA_BRANCH_VERIFY", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuditPrefixSchemaBranchesIT {
    private Path root;
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;
    private LegacyAuditPrefixFixture fixture;
    private Map<String, String> retained;

    @BeforeAll void prepareFreshNonemptyInput() throws Exception {
        root = Path.of(required("AUDIT_SCHEMA_BRANCH_ROOT")).toRealPath();
        assertThat(root.toString().replace('\\', '/')).contains("/.local/");
        assertThat(required("OUTBOUND_DB_URL")).isEqualTo("jdbc:mysql://127.0.0.1:19396/acrylic_d04_branches_20261021_2196");
        assertThat(required("OUTBOUND_DB_USER")).isEqualTo("d04_branches_2196");
        jdbc = new JdbcTemplate(new DriverManagerDataSource(required("OUTBOUND_DB_URL"),
            required("OUTBOUND_DB_USER"), required("OUTBOUND_DB_PASSWORD")));
        requireIdentity();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()", Integer.class)).isZero();
        Files.createFile(root.resolve("test-started.marker"));
        migrate("34");
        fixture = new LegacyAuditPrefixFixture(jdbc, root.resolve("storage"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submission", Integer.class)).isPositive();
        migrate("36"); fixture.insertEvents();
        transactions = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        retained = retainedFacts();
    }

    private void requireIdentity() throws Exception {
        assertThat(jdbc.queryForObject("SELECT @@port", Integer.class)).isEqualTo(19396);
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("acrylic_d04_branches_20261021_2196");
        assertThat(jdbc.queryForObject("SELECT CURRENT_USER()", String.class)).isEqualTo("d04_branches_2196@127.0.0.1");
        assertThat(jdbc.queryForObject("SELECT @@log_bin", Integer.class)).isZero();
        assertThat(Path.of(jdbc.queryForObject("SELECT @@datadir", String.class)).toRealPath())
            .isEqualTo(root.resolve("mysql01/data").toRealPath());
    }

    @Test void firstRoundSourceBindingSurvivesNonemptySchemaUpgrade() throws Exception {
        verify("36", 0); verify("63", 1); verify("66", 2); verify("latest", 2);
    }

    private void verify(String stage, int expectedInstalled) throws Exception {
        migrate(stage);
        assertThat(retainedFacts()).isEqualTo(retained);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() "
            + "AND table_name IN ('temporary_source_continuation_submission','temporary_source_continuation_activation')",
            Integer.class)).isEqualTo(expectedInstalled);
        requireCancelledTablesEmpty();
        var baseline = fixture.snapshot();
        positive();
        rejectedMutation(() -> fixture.updateScope("work_round", 2));
        rejectedUpdate("UPDATE temporary_authorization SET recovery_epoch=2 WHERE id=?", Ids.bytes(fixture.authorization));
        rejectedUpdate("UPDATE submission SET operator_id=UUID_TO_BIN(?) WHERE id=?",
            fixture.seed.get("mismatchUserId"), Ids.bytes(fixture.submission));
        rejectedUpdate("UPDATE submission SET source_recovery_epoch=2 WHERE id=?", Ids.bytes(fixture.submission));
        rejectedUpdate("UPDATE pda_audit_event SET record_sha256=REPEAT('f',64) WHERE origin_device_id=? AND local_seq=2",
            Ids.bytes(fixture.device));
        assertThat(fixture.snapshot()).isEqualTo(baseline);
        Files.writeString(root.resolve("stage-" + stage + "-tables.json"), SubmissionJson.canonical(
            LegacyAuditPrefixFixture.JSON.valueToTree(baseline)));
        positive(); requireCancelledTablesEmpty();
    }

    private void positive() { transactions.executeWithoutResult(status -> fixture.requireValid()); }

    private Map<String, String> retainedFacts() {
        var result = new java.util.TreeMap<String, String>();
        var queries = Map.of(
            "events", "SELECT id,origin_device_id,local_seq,record_sha256,payload FROM pda_audit_event ORDER BY id",
            "origins", "SELECT creator_device_id,client_order_id,authorization_id FROM temporary_work_origin ORDER BY creator_device_id,client_order_id",
            "authorizations", "SELECT id,user_id,device_id,warehouse_id,recovery_epoch FROM temporary_authorization ORDER BY id",
            "submissions", "SELECT id,scope_id,freeze_device_id,operator_id,work_round,source_recovery_epoch,status FROM submission ORDER BY id");
        queries.forEach((name, sql) -> result.put(name, SubmissionJson.sha(LegacyAuditPrefixFixture.JSON.valueToTree(
            jdbc.queryForList(sql).stream().map(this::retainedRow).toList()))));
        return result;
    }

    private Map<String, String> retainedRow(Map<String, Object> row) {
        var normalized = new java.util.TreeMap<String, String>();
        row.forEach((key, value) -> normalized.put(key, value == null ? null
            : value instanceof byte[] bytes ? "binary:" + java.util.Base64.getEncoder().encodeToString(bytes)
            : value.getClass().getName() + ":" + value));
        return normalized;
    }

    private void rejectedUpdate(String sql, Object... parameters) {
        rejectedMutation(() -> assertThat(jdbc.update(sql, parameters)).isEqualTo(1));
    }

    private void rejectedMutation(Runnable mutation) {
        var baseline = fixture.snapshot();
        transactions.executeWithoutResult(status -> {
            mutation.run();
            assertThatThrownBy(() -> fixture.prefix.requireStopEvent(fixture.device, fixture.warehouse,
                fixture.operator, fixture.authorization, 3, 2, fixture.temporary.path("id").asText(), fixture.submission))
                .isInstanceOfSatisfying(BusinessException.class,
                    error -> assertThat(error.code()).isEqualTo("E2A_AUDIT_PREFIX_UNVERIFIABLE"));
            status.setRollbackOnly();
        });
        assertThat(fixture.snapshot()).isEqualTo(baseline);
        positive();
    }

    private void requireCancelledTablesEmpty() {
        for (String table : List.of("temporary_source_continuation_submission", "temporary_source_continuation_activation")) {
            int installed = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema=DATABASE() AND table_name=?", Integer.class, table);
            if (installed == 1) assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class)).isZero();
        }
    }

    private void migrate(String target) {
        var configuration = Flyway.configure().dataSource(jdbc.getDataSource());
        if (!target.equals("latest")) configuration.target(target);
        configuration.load().migrate();
    }

    private static String required(String name) { return java.util.Objects.requireNonNull(System.getenv(name), name); }
}
