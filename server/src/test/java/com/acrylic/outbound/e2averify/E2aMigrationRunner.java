package com.acrylic.outbound.e2averify;

import java.nio.file.Path;
import java.util.Map;
import java.nio.file.Files;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.HashMap;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.JsonNode;

/** Opt-in entry point for future target-34/35 execution; disabled without the full guard. */
public final class E2aMigrationRunner {
    private E2aMigrationRunner() { }

    public static void main(String[] args) throws Exception {
        Map<String, String> env = new HashMap<>(System.getenv());
        Path root = workspace();
        requireProfile(env);
        requireTarget35Resource(root);
        Path marker = createPhaseMarker(root);
        Process server = null;
        Path data = root.resolve(E2aMigrationEnvironment.DATA_DIR).toAbsolutePath().normalize();
        String password = E2aMysqlBootstrap.newPassword();
        env.put("OUTBOUND_DB_PASSWORD", password);
        Throwable primary = null;
        try {
            Process initialized = E2aMysqlLauncher.start(env, root);
            if (initialized.isAlive()) throw new IllegalStateException("MySQL initialize did not finish");
            Exception initializedMarkerFailure = appendMarker(marker, "initialized");
            if (initializedMarkerFailure != null) throw initializedMarkerFailure;
            Path initFile = E2aMysqlBootstrap.write(root, password);
            server = E2aMysqlLauncher.startServer(env, root, initFile);
            E2aMysqlProcessIdentity.verifyStartedProcess(server, data);
            E2aMysqlLauncher.awaitReady(server);
            E2aMysqlProcessIdentity.requireOwnedListener(server, data);
            JdbcTemplate jdbc = E2aMigrationEnvironment.connectLive(env, root);
            E2aMysqlTriggerPreflight.check(jdbc, root.resolve(E2aMigrationEnvironment.EVIDENCE_DIR).resolve("trigger-preflight.json"));
            runMigration(jdbc, root);
        } catch (Throwable failure) {
            primary = failure;
            addMarkerFailure(primary, marker, "failed: " + failure.getClass().getSimpleName());
            if (failure instanceof Exception error) throw error;
            if (failure instanceof Error error) throw error;
            throw new RuntimeException(failure);
        } finally {
            Exception cleanupFailure = null;
            if (server != null) {
                try { E2aMysqlCleanup.stop(server, root, data, password); }
                catch (Exception error) { cleanupFailure = error; }
            }
            boolean portFreeAfter = E2aMysqlLauncher.isPortFree();
            if (!portFreeAfter && cleanupFailure == null) cleanupFailure = new IllegalStateException("19329 listener remains after shutdown");
            Exception markerFailure = appendMarker(marker, cleanupFailure == null && portFreeAfter
                ? (server == null ? "not-started" : "stopped")
                : "stop-failed: " + (cleanupFailure == null ? "listener-remains" : cleanupFailure.getClass().getSimpleName()));
            if (cleanupFailure == null && primary == null && markerFailure == null) markerFailure = appendMarker(marker, "completed");
            if (primary != null) {
                if (cleanupFailure != null) primary.addSuppressed(cleanupFailure);
                if (markerFailure != null) primary.addSuppressed(markerFailure);
            } else if (cleanupFailure != null) throw cleanupFailure;
            else if (markerFailure != null) throw markerFailure;
        }
    }

    static Path workspace() {
        return E2aWorktree.ROOT;
    }

    static boolean portFree() {
        return E2aMysqlLauncher.isPortFree();
    }

    static void requireProfile(Map<String, String> env) {
        E2aMigrationEnvironment.requireExact(env, E2aMysqlLauncher.isPortFree(), workspace());
    }

    private static void runMigration(JdbcTemplate jdbc, Path root) throws Exception {
        Path marker = root.resolve(E2aMigrationEnvironment.TARGET_MARKER);
        if (Files.exists(marker)) throw new IllegalStateException("Existing E2a marker; refusing reset or reseed");
        Integer tables = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()", Integer.class);
        if (tables == null || tables != 0) throw new IllegalStateException("E2a schema is not empty; refusing reset");
        Flyway.configure().dataSource(jdbc.getDataSource()).target("34").load().migrate();
        E2aV34Seed.seed(jdbc, root.resolve(E2aMigrationEnvironment.STORAGE_DIR));
        var snapshot = new E2aMigrationSnapshot(jdbc, root, root.resolve(E2aMigrationEnvironment.STORAGE_DIR));
        JsonNode before = snapshot.capture(); snapshot.saveBefore("before-v35.json");
        Flyway.configure().dataSource(jdbc.getDataSource()).target("35").load().migrate();
        new E2aV35NegativeChecks(jdbc).verify(root.resolve(E2aMigrationEnvironment.EVIDENCE_DIR));
        snapshot.assertPreserved(before); snapshot.saveAfter("after-v35.json");
        Files.createFile(marker);
    }

    private static void requireTarget35Resource(Path root) throws Exception {
        Enumeration<URL> resources = E2aMigrationRunner.class.getClassLoader().getResources("db/migration/V35__source_evidence_intake_receipts.sql");
        if (!resources.hasMoreElements()) throw new IllegalStateException("Required V35 migration resource is missing from classpath");
        URL resource = resources.nextElement();
        if (resources.hasMoreElements()) throw new IllegalStateException("Duplicate V35 migration resources on classpath");
        byte[] bytes;
        try (var stream = resource.openStream()) { bytes = stream.readAllBytes(); }
        String rawHash = HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        String canonicalHash = HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(new String(bytes, java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        Path evidence = root.resolve(E2aMigrationEnvironment.EVIDENCE_DIR).resolve("v35-resource.json");
        Files.createDirectories(evidence.getParent());
        Files.writeString(evidence, "{\"commit\":\"fa7981d7db9253e7ec0a9efbbcb1ce909d0301db\",\"resource\":\"" + resource + "\",\"rawSha256\":\"" + rawHash + "\",\"canonicalSha256\":\"" + canonicalHash + "\"}\n", java.nio.file.StandardOpenOption.CREATE_NEW);
        if (!"CE433B38BDC06A2050C3E4A6C4BA413C8FF57E55EDC1477A65D78667F30F420C".equals(canonicalHash)) {
            throw new IllegalStateException("Unexpected V35 migration resource hash raw=" + rawHash + " canonical=" + canonicalHash);
        }
    }

    private static Path createPhaseMarker(Path root) throws Exception {
        Path marker = root.resolve(E2aMigrationEnvironment.LAUNCH_MARKER);
        Files.createDirectories(marker.getParent());
        Files.createFile(marker);
        Files.writeString(marker, "started\n", java.nio.file.StandardOpenOption.APPEND);
        return marker;
    }

    private static Exception appendMarker(Path marker, String value) {
        try { Files.writeString(marker, value + "\n", java.nio.file.StandardOpenOption.APPEND); return null; }
        catch (Exception error) { return error; }
    }

    private static void addMarkerFailure(Throwable primary, Path marker, String value) {
        Exception markerFailure = appendMarker(marker, value);
        if (markerFailure != null) primary.addSuppressed(markerFailure);
    }

}
