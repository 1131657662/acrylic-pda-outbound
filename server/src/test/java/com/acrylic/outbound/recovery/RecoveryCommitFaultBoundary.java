package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.*;
import java.sql.Connection;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** Pauses the actual worker completion before commit and after driver success, before synthetic 08007. */
final class RecoveryCommitFaultBoundary {
    private final Path home;
    private final Supplier<RecoveryOperationRegistry> registry;
    private final AtomicBoolean chosen = new AtomicBoolean();

    RecoveryCommitFaultBoundary(Path home, Supplier<RecoveryOperationRegistry> registry) {
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
        observation(connection, job, "jdbc-before-commit.json", false);
        waitFor("commit-release.marker");
    }

    void committed(Connection connection, String job) throws Exception {
        observation(connection, job, "jdbc-after-commit-before-ack.json", true);
        waitFor("lose-ack.marker");
    }

    private void observation(Connection connection, String job, String name, boolean committed) throws Exception {
        var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
        var row = jdbc.queryForMap("SELECT status,attempt_count AS attempts,CAST(payload AS CHAR) AS payload "
            + "FROM background_job WHERE id=UUID_TO_BIN(?)", job);
        assertThat(row.get("status")).isEqualTo("SUCCEEDED");
        var artifact = SubmissionJson.parse(row.get("payload").toString()).path("artifact");
        assertThat(artifact.path("sha256").asText()).matches("[a-f0-9]{64}");
        int audits = jdbc.queryForObject("SELECT COUNT(*) FROM operation_event WHERE request_id=? AND event_type='REPORT_EXPORT_GENERATED'", Integer.class, "r08:" + job);
        assertThat(audits).isEqualTo(1);
        var fact = SubmissionJson.object().put("pid", ProcessHandle.current().pid()).put("job", job)
            .put("driverCommitReturned", committed).put("syntheticAckLoss", true).put("auditCount", audits);
        fact.set("row", new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(row));
        fact.set("database", new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(jdbc.queryForMap(
            "SELECT @@server_uuid AS uuid,DATABASE() AS schemaName,@@port AS port,@@datadir AS datadir,CURRENT_USER() AS userName,CONNECTION_ID() AS connectionId")));
        save(name, fact);
    }

    private void save(String name, com.fasterxml.jackson.databind.JsonNode value) throws Exception {
        Files.writeString(home.resolve(name), value.toPrettyString(), StandardOpenOption.CREATE_NEW);
    }

    private void waitFor(String name) {
        await().atMost(Duration.ofSeconds(120)).until(() -> Files.isRegularFile(home.resolve(name)));
    }
}
