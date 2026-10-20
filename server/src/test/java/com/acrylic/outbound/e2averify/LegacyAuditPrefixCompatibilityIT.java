package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

/** Real isolated MySQL; deliberately stops migrations at the legacy compatibility input. */
@EnabledIfEnvironmentVariable(named = "LEGACY_AUDIT_PREFIX_VERIFY", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LegacyAuditPrefixCompatibilityIT {
    private JdbcTemplate jdbc;
    private LegacyAuditPrefixFixture fixture;
    private TransactionTemplate transactions;
    private Map<String, String> baseline;
    private Path output;

    @BeforeAll void prepareFreshLegacyInput() throws Exception {
        output = Path.of(required("LEGACY_AUDIT_PREFIX_ROOT")).toRealPath();
        assertThat(output.toString().replace('\\', '/')).contains("/.local/");
        assertThat(Files.exists(output.resolve("test-started.marker"))).isFalse();
        String url = "jdbc:mysql://127.0.0.1:19390/acrylic_d04_prefix_20261021_2190";
        assertThat(required("OUTBOUND_DB_URL")).isEqualTo(url);
        assertThat(required("OUTBOUND_DB_USER")).isEqualTo("d04_prefix_2190");
        assertThat(required("OUTBOUND_DB_PASSWORD")).hasSizeGreaterThanOrEqualTo(32);
        jdbc = new JdbcTemplate(new DriverManagerDataSource(url, required("OUTBOUND_DB_USER"), required("OUTBOUND_DB_PASSWORD")));
        requireIdentity();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()", Integer.class)).isZero();
        Files.createFile(output.resolve("test-started.marker"));
        migrate("34");
        fixture = new LegacyAuditPrefixFixture(jdbc, output.resolve("storage"));
        var before = fixture.snapshot();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submission", Integer.class)).isPositive();
        migrate("36");
        var after = fixture.snapshot();
        before.forEach((table, hash) -> {
            if (!table.equals("flyway_schema_history")) assertThat(after.get(table)).as(table).isEqualTo(hash);
        });
        fixture.insertEvents();
        transactions = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        baseline = fixture.snapshot();
        Files.writeString(output.resolve("baseline-tables.json"), SubmissionJson.canonical(LegacyAuditPrefixFixture.JSON.valueToTree(baseline)));
    }

    private void requireIdentity() throws Exception {
        assertThat(jdbc.queryForObject("SELECT @@port", Integer.class)).isEqualTo(19390);
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("acrylic_d04_prefix_20261021_2190");
        assertThat(jdbc.queryForObject("SELECT CURRENT_USER()", String.class)).isEqualTo("d04_prefix_2190@127.0.0.1");
        assertThat(jdbc.queryForObject("SELECT @@log_bin", Integer.class)).isZero();
        assertThat(Path.of(jdbc.queryForObject("SELECT @@datadir", String.class)).toRealPath())
            .isEqualTo(output.resolve("mysql01/data").toRealPath());
    }

    @AfterEach void allTablesUnchanged() {
        assertThat(fixture.snapshot()).isEqualTo(baseline);
        transactions.executeWithoutResult(status -> fixture.requireValid());
    }

    @AfterAll void saveFinalTables() throws Exception {
        if (fixture != null && baseline != null) Files.writeString(output.resolve("final-tables.json"),
            SubmissionJson.canonical(LegacyAuditPrefixFixture.JSON.valueToTree(fixture.snapshot())));
    }

    @Test void sameDeviceFirstRoundStopAcceptsCompleteFormalAndTemporaryPrefix() {
        transactions.executeWithoutResult(status -> fixture.requireValid());
    }

    @Test void oldUnguardedQueryActuallyFailsBecauseContinuationTableIsAbsent() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() "
            + "AND table_name IN ('temporary_source_continuation_submission','temporary_source_continuation_activation')", Integer.class)).isZero();
        assertThatThrownBy(() -> jdbc.queryForList("""
            SELECT BIN_TO_UUID(CASE WHEN s.work_round=1 THEN c.client_order_id ELSE n.new_client_order_id END)
            FROM submission s JOIN submission_scope c ON c.id=s.scope_id
            LEFT JOIN temporary_source_continuation_submission r ON r.submission_id=s.id AND r.scope_id=c.id
              AND r.to_work_round=s.work_round AND r.from_work_round=s.work_round-1
            LEFT JOIN temporary_source_continuation_activation n ON n.ticket_id=r.ticket_id AND n.scope_id=c.id
              AND n.from_work_round=r.from_work_round AND n.new_authorization_id=s.temporary_authorization_id
              AND n.target_device_id=s.freeze_device_id AND n.target_operator_id=s.operator_id
            WHERE s.id=?
            """, Ids.bytes(fixture.submission)))
            .isInstanceOf(BadSqlGrammarException.class);
    }

    @Test void missingFirstEventRejectsCompletePrefixClaim() {
        rejectedMutation("DELETE FROM pda_audit_event WHERE origin_device_id=? AND local_seq=1", Ids.bytes(fixture.device));
    }

    @Test void changedStoredEventHashRejectsPrefix() {
        rejectedMutation("UPDATE pda_audit_event SET record_sha256=REPEAT('f',64) WHERE origin_device_id=? AND local_seq=2", Ids.bytes(fixture.device));
    }

    @Test void futureScopeRoundCannotUseFirstRoundFallback() {
        transactions.executeWithoutResult(status -> {
            fixture.updateScope("work_round", 2);
            rejected(fixture::requireValid);
            status.setRollbackOnly();
        });
    }

    @Test void differentClientOrderCannotReuseOriginalStop() {
        transactions.executeWithoutResult(status -> {
            UUID other = UUID.randomUUID();
            assertThat(jdbc.update("INSERT INTO temporary_work_origin VALUES(?,?,?)", Ids.bytes(fixture.device),
                Ids.bytes(other), Ids.bytes(fixture.authorization))).isEqualTo(1);
            fixture.updateScope("client_order_id", Ids.bytes(other));
            rejected(fixture::requireValid);
            status.setRollbackOnly();
        });
    }

    @Test void authorizationRecoveryEpochMismatchRejectsPrefix() {
        rejectedMutation("UPDATE temporary_authorization SET recovery_epoch=2 WHERE id=?", Ids.bytes(fixture.authorization));
    }

    @Test void authorizationOperatorMismatchRejectsPrefix() {
        rejectedMutation("UPDATE temporary_authorization SET user_id=UUID_TO_BIN(?) WHERE id=UUID_TO_BIN(?)",
            fixture.seed.get("mismatchUserId"), fixture.authorization.toString());
    }

    @Test void foreignWarehouseCannotReadPrefix() {
        rejected(() -> fixture.prefix.require(fixture.device, UUID.randomUUID(), 3, 2));
    }

    @Test void wrongStopEventIdCannotBindSubmission() {
        rejected(() -> stop(fixture.operator, fixture.authorization, UUID.randomUUID().toString(), fixture.submission));
    }

    @Test void wrongOperatorCannotBindStop() {
        rejected(() -> stop(UUID.randomUUID(), fixture.authorization, fixture.temporary.path("id").asText(), fixture.submission));
    }

    @Test void wrongAuthorizationCannotBindStop() {
        rejected(() -> stop(fixture.operator, UUID.randomUUID(), fixture.temporary.path("id").asText(), fixture.submission));
    }

    @Test void wrongSubmissionCannotBindStop() {
        rejected(() -> stop(fixture.operator, fixture.authorization, fixture.temporary.path("id").asText(), UUID.randomUUID()));
    }

    private void stop(UUID operator, UUID authorization, String event, UUID submission) {
        fixture.prefix.requireStopEvent(fixture.device, fixture.warehouse, operator, authorization, 3, 2, event, submission);
    }

    private void rejectedMutation(String sql, Object... arguments) {
        transactions.executeWithoutResult(status -> {
            assertThat(jdbc.update(sql, arguments)).isEqualTo(1);
            rejected(fixture::requireValid);
            status.setRollbackOnly();
        });
    }

    private static void rejected(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
            error -> assertThat(error.code()).isEqualTo("E2A_AUDIT_PREFIX_UNVERIFIABLE"));
    }

    private void migrate(String target) { Flyway.configure().dataSource(jdbc.getDataSource()).target(target).load().migrate(); }
    private static String required(String name) { return java.util.Objects.requireNonNull(System.getenv(name), name); }
}
