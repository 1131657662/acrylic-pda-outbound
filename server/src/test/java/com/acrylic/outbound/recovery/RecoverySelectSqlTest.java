package com.acrylic.outbound.recovery;

import java.sql.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecoverySelectSqlTest {
    static Stream<String> productReads() {
        return Stream.of("""
            SELECT S.PRIMARY_ID, S.SESSION_ID, S.CREATION_TIME, S.LAST_ACCESS_TIME,
              S.MAX_INACTIVE_INTERVAL, SA.ATTRIBUTE_NAME, SA.ATTRIBUTE_BYTES
            FROM SPRING_SESSION S LEFT JOIN SPRING_SESSION_ATTRIBUTES SA ON S.PRIMARY_ID = SA.SESSION_PRIMARY_ID
            WHERE S.SESSION_ID = ?
            """, """
            SELECT BIN_TO_UUID(id),job_type,dedupe_key,status,payload,attempt_count,max_attempts,
              available_at,lease_owner,BIN_TO_UUID(lease_token),lease_until,last_error,created_at,updated_at,recovery_epoch
            FROM background_job WHERE job_type IN (?,?) AND recovery_epoch=? AND status IN ('PENDING','RETRY')
              AND attempt_count<max_attempts AND available_at<=UTC_TIMESTAMP(3)
            ORDER BY available_at,id LIMIT 1 FOR UPDATE SKIP LOCKED
            """, """
            SELECT p.code FROM permission p WHERE EXISTS (
              SELECT 1 FROM role_permission rp INNER JOIN user_role ur ON ur.role_id=rp.role_id
              WHERE rp.permission_id=p.id AND ur.user_id=?) ORDER BY p.code
            """, """
            SELECT COUNT(*) FROM background_job WHERE job_type=? AND payload->>'$.scope.actor'=?
              AND payload->>'$.epoch'=? AND CAST(recovery_epoch AS CHAR)=payload->>'$.epoch'
              AND created_at>UTC_TIMESTAMP(3)-INTERVAL 7 DAY AND status IN ('PENDING','RUNNING','RETRY')
            """, "SELECT id FROM app_user WHERE id=? FOR UPDATE", "SELECT UTC_TIMESTAMP(6)",
            "SELECT BIN_TO_UUID(COALESCE(submission_id,pc_submission_id)) FROM dispatch_record WHERE id=?",
            "SELECT record_sha256 FROM pda_audit_event WHERE id=? UNION ALL SELECT record_sha256 FROM pda_audit_event WHERE origin_device_id=? AND local_seq=?",
            "SELECT id FROM t WHERE id IN (SELECT id FROM u UNION ALL SELECT id FROM v)",
            "SELECT id FROM t WHERE x BETWEEN 2 AND ? AND (y IS NULL OR y NOT IN (?,?)) ORDER BY id DESC LIMIT ? OFFSET ?",
            "SELECT CASE WHEN x IS NULL THEN 0 ELSE 1 END AS n FROM t",
            "SELECT COUNT(*) FROM t GROUP BY id HAVING COUNT(*)>1");
    }

    @ParameterizedTest @MethodSource("productReads")
    void actualProductSyntaxAlwaysRequiresEffectAuthority(String sql) throws Exception {
        assertThat(RecoveryEffectSql.classify(sql)).isEqualTo(RecoveryEffectSql.Kind.SOURCE_READ);
        try (var f = new EffectUnitFixture(true)) {
            var raw = mock(Statement.class);
            when(f.physical.createStatement()).thenReturn(raw);
            var statement = f.connection.createStatement();
            statement.execute(sql);
            verify(raw).execute(sql);
            assertThat(f.authority.checks).isPositive();
            clearInvocations(raw);
            f.gate.fence();
            assertThatThrownBy(() -> statement.execute(sql)).hasMessage("RECOVERY_LEASE_FENCED");
            verifyNoInteractions(raw);
        }
    }

    @ParameterizedTest @ValueSource(strings = {
        "SELECT evil() FROM t", "SELECT app.COUNT(*) FROM t", "SELECT `COUNT`(*) FROM t",
        "SELECT COUNT(evil()) FROM t", "SELECT id FROM t WHERE EXISTS (SELECT evil() FROM u)",
        "SELECT id FROM t WHERE id=GET_LOCK('lock',1)", "SELECT SLEEP(1)", "SELECT LOAD_FILE('file')",
        "SELECT id INTO OUTFILE 'file' FROM t", "SELECT id FROM t INTO DUMPFILE 'file'",
        "SELECT id FROM t UNION SELECT evil() FROM u", "SELECT id FROM t UNION ALL SELECT evil() FROM u",
        "SELECT id FROM t UNION ALL UPDATE t SET id=1", "SELECT id FROM t UNION ALL", "SELECT id FROM t; COMMIT",
        "SELECT id FROM t /*comment*/", "SELECT id FROM t -- comment", "SELECT id FROM t #comment",
        "SELECT @x:=id FROM t", "SELECT id FROM t PROCEDURE ANALYSE()", "SELECT id FROM t WHERE",
        "SELECT id FROM t WHERE id IN ()", "SELECT id FROM t FOR", "SELECT id FROM t FOR UPDATE SKIP",
        "SELECT id FROM t JOIN u", "SELECT COUNT(*) FROM t WHERE id=? trailing",
        "SELECT id FROM t WHERE id=app.fn(?)", "SELECT id FROM t WHERE id=? COLLATE utf8mb4_bin",
        "SELECT B\u0131N_TO_UUID(id) FROM t"
    })
    void unknownSyntaxNeverReachesTheDriver(String sql) throws Exception {
        try (var f = new EffectUnitFixture(true)) {
            var raw = mock(Statement.class);
            when(f.physical.createStatement()).thenReturn(raw);
            var statement = f.connection.createStatement();
            assertThatThrownBy(() -> statement.execute(sql)).hasMessage("CONTROL_EFFECT_UNCLASSIFIED");
            verifyNoInteractions(raw);
        }
    }

    @Test void preparedProductReadStillChecksAuthorityAtExecution() throws Exception {
        try (var f = new EffectUnitFixture(true)) {
            String sql = productReads().findFirst().orElseThrow();
            var raw = mock(PreparedStatement.class);
            when(f.physical.prepareStatement(sql)).thenReturn(raw);
            var statement = f.connection.prepareStatement(sql);
            statement.setString(1, "session"); clearInvocations(raw);
            f.deny();
            assertThatThrownBy(statement::executeQuery).hasMessage("RECOVERY_LEASE_OUTSTANDING");
            verifyNoInteractions(raw);
        }
    }

    @Test void nestedAndOversizedInputsAreBounded() {
        String nested = "SELECT " + "(".repeat(40) + "id" + ")".repeat(40) + " FROM t";
        assertThatThrownBy(() -> RecoveryEffectSql.classify(nested)).hasMessage("CONTROL_EFFECT_UNCLASSIFIED");
        assertThatThrownBy(() -> RecoveryEffectSql.classify("SELECT " + " ".repeat(65536) + "1"))
            .hasMessage("CONTROL_EFFECT_UNCLASSIFIED");
        assertThatThrownBy(() -> RecoveryEffectSql.classify("SELECT 1" + " UNION ALL SELECT 1".repeat(64)))
            .hasMessage("CONTROL_EFFECT_UNCLASSIFIED");
    }
}
