package com.acrylic.outbound.e2averify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

final class E2aMysqlTriggerPreflight {
    private E2aMysqlTriggerPreflight() { }

    static void check(JdbcTemplate jdbc, Path evidence) throws IOException {
        Snapshot snapshot = read(jdbc);
        String reason = blockingReason(snapshot);
        Files.writeString(evidence, new ObjectMapper().writeValueAsString(Map.of(
            "snapshot", snapshot, "result", reason == null ? "preconditions-met" : "blocked",
            "reason", reason == null ? "" : reason)) + "\n", StandardOpenOption.CREATE_NEW);
        if (reason != null) throw new IllegalStateException("E2a V35 trigger preflight blocked: " + reason);
    }

    private static Snapshot read(JdbcTemplate jdbc) {
        String grantee = "'" + E2aMigrationEnvironment.USER + "'@'127.0.0.1'";
        return new Snapshot(
            jdbc.queryForObject("SELECT DATABASE()", String.class),
            jdbc.queryForObject("SELECT CURRENT_USER()", String.class),
            jdbc.queryForObject("SELECT @@VERSION", String.class),
            jdbc.queryForObject("SELECT @@GLOBAL.log_bin", Integer.class),
            jdbc.queryForObject("SELECT @@GLOBAL.log_bin_trust_function_creators", Integer.class),
            jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.SCHEMA_PRIVILEGES WHERE GRANTEE=? AND TABLE_SCHEMA=DATABASE() AND PRIVILEGE_TYPE='TRIGGER'", Integer.class, grantee),
            jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.USER_PRIVILEGES WHERE GRANTEE=? AND PRIVILEGE_TYPE='SUPER'", Integer.class, grantee));
    }

    private static String blockingReason(Snapshot snapshot) {
        if (!E2aMigrationEnvironment.DATABASE.equals(snapshot.database())
                || !(E2aMigrationEnvironment.USER + "@127.0.0.1").equals(snapshot.principal())) return "unexpected schema or principal";
        if (!Integer.valueOf(1).equals(snapshot.logBin())) return "binary logging must remain enabled";
        if (!Integer.valueOf(0).equals(snapshot.superGrants())) return "migration account must not have SUPER";
        if (!Integer.valueOf(1).equals(snapshot.triggerGrants())) return "migration account requires schema TRIGGER privilege";
        if (!Integer.valueOf(1).equals(snapshot.trustFunctionCreators())) return "binlog-enabled trigger creation needs an approved log_bin_trust_function_creators policy";
        return null;
    }

    private record Snapshot(String database, String principal, String serverVersion, Integer logBin,
        Integer trustFunctionCreators, Integer triggerGrants, Integer superGrants) { }
}
