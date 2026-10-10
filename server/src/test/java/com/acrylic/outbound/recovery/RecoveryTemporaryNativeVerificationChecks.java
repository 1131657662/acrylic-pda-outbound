package com.acrylic.outbound.recovery;

import com.acrylic.outbound.e2averify.E2aMigrationSnapshot;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Real MySQL checks with synthetic identity/verification; no original-source recovery is claimed. */
public final class RecoveryTemporaryNativeVerificationChecks {
    private final JdbcTemplate jdbc;
    private final Path attempt;
    private final RecoveryTemporaryNativeHandoverFixture f;
    private final TransactionTemplate tx;
    private final E2aMigrationSnapshot snapshots;
    private final String verification;
    private final AtomicInteger calls = new AtomicInteger();
    private JsonNode before;

    private RecoveryTemporaryNativeVerificationChecks(JdbcTemplate jdbc, Path attempt, Map<String,String> seed) throws Exception {
        this.jdbc=jdbc; this.attempt=attempt; verification=seed.get("mappedVerificationId");
        var source=new RecoverySourceIdentity("TEMP",id(seed,"warehouseId"),id(seed,"mappedScopeId"),id(seed,"mappedSubmissionId"),
            id(seed,"deviceId"),id(seed,"operatorId"),id(seed,"authorizationId"),id(seed,"orderId"),id(seed,"deviceId"),
            id(seed,"mappedClientOrderId"),"1",1,1,5,1);
        f=new RecoveryTemporaryNativeHandoverFixture(source,id(seed,"mappedSetId"));
        tx=new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        snapshots=new E2aMigrationSnapshot(jdbc,attempt,attempt.resolve("storage"));
        jdbc.update("UPDATE source_evidence_verification SET result='VERIFIED_COMPLETE_AT_STOP',set_sha256=? WHERE id=UUID_TO_BIN(?)",
            f.archive.provenance().setSha256(),verification);
        ((ObjectNode)f.input.path("archive")).put("verificationId",verification).put("verificationSha256",sha256(RecoveryControlJson.encode(row())));
        jdbc.execute("CREATE TABLE recovery_native_consumer_probe(id INT PRIMARY KEY) ENGINE=InnoDB");
        before=snapshots.capture();
    }

    public static void verify(JdbcTemplate jdbc, Path attempt, Map<String,String> seed) throws Exception {
        var c=new RecoveryTemporaryNativeVerificationChecks(jdbc,attempt,seed);
        c.transactionRequired(); c.missing(); c.foreign(seed.get("mapping0VerificationId"));
        for(String change:List.of("result='PARTIAL'","algorithm_version=2","verification_version=2","set_sha256=REPEAT('b',64)"))
            c.rejectsChangedBinding(change);
        for(String change:List.of("known_facts_sha256=REPEAT('b',64)","findings_bytes=CAST('{\"changed\":true}' AS BINARY)",
                "verified_at=verified_at+INTERVAL 1 SECOND")) {
            c.rejectsChangedRow(change,false); c.rejectsChangedRow(change,true); c.rejectsBeforeEntry(change);
        }
        c.tailSql(); c.locked(); c.largeFindings(); c.committed(); c.save();
    }

