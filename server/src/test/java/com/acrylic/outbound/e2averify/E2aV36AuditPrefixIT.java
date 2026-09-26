package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.audit.PdaAuditOrigins;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeAuditPrefix;
import com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeTransactions;
import com.acrylic.outbound.temporary.protocol.E2aPageItem;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "E2A_AUDIT_PREFIX_VERIFY", matches = "true")
class E2aV36AuditPrefixIT {
    private static final Path ROOT = Path.of("D:/acrylic-pda-worktrees/r06-e2a-migration");
    private static final Path ATTEMPT = ROOT.resolve(".local/e2a-v36-audit-attempt5");
    private static final String DATABASE = "acrylic_outbound_e2a_audit_20260924e";
    private static final String USER = "e2a_audit_20260924e";
    private static final String URL = "jdbc:mysql://127.0.0.1:19330/" + DATABASE
        + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

    @Test void storedFormalAndTemporaryAuditPrefixIsCheckedEventByEvent() throws Exception {
        requireProfile();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(URL, USER, System.getenv("E2A_AUDIT_PREFIX_PASSWORD")));
        requireIdentity(jdbc);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()",
            Integer.class) != 0) throw new IllegalStateException("Audit prefix schema is not empty");
        Files.createFile(ATTEMPT.resolve("test-started.marker"));
        Flyway.configure().dataSource(jdbc.getDataSource()).target("34").load().migrate();
        var seed = E2aV34Seed.seed(jdbc, ATTEMPT.resolve("storage"));
        Flyway.configure().dataSource(jdbc.getDataSource()).target("36").load().migrate();
        verifyEvents(jdbc, seed);
        Files.writeString(ATTEMPT.resolve("test-completed.marker"),
            "nonempty V34 -> V35/V36; full device FORMAL/TEMP audit prefix and rollback checks\n");
    }

    private static void verifyEvents(JdbcTemplate jdbc, Map<String, String> seed) {
        UUID device = UUID.fromString(seed.get("deviceId")), warehouse = UUID.fromString(seed.get("warehouseId"));
        UUID operator = UUID.fromString(seed.get("operatorId"));
        var formal = event(device, operator, 1);
        formal.put("assignmentId", seed.get("assignmentId"));
        insert(jdbc, seed, formal, seed.get("assignmentId"), null, null);
        var temporary = event(device, operator, 2);
        temporary.put("detail", seed.get("mapping0SubmissionId"));
        temporary.set("source", SubmissionJson.object().put("kind", "TEMP")
            .put("creatorDeviceId", device.toString()).put("clientOrderId", seed.get("mapping0ClientOrderId")));
        temporary.put("authorizationId", seed.get("authorizationId"));
        insert(jdbc, seed, temporary, null, seed.get("authorizationId"), seed.get("mapping0ClientOrderId"));
        var transactions = new SourceEvidenceIntakeTransactions(new DataSourceTransactionManager(jdbc.getDataSource()));
        var prefix = new SourceEvidenceIntakeAuditPrefix(jdbc, new PdaAuditOrigins(jdbc));
        transactions.read(status -> { prefix.require(device, warehouse, 3, 2); return null; });
        transactions.read(status -> {
            prefix.requireStopEvent(device, warehouse, operator, UUID.fromString(seed.get("authorizationId")),
                3, 2, temporary.path("id").asText(), UUID.fromString(seed.get("mapping0SubmissionId")));
            return null;
        });
        assertUnverifiable(() -> transactions.read(status -> {
            prefix.requireStopEvent(device, warehouse, operator, UUID.fromString(seed.get("authorizationId")),
                3, 1, formal.path("id").asText(), UUID.fromString(seed.get("mapping0SubmissionId")));
            return null;
        }));
        assertUnverifiable(() -> transactions.read(status -> {
            prefix.requireStopEvent(device, warehouse, operator, UUID.fromString(seed.get("authorizationId")),
                3, 2, UUID.randomUUID().toString(), UUID.fromString(seed.get("mapping0SubmissionId")));
            return null;
        }));
        var raw = SubmissionJson.canonical(temporary).getBytes(StandardCharsets.UTF_8);
        var item = new E2aPageItem("AUDIT", SubmissionJson.uuid(temporary, "id"), "AUDIT_RECORDS",
            UUID.randomUUID(), com.acrylic.outbound.inventory.ImportFiles.sha256(raw), raw.length, 2,
            SubmissionJson.sha(temporary), "SAVED", null, null, null);
        transactions.read(status -> { prefix.requireItem(item, raw, device, warehouse, 3); return null; });
        assertUnverifiable(() -> transactions.read(status -> {
            prefix.requireItem(new E2aPageItem("AUDIT", UUID.randomUUID(), "AUDIT_RECORDS",
                item.originalObjectId(), item.itemBytesSha256(), item.byteCount(), 2,
                item.recordSha256(), "SAVED", null, null, null), raw, device, warehouse, 3);
            return null;
        }));
        transactions.write(status -> {
            jdbc.update("UPDATE pda_audit_event SET record_sha256=? WHERE origin_device_id=? AND local_seq=2",
                "f".repeat(64), Ids.bytes(device));
            assertUnverifiable(() -> prefix.require(device, warehouse, 3, 2));
            status.setRollbackOnly();
            return null;
        });
        transactions.write(status -> {
            jdbc.update("DELETE FROM pda_audit_event WHERE origin_device_id=? AND local_seq=1", Ids.bytes(device));
            assertUnverifiable(() -> prefix.require(device, warehouse, 3, 2));
            status.setRollbackOnly();
            return null;
        });
        transactions.read(status -> { prefix.require(device, warehouse, 3, 2); return null; });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pda_audit_event", Integer.class)).isEqualTo(2);
    }

    private static ObjectNode event(UUID device, UUID operator, long sequence) {
        return SubmissionJson.object().put("id", UUID.randomUUID().toString()).putNull("captureId")
            .put("originDeviceId", device.toString()).put("operatorId", operator.toString())
            .put("localSeq", Long.toString(sequence)).put("type", "STOP").put("detail", "synthetic")
            .put("wallMs", "1").put("elapsedMs", "1").put("boot", 1);
    }

    private static void insert(JdbcTemplate jdbc, Map<String, String> seed, ObjectNode event,
        String assignment, String authorization, String client) {
        jdbc.update("""
            INSERT INTO pda_audit_event(id,origin_device_id,local_seq,warehouse_id,assignment_id,operator_id,
              source_recovery_epoch,record_sha256,payload,uploaded_by,received_at,temporary_authorization_id,client_order_id)
            VALUES(?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(3),?,?)
            """, Ids.bytes(SubmissionJson.uuid(event, "id")), Ids.bytes(UUID.fromString(seed.get("deviceId"))),
            SubmissionJson.number(event, "localSeq"), Ids.bytes(UUID.fromString(seed.get("warehouseId"))),
            bytes(assignment), Ids.bytes(UUID.fromString(seed.get("operatorId"))), 1L,
            SubmissionJson.sha(event), SubmissionJson.canonical(event), Ids.bytes(UUID.fromString(seed.get("operatorId"))),
            bytes(authorization), bytes(client));
    }

    private static byte[] bytes(String value) { return value == null ? null : Ids.bytes(UUID.fromString(value)); }

    private static void assertUnverifiable(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
            error -> assertThat(error.code()).isEqualTo("E2A_AUDIT_PREFIX_UNVERIFIABLE"));
    }

    private static void requireProfile() throws Exception {
        if (!ROOT.resolve("server").toRealPath().equals(Path.of("").toAbsolutePath().toRealPath())
            || !URL.equals(System.getenv("E2A_AUDIT_PREFIX_URL"))
            || !USER.equals(System.getenv("E2A_AUDIT_PREFIX_USER"))
            || System.getenv("E2A_AUDIT_PREFIX_PASSWORD") == null
            || System.getenv("E2A_AUDIT_PREFIX_PASSWORD").length() < 32
            || !Files.isDirectory(ATTEMPT.resolve("data"))
            || Files.exists(ATTEMPT.resolve("test-started.marker")))
            throw new IllegalStateException("Audit prefix one-shot profile mismatch");
        for (String forbidden : new String[] {"OUTBOUND_DB_RESET", "OUTBOUND_DB_REPAIR", "E2A_MIGRATION_RESET"}) {
            if ("true".equals(System.getenv(forbidden))) throw new IllegalStateException("Reset/repair forbidden");
        }
    }

    private static void requireIdentity(JdbcTemplate jdbc) {
        String expected = ATTEMPT.resolve("data").toAbsolutePath().normalize().toString().replace('\\', '/') + "/";
        String actual = jdbc.queryForObject("SELECT @@datadir", String.class).replace('\\', '/');
        if (!expected.equalsIgnoreCase(actual)
            || !DATABASE.equals(jdbc.queryForObject("SELECT DATABASE()", String.class))
            || jdbc.queryForObject("SELECT @@port", Integer.class) != 19330
            || jdbc.queryForObject("SELECT @@log_bin", Integer.class) != 0
            || !(USER + "@127.0.0.1").equals(jdbc.queryForObject("SELECT CURRENT_USER()", String.class)))
            throw new IllegalStateException("Connected MySQL is not the isolated audit target");
    }
}
