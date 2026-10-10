package com.acrylic.outbound.e2averify;

import java.nio.file.Files;
import java.nio.file.Path;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** One-shot local test profile. It cannot target a shared or previously seeded schema. */
final class E2aCrossSourceProfile {
    /**
     * The tree the harness provisions and verifies. It defaults to the shared migration worktree;
     * {@code E2A_CROSS_ROOT} points it at the tree under test, and every other check (attempt under
     * that root, datadir identity, one-shot marker, port, credential) still applies.
     */
    static final Path ROOT = E2aWorktree.ROOT;
    final Path attempt;
    final String database, user, url;

    E2aCrossSourceProfile() throws Exception { this(null); }

    static E2aCrossSourceProfile completedPhysicalReview() throws Exception {
        var profile=new E2aCrossSourceProfile("physical");
        var jdbc=profile.connectChecked();
        require("RECOVERY_PENDING".equals(jdbc.queryForObject("SELECT service_mode FROM server_state WHERE id=1",String.class)),"Restart requires closed state");
        return profile;
    }

    static E2aCrossSourceProfile completedScopeRelease() throws Exception {
        var profile=new E2aCrossSourceProfile("scope"); var jdbc=profile.connectChecked();
        require("RECOVERY_PENDING".equals(jdbc.queryForObject("SELECT service_mode FROM server_state WHERE id=1",String.class)),"Restart requires closed state");
        require(jdbc.queryForObject("SELECT COUNT(*) FROM recovery_scope_release",Integer.class)==1,"Scope DB commit missing");
        return profile;
    }

    private E2aCrossSourceProfile(String restart) throws Exception {
        String run = System.getenv("E2A_CROSS_RUN");
        if (run == null || !run.matches("[1-9][0-9]{0,3}")) throw new IllegalStateException("Invalid run allocation");
        attempt = ROOT.resolve(".local/e2a-cross-20260925-" + run);
        database = "acrylic_e2a_cross_20260925_" + run;
        user = "e2a_cross_20260925_" + run;
        url = "jdbc:mysql://127.0.0.1:19330/" + database
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        require(ROOT.resolve("server").toRealPath().equals(Path.of("").toRealPath()), "Workspace mismatch");
        require(attempt.toRealPath().startsWith(ROOT.toRealPath()), "Attempt escapes workspace");
        require(Files.isDirectory(attempt.resolve("data")), "Missing isolated datadir");
        if("physical".equals(restart)) requireCompletedPhysicalReview(run);
        else if("scope".equals(restart)) requireCompletedScopeRelease(run);
        else require(!Files.exists(attempt.resolve("test-started.marker")), "One-shot attempt already used");
        require(System.getenv("E2A_CROSS_PASSWORD") != null
            && System.getenv("E2A_CROSS_PASSWORD").matches("[0-9a-f]{64}"), "Missing private credential");
        for (String key : new String[]{"OUTBOUND_DB_RESET", "OUTBOUND_DB_REPAIR", "E2A_MIGRATION_RESET"})
            require(!"true".equals(System.getenv(key)), "Reset/repair forbidden");
    }

    JdbcTemplate connect() {
        var jdbc=connectChecked();
        require(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()",
            Integer.class) == 0, "Nonempty schema cannot be reseeded");
        return jdbc;
    }

    private JdbcTemplate connectChecked() {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(url, user, System.getenv("E2A_CROSS_PASSWORD")));
        String actual = jdbc.queryForObject("SELECT @@datadir", String.class).replace('\\', '/');
        String expected = attempt.resolve("data").toString().replace('\\', '/') + "/";
        require(expected.equalsIgnoreCase(actual), "Datadir mismatch");
        require(database.equals(jdbc.queryForObject("SELECT DATABASE()", String.class)), "Schema mismatch");
        require((user + "@127.0.0.1").equals(jdbc.queryForObject("SELECT CURRENT_USER()", String.class)), "User mismatch");
        require(jdbc.queryForObject("SELECT @@port", Integer.class) == 19330, "Port mismatch");
        require(jdbc.queryForObject("SELECT @@log_bin", Integer.class) == 1, "Binlog must remain enabled");
        return jdbc;
    }

    private void requireCompletedPhysicalReview(String run) throws Exception {
        require(Files.exists(attempt.resolve("test-completed.marker"))
            && Files.exists(attempt.resolve("physical-restart-input.json"))
            && Files.exists(attempt.resolve("recovered-fault/approval-receipt.json")),"Physical approval phase incomplete");
        require(!Files.exists(ROOT.resolve(".local/d01-d18-20261010/temporary-evidence-restore-mysql-"+run+"/retention.json")),"Retained attempt cannot be reused");
        var report=new com.fasterxml.jackson.databind.ObjectMapper().readTree(Files.readAllBytes(
            ROOT.resolve(".local/d01-d18-20261008/r09-temporary-evidence-restore-mysql-"+run+"/result.json")));
        require(report.path("exit").asInt(-1)==0 && report.path("tests").asInt()==1 && report.path("sourceUnchanged").asBoolean()
            && report.path("failures").asInt(-1)==0 && report.path("errors").asInt(-1)==0 && report.path("skipped").asInt(-1)==0,"Prior physical phase failed");
        require(report.path("command").toString().contains("approvesPhysicalScopeAcrossOriginalCheckpoint"),"Prior phase is not physical approval");
    }

    private void requireCompletedScopeRelease(String run) throws Exception {
        for(String file:new String[]{"test-completed.marker","physical-restart-input.json","scope-restart-input.json","scope-release-db-committed.json"})
            require(Files.isRegularFile(attempt.resolve(file)),"Scope preparation incomplete");
        require(!Files.exists(attempt.resolve("scope-restart-completed.marker")),"Scope restart already used");
        require(!Files.exists(ROOT.resolve(".local/d01-d18-20261010/temporary-evidence-restore-mysql-"+run+"/retention.json")),"Retained attempt cannot be reused");
        var report=new com.fasterxml.jackson.databind.ObjectMapper().readTree(Files.readAllBytes(
            ROOT.resolve(".local/d01-d18-20261008/r09-temporary-evidence-restore-mysql-"+run+"/result.json")));
        require(report.path("exit").asInt(-1)==0 && report.path("tests").asInt()==1 && report.path("sourceUnchanged").asBoolean()
            && report.path("failures").asInt(-1)==0 && report.path("errors").asInt(-1)==0 && report.path("skipped").asInt(-1)==0,"Prior scope phase failed");
        boolean matched=false;
        for(var argument:report.path("command")) matched|=argument.asText().equals("*E2aReceivedScopeReleaseIT.preparesScopeReleaseWithConfirmedDatabaseCommit");
        require(matched,"Prior phase is not confirmed scope preparation");
    }

    void migrate(JdbcTemplate jdbc, String target) {
        Flyway.configure().dataSource(jdbc.getDataSource()).target(target).load().migrate();
    }

    void migrate(JdbcTemplate jdbc) {
        Flyway.configure().dataSource(jdbc.getDataSource()).load().migrate();
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
