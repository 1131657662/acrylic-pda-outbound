package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Restricted original source accounts. Queries never select password hashes or authentication material. */
final class RecoveryPermissionWriterProfile {
    private RecoveryPermissionWriterProfile() { }

    static ObjectNode capture(JdbcTemplate jdbc, String schema) {
        var value = SubmissionJson.object().put("currentAccount", jdbc.queryForObject("SELECT CURRENT_USER()", String.class));
        var accounts = value.putArray("accounts");
        jdbc.query("SELECT User,Host,account_locked FROM mysql.user ORDER BY User,Host LIMIT 1025", (rs, n) ->
            SubmissionJson.object().put("user", rs.getString(1)).put("host", rs.getString(2))
                .put("locked", "Y".equals(rs.getString(3)))).forEach(accounts::add);
        value.put("roleEdges", jdbc.queryForObject("SELECT COUNT(*) FROM mysql.role_edges", String.class));
        value.put("mandatoryRoles", jdbc.queryForObject("SELECT @@global.mandatory_roles", String.class));
        runtime(jdbc, value);
        value.put("sessionCannotDisableLogging", cannotDisableLogging(jdbc));
        var grants = value.putArray("grants"); jdbc.queryForList("SHOW GRANTS FOR CURRENT_USER", String.class).forEach(grants::add);
        verify(value, schema); return value;
    }

    static void verify(JsonNode profile, String schema) {
        fields(profile, "currentAccount accounts roleEdges mandatoryRoles grants exclusiveCurrentSession eventScheduler replicationThreads sessionCannotDisableLogging");
        String current = text(profile, "currentAccount");
        require(current.matches("[A-Za-z0-9_]{1,64}@127\\.0\\.0\\.1"), "RECOVERY_PERMISSION_WRITER_ACCOUNT");
        require(revision(profile, "roleEdges") == 0 && text(profile, "mandatoryRoles").isEmpty(), "RECOVERY_PERMISSION_WRITER_ROLES");
        require(profile.path("exclusiveCurrentSession").isBoolean() && profile.path("exclusiveCurrentSession").booleanValue(),
            "RECOVERY_PERMISSION_WRITER_SESSIONS");
        require(profile.path("sessionCannotDisableLogging").isBoolean() && profile.path("sessionCannotDisableLogging").booleanValue(),
            "RECOVERY_PERMISSION_WRITER_SESSION_PRIVILEGES");
        require(text(profile, "eventScheduler").equals("OFF") && revision(profile, "replicationThreads") == 0,
            "RECOVERY_PERMISSION_WRITER_BACKGROUND");
        var accounts = SubmissionJson.array(profile, "accounts");
        require(!accounts.isEmpty() && accounts.size() <= 1024, "RECOVERY_PERMISSION_WRITER_ACCOUNTS");
        var seen = new HashSet<String>(); int active = 0;
        for (var account : accounts) {
            fields(account, "user host locked"); String identity = text(account, "user") + "@" + text(account, "host");
            require(account.path("locked").isBoolean() && seen.add(identity), "RECOVERY_PERMISSION_WRITER_ACCOUNTS");
            if (!account.path("locked").booleanValue()) {
                require(identity.equals(current), "RECOVERY_PERMISSION_WRITER_PRIVILEGED_ACCOUNT"); active++;
            }
        }
        require(active == 1, "RECOVERY_PERMISSION_WRITER_ACCOUNTS"); grants(profile, schema, current);
    }

    private static boolean cannotDisableLogging(JdbcTemplate jdbc) {
        try {
            // Assign the existing value: a privileged stale session succeeds without changing logging.
            jdbc.execute("SET SESSION sql_log_bin=@@session.sql_log_bin"); return false;
        } catch (org.springframework.dao.DataAccessException denied) {
            require(denied.getMostSpecificCause() instanceof java.sql.SQLException, "RECOVERY_PERMISSION_WRITER_SESSION_PRIVILEGES");
            return ((java.sql.SQLException)denied.getMostSpecificCause()).getErrorCode() == 1227;
        }
    }

    private static void runtime(JdbcTemplate jdbc, ObjectNode value) {
        Long current = jdbc.queryForObject("SELECT CONNECTION_ID()", Long.class);
        var sessions = jdbc.queryForList("""
            SELECT PROCESSLIST_ID FROM performance_schema.threads WHERE TYPE='FOREGROUND'
             AND (NAME<>'thread/sql/compress_gtid_table' OR PROCESSLIST_USER IS NOT NULL
                  OR COALESCE(PROCESSLIST_COMMAND,'')<>'Daemon')
            """, Long.class);
        value.put("exclusiveCurrentSession", sessions.size() == 1 && Objects.equals(sessions.getFirst(), current));
        value.put("eventScheduler", jdbc.queryForObject("SELECT @@global.event_scheduler", String.class));
        value.put("replicationThreads", jdbc.queryForObject(
            "SELECT COUNT(*) FROM performance_schema.threads WHERE NAME LIKE 'thread/sql/slave%' OR NAME LIKE 'thread/sql/replica%' OR NAME LIKE 'thread/group_rpl/%'", String.class));
    }

    private static void grants(JsonNode profile, String schema, String current) {
        String user = current.substring(0, current.indexOf('@')), target = " TO `" + user + "`@`127.0.0.1`";
        var expected = Set.of(
            "GRANT RELOAD, SHUTDOWN, REPLICATION CLIENT ON *.*" + target,
            "GRANT SELECT, INSERT, UPDATE, DELETE ON `" + schema + "`.*" + target,
            "GRANT SELECT ON `performance_schema`.*" + target,
            "GRANT SELECT (HOST, USER, ACCOUNT_LOCKED) ON `mysql`.`user`" + target,
            "GRANT SELECT ON `mysql`.`role_edges`" + target);
        var actual = new HashSet<String>();
        for (var grant : SubmissionJson.array(profile, "grants")) {
            require(grant.isTextual() && actual.add(normalizeColumns(grant.textValue())), "RECOVERY_PERMISSION_WRITER_GRANTS");
        }
        require(actual.equals(expected), "RECOVERY_PERMISSION_WRITER_GRANTS");
    }

    private static String normalizeColumns(String grant) {
        int start = grant.indexOf("SELECT ("), end = grant.indexOf(") ON ");
        if (start < 0 || end < start) return grant;
        var columns = Arrays.stream(grant.substring(start + 8, end).replace("`", "").split(", "))
            .map(name -> name.toUpperCase(Locale.ROOT)).sorted().toList();
        require(columns.equals(List.of("ACCOUNT_LOCKED", "HOST", "USER")), "RECOVERY_PERMISSION_WRITER_GRANTS");
        return grant.substring(0, start) + "SELECT (HOST, USER, ACCOUNT_LOCKED" + grant.substring(end);
    }
}
