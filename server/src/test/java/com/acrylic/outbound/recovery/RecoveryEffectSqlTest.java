package com.acrylic.outbound.recovery;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RecoveryEffectSqlTest {
    @ParameterizedTest @ValueSource(strings = {"SELECT 1", "SHOW TABLES"})
    void provenReadGrammar(String sql) throws Exception {
        assertThat(RecoveryEffectSql.classify(sql)).isEqualTo(RecoveryEffectSql.Kind.READ);
    }

    @ParameterizedTest @ValueSource(strings = {"select id,val from t", "SELECT COUNT(*) FROM t",
        "SELECT id FROM t WHERE id = ?", "DESC t", "EXPLAIN SELECT id FROM t"})
    void sourceSyntaxCannotProveAbsenceOfEffects(String sql) throws Exception {
        assertThat(RecoveryEffectSql.classify(sql)).isEqualTo(RecoveryEffectSql.Kind.SOURCE_READ);
    }

    @ParameterizedTest @ValueSource(strings = {"SELECT JSON_OBJECT('id',BIN_TO_UUID(id),'round',CAST(work_round AS CHAR)) FROM submission_scope",
        "SELECT DATE_FORMAT(created_at,'%Y-%m-%dT%H:%i:%s.%fZ') FROM submission",
        "SELECT JSON_OBJECT('registeredAt',DATE_FORMAT(s.created_at,'%Y-%m-%dT%H:%i:%s.%fZ')) FROM submission s WHERE s.id=?"})
    void originalEvidenceProjectionStillRequiresSourceEffectGate(String sql) throws Exception {
        assertThat(RecoveryEffectSql.classify(sql)).isEqualTo(RecoveryEffectSql.Kind.SOURCE_READ);
    }

    @ParameterizedTest @ValueSource(strings = {"SELECT JSON_OBJECT('value',dangerous_function()) FROM submission",
        "SELECT DATE_FORMAT(dangerous_function(),'%Y') FROM submission",
        "SELECT app.JSON_OBJECT('value',1) FROM submission", "SELECT app.DATE_FORMAT(created_at,'%Y') FROM submission",
        "SELECT JSON_OBJECT('value',1) INTO OUTFILE 'unsafe' FROM submission",
        "SELECT JSON_OBJECT('value',1); DELETE FROM submission"})
    void originalEvidenceFunctionsCannotHideUnclassifiedEffects(String sql) {
        assertThatThrownBy(() -> RecoveryEffectSql.classify(sql)).hasMessage("CONTROL_EFFECT_UNCLASSIFIED");
    }

    @ParameterizedTest @ValueSource(strings = {"COMMIT", "SET AUTOCOMMIT=1", "START TRANSACTION", "BEGIN",
        "INSERT INTO t(id) VALUES (1)", "UPDATE t SET val='x'", "DELETE FROM t", "REPLACE INTO t VALUES(1)",
        "MERGE INTO t USING s ON t.id=s.id WHEN MATCHED THEN UPDATE SET t.v=1",
        "CREATE TABLE t(id INT)", "ALTER TABLE t ADD v INT", "DROP TABLE t", "TRUNCATE TABLE t"})
    void knownEffectsNeverBecomeReadOnly(String sql) throws Exception {
        assertThat(RecoveryEffectSql.classify(sql)).isNotIn(RecoveryEffectSql.Kind.READ, RecoveryEffectSql.Kind.ROLLBACK);
    }

    @ParameterizedTest @ValueSource(strings = {"/* hint */ INSERT INTO t VALUES(1)", "SELECT 1; SELECT 2",
        "CALL p()", "{call p()}", "SET SESSION sql_mode='ANSI'", "SOME VENDOR SYNTAX",
        "CREATE PROCEDURE p() SELECT 1", "SELECT dangerous_function()", "SELECT 1 INTO OUTFILE 'file'",
        "EXPLAIN ANALYZE SELECT dangerous_function()", "WITH c AS (SELECT 1) SELECT * FROM c",
        "SELECT 'unterminated", "SELECT 1 -- comment", "SELECT 1 # comment", "SET @@autocommit=1"})
    void unprovedGrammarHasExactlyOneOutcome(String sql) {
        assertThatThrownBy(() -> RecoveryEffectSql.classify(sql)).hasMessage("CONTROL_EFFECT_UNCLASSIFIED");
    }
}
