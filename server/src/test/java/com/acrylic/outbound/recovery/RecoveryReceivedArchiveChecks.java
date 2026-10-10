package com.acrylic.outbound.recovery;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Actual pre-restored originals remain unchanged while trusted consumer effects commit or roll back. */
public final class RecoveryReceivedArchiveChecks {
    private final RecoveryReceivedArchiveSetup.Context c;
    private final AtomicInteger calls=new AtomicInteger();
    private RecoveryReceivedArchiveChecks(RecoveryReceivedArchiveSetup.Context context) { c=context; }

    public static void verify(JdbcTemplate jdbc,Path attempt,Path original) throws Exception {
        RecoveryReceivedCheckpointTargetChecks.verify(jdbc,attempt,original);
        var checks=new RecoveryReceivedArchiveChecks(RecoveryReceivedArchiveSetup.create(jdbc,attempt,original)); checks.verify();
    }

    private void verify() throws Exception {
        var before=c.snapshots().capture(); Files.writeString(c.attempt().resolve("received-archive-before.json"),before.toPrettyString());
        var archive=consume((original,facts) -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.hasResource(c.jdbc().getDataSource())).isTrue();
            insert(1,original); assertOutsideCount(0); return original;
        });
        var complete=c.snapshots().capture();
        Integer duplicate=consume((original,facts) -> c.jdbc().update("INSERT IGNORE INTO recovery_received_consumer_probe VALUES(1,?)",original.provenance().sourceManifestSha256()));
        assertThat(duplicate).isZero();
        assertThat(c.snapshots().capture()).isEqualTo(complete);
        callbackFailure(complete); sqlFailure(complete); verificationMutation(complete,false); verificationMutation(complete,true);
        changedFile(complete); badRowsPin(complete); originalChangedDuringConsumer(complete); lockedStock(complete); ownerChanged(complete); outerTransaction(complete);
        conserved(before,complete); assertOutsideCount(1);
        assertThat(c.jdbc().queryForObject("SELECT service_mode FROM server_state WHERE id=1",String.class)).isEqualTo("RECOVERY_PENDING");
        assertThat(archive.ordinaryWritesAllowed()).isFalse(); assertThat(archive.localDeletionAllowed()).isFalse();
        assertThat(archive.source().auditThroughSeq()).isEqualTo(c.source().identity.auditThroughSeq()+1);
        save(archive,complete);
    }

    private void save(RecoveryRecoveredTemporaryArchive archive,JsonNode complete) throws Exception {
        Files.writeString(c.attempt().resolve("received-archive-after.json"),complete.toPrettyString());
        var result=SubmissionJson.object().put("sourcePin",archive.provenance().sourceManifestSha256()).put("sourceRoot",c.source().materials.toString())
            .put("rowsPin",c.request().rows().independentSha256()).put("verificationId",text(c.nativeFacts().path("archive"),"verificationId"))
            .put("verificationSha256",text(c.nativeFacts().path("archive"),"verificationSha256")).put("calls",calls.get()).put("ordinaryWritesAllowed",false).put("localDeletionAllowed",false);
        result.set("source",RecoveryOriginalOccupancyFacts.source(archive.source()));
        Files.write(c.attempt().resolve("received-archive-result.json"),RecoveryControlJson.encode(result));
        Files.writeString(c.attempt().resolve("received-archive-native-facts.json"),SubmissionJson.canonical(c.nativeFacts()));
        Files.write(c.attempt().resolve("received-archive-payload.bin"),archive.payload());
        Files.write(c.attempt().resolve("received-archive-terminal.bin"),archive.terminalReceipt());
        Files.write(c.attempt().resolve("received-archive-stop.bin"),archive.stopProof());
    }

    private void callbackFailure(JsonNode complete) throws Exception {
        assertThatThrownBy(() -> consume((archive,facts) -> { insert(2,archive); throw new IllegalStateException("consumer rollback"); })).hasMessage("consumer rollback");
        assertThat(c.snapshots().capture()).isEqualTo(complete);
    }
    private void sqlFailure(JsonNode complete) throws Exception {
        assertThatThrownBy(() -> consume((archive,facts) -> { insert(2,archive); return c.jdbc().update("INSERT INTO recovery_received_consumer_probe VALUES(3,NULL)"); }));
        assertThat(c.snapshots().capture()).isEqualTo(complete);
    }
    private void verificationMutation(JsonNode complete,boolean tail) throws Exception {
        assertThatThrownBy(() -> consume((archive,facts) -> {
            insert(2,archive);
            Runnable mutation=() -> c.jdbc().update("UPDATE source_evidence_verification SET known_facts_sha256=REPEAT('0',64) WHERE id=?",Ids.bytes(facts.verificationId()));
            if(tail) TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void beforeCommit(boolean readOnly) { mutation.run(); }
            }); else mutation.run();
            return archive;
        })).hasMessageContaining("RECOVERY_NATIVE_VERIFICATION_CHANGED");
        assertThat(c.snapshots().capture()).isEqualTo(complete);
    }
    private void changedFile(JsonNode complete) throws Exception {
        var material=RecoveryTemporaryEvidenceMaterial.decode(Files.readAllBytes(c.request().rows().materialRoot().resolve(c.request().rows().independentSha256()+".json")));
        Path file=c.request().storage().resolve(text(material.path("rows").path("evidence").path("source_evidence_object").get(0),"file_key"));
        byte[] original=Files.readAllBytes(file),changed=original.clone(); changed[0]^=1; Files.write(file,changed); int count=calls.get();
        try { assertThatThrownBy(() -> consume((archive,facts) -> archive)).hasMessageContaining("RECOVERY_RECEIVED_EVIDENCE_FILE"); }
        finally { Files.write(file,original); }
        assertThat(calls.get()).isEqualTo(count); assertThat(c.snapshots().capture()).isEqualTo(complete);
    }
    private void badRowsPin(JsonNode complete) throws Exception {
        var r=c.request(); var rows=new RecoveryReceivedTemporaryMaterial.Request(r.rows().materialRoot(),ZERO_HASH,r.rows().database(),r.rows().notAfter());
        var bad=new RecoveryReceivedTemporaryArchiveConsumer.Request(r.sourceRoot(),r.preparation(),r.registration(),r.evidence(),r.occupancy(),r.permissions(),rows,r.storage());
        assertThatThrownBy(() -> c.consumer().consume(bad,c.nativeFacts(),(archive,facts) -> { throw new AssertionError("callback forbidden"); }))
            .hasMessage("RECOVERY_RECEIVED_PIN_REQUIRED");
        assertThat(c.snapshots().capture()).isEqualTo(complete);
    }
    private void originalChangedDuringConsumer(JsonNode complete) throws Exception {
        String hash=text(c.source().manifest.path("objects").path("origin"),"sha256"); Path file=c.source().materials.resolve(hash+".bin");
        byte[] original=Files.readAllBytes(file),changed=original.clone(); changed[0]^=1;
        try {
            assertThatThrownBy(() -> consume((archive,facts) -> {
                insert(2,archive);
                try { Files.write(file,changed); } catch(java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
                return archive;
            })).hasMessageContaining("RECOVERY_SOURCE_OBJECT_MISMATCH");
        } finally { Files.write(file,original); }
        assertThat(c.snapshots().capture()).isEqualTo(complete);
    }
    private void lockedStock(JsonNode complete) throws Exception {
        byte[] bale=c.jdbc().queryForObject("SELECT bale_id FROM bale_commitment WHERE submission_id=?",byte[].class,Ids.bytes(c.source().identity.submissionId()));
        int count=calls.get();
        try(var connection=c.jdbc().getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try(var statement=connection.prepareStatement("SELECT bale_id FROM inventory_position WHERE bale_id=? FOR UPDATE")) {
                statement.setBytes(1,bale); try(var rows=statement.executeQuery()) { assertThat(rows.next()).isTrue(); }
            }
            try { assertThatThrownBy(() -> consume((archive,facts) -> archive)).hasMessageContaining("Lock wait timeout exceeded"); }
            finally { connection.rollback(); }
        }
        assertThat(calls.get()).isEqualTo(count); assertThat(c.snapshots().capture()).isEqualTo(complete);
    }
    private void ownerChanged(JsonNode complete) throws Exception {
        byte[] id=Ids.bytes(c.source().identity.submissionId()); int count=calls.get();
        c.jdbc().update("UPDATE bale_commitment SET dispatch_cycle=dispatch_cycle+1 WHERE submission_id=?",id);
        try { assertThatThrownBy(() -> consume((archive,facts) -> archive)).hasMessageContaining("RECOVERY_OCCUPANCY_CAPTURE_OWNER"); }
        finally { c.jdbc().update("UPDATE bale_commitment SET dispatch_cycle=dispatch_cycle-1 WHERE submission_id=?",id); }
        assertThat(calls.get()).isEqualTo(count); assertThat(c.snapshots().capture()).isEqualTo(complete);
    }
    private void outerTransaction(JsonNode complete) throws Exception {
        var tx=new TransactionTemplate(new DataSourceTransactionManager(c.jdbc().getDataSource()));
        tx.execute(status -> { assertThatThrownBy(() -> consume((archive,facts) -> archive)).hasMessage("CONTROL_OUTER_TRANSACTION_FORBIDDEN"); return null; });
        assertThat(c.snapshots().capture()).isEqualTo(complete);
    }
    private <T> T consume(BiFunction<RecoveryRecoveredTemporaryArchive,RecoveryTemporaryNativeHandoverFacts.Facts,T> operation) {
        return c.consumer().consume(c.request(),c.nativeFacts(),(archive,facts) -> { calls.incrementAndGet(); return operation.apply(archive,facts); });
    }
    private void insert(int id,RecoveryRecoveredTemporaryArchive archive) {
        assertThat(c.jdbc().update("INSERT INTO recovery_received_consumer_probe VALUES(?,?)",id,archive.provenance().sourceManifestSha256())).isEqualTo(1);
    }
    private void assertOutsideCount(int expected) {
        try(var connection=c.jdbc().getDataSource().getConnection();var statement=connection.createStatement();var rows=statement.executeQuery("SELECT COUNT(*) FROM recovery_received_consumer_probe")) {
            assertThat(rows.next()).isTrue(); assertThat(rows.getInt(1)).isEqualTo(expected);
        } catch(java.sql.SQLException error) { throw new AssertionError(error); }
    }
    private static void conserved(JsonNode before,JsonNode after) {
        before.path("tables").fields().forEachRemaining(entry -> {
            if(!entry.getKey().equals("recovery_received_consumer_probe")) assertThat(after.path("tables").path(entry.getKey())).as(entry.getKey()).isEqualTo(entry.getValue());
        });
        for(String field:new String[]{"files","schema","flywayHistory"}) assertThat(after.path(field)).isEqualTo(before.path(field));
    }
}
