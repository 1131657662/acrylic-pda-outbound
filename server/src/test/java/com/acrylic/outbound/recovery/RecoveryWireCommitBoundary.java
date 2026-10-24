package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.*;
import java.sql.*;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** Read-only transaction observer. Transport failure is supplied by real sockets, never by this class. */
final class RecoveryWireCommitBoundary {
    private final Path home;
    private final Supplier<RecoveryOperationRegistry> registry;
    private final AtomicBoolean chosen = new AtomicBoolean();

    RecoveryWireCommitBoundary(Path home, Supplier<RecoveryOperationRegistry> registry) {
        this.home = home; this.registry = registry;
    }

    boolean select() { return Files.isRegularFile(home.resolve("arm-commit.marker")) && chosen.compareAndSet(false, true); }

    void before(Connection connection, String job) throws Exception {
        assertThat(connection.getAutoCommit()).isFalse();
        var operations = registry.get(); var token = operations.bound(Thread.currentThread());
        assertThat(token.kind()).isEqualTo(RecoveryOperationKind.WORKER_JOB);
        var root = operations.live(token);
        assertThat(root.phase()).isEqualTo(RecoveryOperationPhase.SERVING);
        assertThat(root.openSubOperations()).isPositive(); assertThat(root.leaseId()).isNotNull();
        Path p5 = home.getParent().resolve("restored-admission-p5/p5");
        var lease = RecoveryControlHistory.read(p5.resolve("leases/" + root.leaseId() + ".json"));
        var process = RecoveryControlHistory.read(p5.resolve("processes/" + lease.path("processIdentity").asText() + ".json"));
        assertThat(process.path("pid").asLong()).isEqualTo(ProcessHandle.current().pid());
        var fact = SubmissionJson.object().put("pid", ProcessHandle.current().pid()).put("phase", root.phase().toString());
        fact.set("lease", lease); fact.set("process", process);
        fact.set("index", RecoveryControlHistory.read(p5.resolve("leases/index.json")));
        save("worker-commit-held.json", fact);
        observation(connection, job);
        await().atMost(Duration.ofSeconds(120)).until(() -> Files.isRegularFile(home.resolve("commit-release.marker")));
    }

    private void observation(Connection connection, String job) throws Exception {
        var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
        var row = jdbc.queryForMap("SELECT status,attempt_count AS attempts,CAST(payload AS CHAR) AS payload "
            + "FROM background_job WHERE id=UUID_TO_BIN(?)", job);
        assertThat(row.get("status")).isEqualTo("SUCCEEDED");
        var artifact = SubmissionJson.parse(row.get("payload").toString()).path("artifact");
        assertThat(artifact.path("sha256").asText()).matches("[a-f0-9]{64}");
        int audits = jdbc.queryForObject("SELECT COUNT(*) FROM operation_event WHERE request_id=? AND event_type='REPORT_EXPORT_GENERATED'", Integer.class, "r08:" + job);
        assertThat(audits).isEqualTo(1);
        var fact = SubmissionJson.object().put("pid", ProcessHandle.current().pid()).put("job", job)
            .put("driverCommitReturned", false).put("syntheticAckLoss", false).put("auditCount", audits);
        fact.set("row", new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(row));
        fact.set("database", new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(jdbc.queryForMap(
            "SELECT @@server_uuid AS uuid,DATABASE() AS schemaName,@@port AS port,@@datadir AS datadir,CURRENT_USER() AS userName,CONNECTION_ID() AS connectionId")));
        save("jdbc-before-commit.json", fact);
    }

    void failed(Throwable failure) throws Exception {
        assertThat(failure).isInstanceOf(SQLException.class);
        var error = (SQLException) failure;
        var fact = SubmissionJson.object().put("pid", ProcessHandle.current().pid()).put("driverCommitReturned", false)
            .put("syntheticAckLoss", false).put("exceptionClass", error.getClass().getName())
            .put("sqlState", error.getSQLState()).put("message", error.getMessage());
        var stack = new java.io.StringWriter(); error.printStackTrace(new java.io.PrintWriter(stack));
        fact.put("stack", stack.toString());
        save("jdbc-driver-failure.json", fact);
    }

    void unexpectedSuccess() throws Exception {
        Files.writeString(home.resolve("jdbc-driver-returned-success.marker"), "Unexpected successful driver return", StandardOpenOption.CREATE_NEW);
    }

    private void save(String name, com.fasterxml.jackson.databind.JsonNode value) throws Exception {
        Files.writeString(home.resolve(name), value.toPrettyString(), StandardOpenOption.CREATE_NEW);
    }
}
