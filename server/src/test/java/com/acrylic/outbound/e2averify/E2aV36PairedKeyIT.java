package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.devices.DeviceCrypto;
import com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeDeviceKeys;
import com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeTransactions;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "E2A_KEY_VERIFY", matches = "true")
class E2aV36PairedKeyIT {
    private static final Path ROOT = E2aWorktree.ROOT;
    private static final Path ATTEMPT = ROOT.resolve(".local/e2a-v36-key-attempt1");
    private static final String DATABASE = "acrylic_outbound_e2a_key_20260924a";
    private static final String USER = "e2a_key_20260924a";
    private static final String URL = "jdbc:mysql://127.0.0.1:19330/" + DATABASE
        + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

    @Test void completedPairingBindsRegisteredKeyAndRejectsTampering() throws Exception {
        requireProfile();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(URL, USER, System.getenv("E2A_KEY_PASSWORD")));
        requireIdentity(jdbc);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()",
            Integer.class) != 0) throw new IllegalStateException("Paired key target schema is not empty");
        Files.createFile(ATTEMPT.resolve("test-started.marker"));
        Flyway.configure().dataSource(jdbc.getDataSource()).target("34").load().migrate();
        Map<String, String> seed = E2aV34Seed.seed(jdbc, ATTEMPT.resolve("storage"));
        Flyway.configure().dataSource(jdbc.getDataSource()).target("35").load().migrate();
        Flyway.configure().dataSource(jdbc.getDataSource()).target("36").load().migrate();
        verifyPairedKey(jdbc, seed);
        Files.writeString(ATTEMPT.resolve("test-completed.marker"),
            "nonempty V34 -> V35/V36; synthetic completed pairing and negative key checks\n");
    }

    private static void verifyPairedKey(JdbcTemplate jdbc, Map<String, String> seed) throws Exception {
        UUID device = UUID.fromString(seed.get("deviceId")), warehouse = UUID.fromString(seed.get("warehouseId"));
        var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        byte[] der = generator.generateKeyPair().getPublic().getEncoded();
        String encoded = Base64.getEncoder().encodeToString(der), hash = DeviceCrypto.sha(encoded);
        var transactions = new SourceEvidenceIntakeTransactions(new DataSourceTransactionManager(jdbc.getDataSource()));
        var keys = new SourceEvidenceIntakeDeviceKeys(jdbc);
        assertUnverifiable(() -> transactions.read(status -> keys.trusted(warehouse, device)));
        jdbc.update("UPDATE device SET public_key=?,key_sha256=? WHERE id=?", encoded, hash, Ids.bytes(device));
        jdbc.update("""
            INSERT INTO device_pairing(id,public_key,key_sha256,credential_sha256,request_sha256,
            model,short_code,status,created_at,expires_at,recovery_epoch,approval_json,
            approval_sha256,approved_by,approved_auth_version,device_id)
            VALUES(?,?,?,?,?,'E2a fixture','E2AKEY01','COMPLETED',UTC_TIMESTAMP(3),
                   DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 10 MINUTE),1,'{}',?, ?,1,?)
            """, Ids.bytes(UUID.randomUUID()), encoded, hash, "a".repeat(64), "b".repeat(64),
            "c".repeat(64), Ids.bytes(UUID.fromString(seed.get("operatorId"))), Ids.bytes(device));
        byte[] registered = transactions.read(status -> keys.trusted(warehouse, device).getEncoded());
        assertThat(registered).containsExactly(der);
        assertUnverifiable(() -> transactions.read(status -> keys.trusted(UUID.randomUUID(), device)));
        transactions.write(status -> {
            jdbc.update("UPDATE device SET key_sha256=? WHERE id=?", "f".repeat(64), Ids.bytes(device));
            assertUnverifiable(() -> keys.trusted(warehouse, device));
            status.setRollbackOnly();
            return null;
        });
        byte[] afterRollback = transactions.read(status -> keys.trusted(warehouse, device).getEncoded());
        assertThat(afterRollback).containsExactly(der);
    }

    private static void assertUnverifiable(Runnable lookup) {
        assertThatThrownBy(lookup::run).isInstanceOfSatisfying(BusinessException.class,
            error -> assertThat(error.code()).isEqualTo("E2A_STOP_KEY_UNVERIFIABLE"));
    }

    private static void requireProfile() throws Exception {
        if (!ROOT.resolve("server").toRealPath().equals(Path.of("").toAbsolutePath().toRealPath())
            || !URL.equals(System.getenv("E2A_KEY_URL")) || !USER.equals(System.getenv("E2A_KEY_USER"))
            || System.getenv("E2A_KEY_PASSWORD") == null || System.getenv("E2A_KEY_PASSWORD").length() < 32
            || !Files.isDirectory(ATTEMPT.resolve("data"))
            || Files.exists(ATTEMPT.resolve("test-started.marker")))
            throw new IllegalStateException("Paired key one-shot profile mismatch");
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
            || !(USER + "@127.0.0.1").equals(jdbc.queryForObject("SELECT CURRENT_USER()", String.class)))
            throw new IllegalStateException("Connected MySQL is not the isolated paired-key target");
    }
}