    private void transactionRequired() throws Exception {
        assertThatThrownBy(() -> RecoveryTemporaryNativeHandoverFacts.readVerified(f.archive,f.input,jdbc))
            .hasMessage("RECOVERY_NATIVE_TRANSACTION_REQUIRED"); preserved();
    }
    private void missing() throws Exception {
        var input=f.input.deepCopy(); ((ObjectNode)input.path("archive")).put("verificationId",UUID.randomUUID().toString());
        assertThatThrownBy(() -> tx.execute(s -> consume(input))).hasMessage("RECOVERY_NATIVE_VERIFICATION_MISSING");
        assertThat(calls).hasValue(0); preserved();
    }
    private void foreign(String id) throws Exception {
        var input=f.input.deepCopy(); ((ObjectNode)input.path("archive")).put("verificationId",id);
        assertThatThrownBy(() -> tx.execute(s -> consume(input))).hasMessage("RECOVERY_NATIVE_VERIFICATION_MISMATCH");
        assertThat(calls).hasValue(0); preserved();
    }
    private void rejectsChangedBinding(String change) throws Exception {
        int count=calls.get();
        assertThatThrownBy(() -> tx.execute(s -> { update(change); return consume(f.input); }))
            .hasMessage("RECOVERY_NATIVE_VERIFICATION_MISMATCH");
        assertThat(calls).hasValue(count); preserved();
    }
    private void rejectsBeforeEntry(String change) throws Exception {
        int count=calls.get();
        assertThatThrownBy(() -> tx.execute(s -> { update(change); return consume(f.input); }))
            .hasMessage("RECOVERY_NATIVE_VERIFICATION_CHANGED");
        assertThat(calls).hasValue(count); preserved();
    }
    private void rejectsChangedRow(String change, boolean atCommit) throws Exception {
        assertThatThrownBy(() -> tx.execute(s -> consume(f.input,(archive,facts) -> {
            jdbc.update("INSERT INTO recovery_native_consumer_probe VALUES(1)");
            if(atCommit) TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void beforeCommit(boolean readOnly) { update(change); }
            }); else update(change);
            return true;
        }))).hasMessage("RECOVERY_NATIVE_VERIFICATION_CHANGED");
        preserved();
    }
    private void tailSql() throws Exception {
        assertThatThrownBy(() -> tx.execute(s -> consume(f.input,(archive,facts) -> {
            jdbc.update("INSERT INTO recovery_native_consumer_probe VALUES(1)");
            return jdbc.update("INSERT INTO recovery_native_consumer_probe VALUES(NULL)");
        }))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        preserved();
    }
    private void locked() throws Exception {
        tx.execute(s -> consume(f.input,(archive,facts) -> {
            try(var executor=Executors.newSingleThreadExecutor()) {
                var other=executor.submit(() -> {
                    try(var connection=jdbc.getDataSource().getConnection(); var statement=connection.createStatement();
                            var update=connection.prepareStatement("UPDATE source_evidence_verification SET known_facts_sha256=REPEAT('c',64) WHERE id=UUID_TO_BIN(?)")) {
                        statement.execute("SET SESSION innodb_lock_wait_timeout=1");
                        update.setString(1,verification); update.executeUpdate();
                        return 0;
                    } catch(java.sql.SQLException failure) { return failure.getErrorCode(); }
                });
                assertThat(other.get(5,TimeUnit.SECONDS)).isEqualTo(1205); return true;
            } catch(Exception failure) { throw new IllegalStateException(failure); }
        })); preserved();
    }
    private void committed() throws Exception {
        var afterCommit=new AtomicInteger();
        var result=tx.execute(s -> consume(f.input,(archive,facts) -> {
            jdbc.update("INSERT INTO recovery_native_consumer_probe VALUES(1)");
            assertThat(outsideCount()).isZero();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { afterCommit.incrementAndGet(); }
            }); return facts;
        }));
        assertThat(result.verificationId().toString()).isEqualTo(verification);
        assertThat(result.ordinaryWritesAllowed()).isFalse(); assertThat(result.localDeletionAllowed()).isFalse();
        assertThat(afterCommit).hasValue(1); assertThat(outsideCount()).isEqualTo(1);
        Integer duplicate=tx.execute(s -> consume(f.input,(archive,facts) -> jdbc.update("INSERT IGNORE INTO recovery_native_consumer_probe VALUES(1)")));
        assertThat(duplicate).isZero();
    }

    private void largeFindings() throws Exception {
        tx.execute(s -> {
            jdbc.update("UPDATE source_evidence_verification SET findings_bytes=CAST(REPEAT('x',262144) AS BINARY) WHERE id=UUID_TO_BIN(?)",verification);
            var input=f.input.deepCopy();
            byte[] canonical=(SubmissionJson.canonical(row())+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            ((ObjectNode)input.path("archive")).put("verificationSha256",sha256(canonical));
            assertThat(canonical.length).isGreaterThan(65536);
            var result=consume(input,(archive,facts) -> facts);
            assertThat(result.verificationId().toString()).isEqualTo(verification);
            s.setRollbackOnly(); return true;
        }); preserved();
    }
    private Object consume(JsonNode input) { return consume(input,(archive,facts) -> calls.incrementAndGet()); }
    private <T> T consume(JsonNode input, BiFunction<RecoveryRecoveredTemporaryArchive,RecoveryTemporaryNativeHandoverFacts.Facts,T> consumer) {
        return new RecoveryTemporaryNativeArchiveConsumer(jdbc).consume(f.archive,input,consumer);
    }
    private void update(String change) {
        assertThat(jdbc.update("UPDATE source_evidence_verification SET "+change+" WHERE id=UUID_TO_BIN(?)",verification)).isEqualTo(1);
    }
    private int outsideCount() {
        try(var connection=jdbc.getDataSource().getConnection(); var statement=connection.createStatement();
                var rows=statement.executeQuery("SELECT COUNT(*) FROM recovery_native_consumer_probe")) {
            assertThat(rows.next()).isTrue(); return rows.getInt(1);
        } catch(java.sql.SQLException failure) { throw new IllegalStateException(failure); }
    }
    private JsonNode row() {
        var raw=jdbc.queryForMap("SELECT * FROM source_evidence_verification WHERE id=UUID_TO_BIN(?)",verification);
        var value=SubmissionJson.object();
        for(String id:List.of("id","set_id","scope_id","submission_id")) {
            var bytes=java.nio.ByteBuffer.wrap((byte[])raw.get(id)); value.put(id,new UUID(bytes.getLong(),bytes.getLong()).toString());
        }
        for(String column:List.of("work_round","verification_version","algorithm_version","set_sha256","result","known_facts_sha256"))
            value.put(column,raw.get(column).toString());
        value.put("findings_bytes",Base64.getEncoder().encodeToString((byte[])raw.get("findings_bytes")));
        var time=raw.get("verified_at");
        var local=time instanceof java.sql.Timestamp stamp ? stamp.toLocalDateTime() : (java.time.LocalDateTime)time;
        return value.put("verified_at",local.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS")));
    }
    private void preserved() throws Exception { assertThat(snapshots.capture()).isEqualTo(before); }
    private static UUID id(Map<String,String> seed,String key) { return UUID.fromString(seed.get(key)); }
    private void save() throws Exception {
        var after=snapshots.capture();
        before.path("tables").fields().forEachRemaining(table -> {
            if(!table.getKey().equals("recovery_native_consumer_probe")) assertThat(after.path("tables").path(table.getKey())).isEqualTo(table.getValue());
        });
        for(String key:List.of("files","schema","flywayHistory")) assertThat(after.path(key)).isEqualTo(before.path(key));
        Files.writeString(attempt.resolve("native-verification-before.json"),before.toPrettyString());
        Files.writeString(attempt.resolve("native-verification-after.json"),after.toPrettyString());
        Files.write(attempt.resolve("synthetic-native-input.json"),RecoveryControlJson.encode(f.input));
        Files.write(attempt.resolve("synthetic-native-verification.json"),RecoveryControlJson.encode(row()));
    }
}
