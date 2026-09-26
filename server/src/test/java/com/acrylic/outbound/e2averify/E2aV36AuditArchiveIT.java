package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.audit.AuditArchiveReader;
import com.acrylic.outbound.audit.AuditArchiveStore;
import com.acrylic.outbound.audit.PdaAuditOrigins;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.common.JsonText;
import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.submissions.SubmissionArchiveProofs;
import com.acrylic.outbound.submissions.SubmissionFiles;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeAuditArchives;
import com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeAuditPrefix;
import com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeTransactions;
import com.acrylic.outbound.temporary.protocol.E2aPageItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
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

@EnabledIfEnvironmentVariable(named = "E2A_AUDIT_ARCHIVE_VERIFY", matches = "true")
class E2aV36AuditArchiveIT {
    private static final Path ROOT = Path.of("D:/acrylic-pda-worktrees/r06-e2a-migration");
    private static final Path ATTEMPT = ROOT.resolve(".local/e2a-v36-audit-attempt6");
    private static final String DATABASE = "acrylic_outbound_e2a_audit_20260924f";
    private static final String USER = "e2a_audit_20260924f";
    private static final String URL = "jdbc:mysql://127.0.0.1:19330/" + DATABASE
        + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

    @Test void signedArchivePageItemBindsIndexFileAndOriginalEvents() throws Exception {
        requireProfile();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(URL, USER,
            System.getenv("E2A_AUDIT_ARCHIVE_PASSWORD")));
        requireIdentity(jdbc);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()",
            Integer.class) != 0) throw new IllegalStateException("Audit archive schema is not empty");
        Files.createFile(ATTEMPT.resolve("test-started.marker"));
        Flyway.configure().dataSource(jdbc.getDataSource()).target("34").load().migrate();
        var seed = E2aV34Seed.seed(jdbc, ATTEMPT.resolve("storage"));
        Flyway.configure().dataSource(jdbc.getDataSource()).target("36").load().migrate();
        verifyArchive(jdbc, seed);
        Files.writeString(ATTEMPT.resolve("test-completed.marker"),
            "nonempty V34 -> V35/V36; signed archive index/file and original event binding\n");
    }

    private static void verifyArchive(JdbcTemplate jdbc, Map<String, String> seed) throws Exception {
        UUID device = UUID.fromString(seed.get("deviceId"));
        UUID warehouse = UUID.fromString(seed.get("warehouseId"));
        UUID operator = UUID.fromString(seed.get("operatorId"));
        var formal = event(device, operator, 1).put("assignmentId", seed.get("assignmentId"));
        insert(jdbc, seed, formal, seed.get("assignmentId"), null, null);
        var temporary = event(device, operator, 2);
        temporary.set("source", SubmissionJson.object().put("kind", "TEMP")
            .put("creatorDeviceId", device.toString()).put("clientOrderId", seed.get("mapping0ClientOrderId")));
        temporary.put("authorizationId", seed.get("authorizationId"));
        insert(jdbc, seed, temporary, null, seed.get("authorizationId"), seed.get("mapping0ClientOrderId"));
        var signer = signer();
        var files = new SubmissionFiles(ATTEMPT.resolve("storage").toString());
        var body = SubmissionJson.object().put("originDeviceId", device.toString())
            .put("afterSeq", "0").put("throughSeq", "2");
        body.putArray("events").add(formal).add(temporary);
        byte[] bodyBytes = SubmissionJson.canonical(body).getBytes(StandardCharsets.UTF_8);
        String payloadSha = files.store(bodyBytes);
        var proof = SubmissionJson.object().put("archiveVersion", 1)
            .put("originDeviceId", device.toString()).put("warehouseId", warehouse.toString())
            .put("recoveryEpoch", "2").put("afterSeq", "0").put("throughSeq", "2")
            .put("payloadSha256", payloadSha).put("byteCount", Integer.toString(bodyBytes.length))
            .put("archivedAt", "2026-09-24T00:00:00Z").put("retainUntil", "2031-09-24T00:00:00Z")
            .put("storagePolicy", "SERVER_DURABLE_FILES_V1").put("localDeletionAllowed", false);
        var claims = SubmissionJson.object().put("iss", "acrylic-outbound")
            .put("aud", "acrylic-pda-audit-archive");
        claims.set("archive", proof.deepCopy());
        proof.put("signedArchive", signer.sign(claims).token());
        new AuditArchiveStore(jdbc).insert("2", device, proof);
        var itemBytes = SubmissionJson.canonical(proof).getBytes(StandardCharsets.UTF_8);
        var item = new E2aPageItem("ARCHIVE",
            SourceEvidenceIntakeAuditArchives.recordId(device, 2, 0), "AUDIT_ARCHIVE", UUID.randomUUID(),
            ImportFiles.sha256(itemBytes), itemBytes.length, 2, SubmissionJson.sha(proof), "SAVED",
            null, null, null);
        var transactions = new SourceEvidenceIntakeTransactions(new DataSourceTransactionManager(jdbc.getDataSource()));
        var prefix = new SourceEvidenceIntakeAuditPrefix(jdbc, new PdaAuditOrigins(jdbc));
        var archives = new SourceEvidenceIntakeAuditArchives(new AuditArchiveStore(jdbc),
            new AuditArchiveReader(files, new SubmissionArchiveProofs(signer)), prefix);
        transactions.read(status -> {
            archives.requireItem(item, itemBytes,
                new SourceEvidenceIntakeAuditArchives.Context(device, warehouse, 2, 2));
            return null;
        });
        var changed = proof.deepCopy().put("signedArchive", "invalid");
        byte[] changedBytes = SubmissionJson.canonical(changed).getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> transactions.read(status -> {
            archives.requireItem(item, changedBytes,
                new SourceEvidenceIntakeAuditArchives.Context(device, warehouse, 2, 2));
            return null;
        })).isInstanceOf(com.acrylic.outbound.common.BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pda_audit_archive", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pda_audit_event", Integer.class)).isEqualTo(2);
    }

    private static OfflineSigner signer() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        var pair = generator.generateKeyPair();
        Files.write(ATTEMPT.resolve("private/private.pk8"), pair.getPrivate().getEncoded());
        Files.write(ATTEMPT.resolve("private/public.der"), pair.getPublic().getEncoded());
        return new OfflineSigner(ATTEMPT.resolve("private/private.pk8").toString(),
            ATTEMPT.resolve("private/public.der").toString(), new JsonText(new ObjectMapper()));
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

    private static void requireProfile() throws Exception {
        if (!ROOT.resolve("server").toRealPath().equals(Path.of("").toAbsolutePath().toRealPath())
            || !URL.equals(System.getenv("E2A_AUDIT_ARCHIVE_URL"))
            || !USER.equals(System.getenv("E2A_AUDIT_ARCHIVE_USER"))
            || System.getenv("E2A_AUDIT_ARCHIVE_PASSWORD") == null
            || System.getenv("E2A_AUDIT_ARCHIVE_PASSWORD").length() < 32
            || !Files.isDirectory(ATTEMPT.resolve("data")) || Files.exists(ATTEMPT.resolve("test-started.marker")))
            throw new IllegalStateException("Audit archive one-shot profile mismatch");
        for (String forbidden : new String[] {"OUTBOUND_DB_RESET", "OUTBOUND_DB_REPAIR", "E2A_MIGRATION_RESET"})
            if ("true".equals(System.getenv(forbidden))) throw new IllegalStateException("Reset/repair forbidden");
    }

    private static void requireIdentity(JdbcTemplate jdbc) {
        String expected = ATTEMPT.resolve("data").toAbsolutePath().normalize().toString().replace('\\', '/') + "/";
        String actual = jdbc.queryForObject("SELECT @@datadir", String.class).replace('\\', '/');
        if (!expected.equalsIgnoreCase(actual) || !DATABASE.equals(jdbc.queryForObject("SELECT DATABASE()", String.class))
            || jdbc.queryForObject("SELECT @@port", Integer.class) != 19330
            || jdbc.queryForObject("SELECT @@log_bin", Integer.class) != 0
            || !(USER + "@127.0.0.1").equals(jdbc.queryForObject("SELECT CURRENT_USER()", String.class)))
            throw new IllegalStateException("Connected MySQL is not the isolated audit archive target");
    }
}
