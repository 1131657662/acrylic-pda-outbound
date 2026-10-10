package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Real transaction visibility and rollback checks; the probe never represents supervisor consent. */
final class RecoveryArchiveConsumerChecks {
    private final RecoveryTemporaryEvidenceTargetChecks.Context c;
    private final AtomicInteger calls=new AtomicInteger();
    private RecoveryArchiveConsumerChecks(RecoveryTemporaryEvidenceTargetChecks.Context context) { c=context; }

    static void verify(RecoveryTemporaryEvidenceTargetChecks.Context c) throws Exception {
        var checks=new RecoveryArchiveConsumerChecks(c);
        checks.missingRows(); c.restorer().restore(c.request());
        c.jdbc().execute("CREATE TABLE recovery_archive_consumer_probe(id INT PRIMARY KEY,source_pin CHAR(64) NOT NULL) ENGINE=InnoDB");
        var original=c.snapshots().capture();
        Files.writeString(c.attempt().resolve("archive-consumer-before.json"),original.toPrettyString());
        var archive=checks.committed(); var complete=c.snapshots().capture();
        checks.duplicate(complete); checks.callbackFailure(complete); checks.sqlFailure(complete);
        checks.originalRowMutation(complete); checks.changedPin(complete,false); checks.changedPin(complete,true);
        checks.badPinNeverCalls(complete); checks.outerTransaction(complete);
        conserved(original,complete);
        assertThat(c.jdbc().queryForObject("SELECT service_mode FROM server_state WHERE id=1",String.class)).isEqualTo("RECOVERY_PENDING");
        assertThat(archive.ordinaryWritesAllowed()).isFalse(); assertThat(archive.localDeletionAllowed()).isFalse();
        checks.save(archive,complete);
    }

    private void missingRows() throws Exception {
        assertThatThrownBy(() -> c.restorer().withVerifiedArchive(c.request(),archive -> calls.incrementAndGet()))
            .hasMessage("RECOVERY_ARCHIVE_RESTORED_ROWS_REQUIRED");
        assertThat(calls).hasValue(0); c.preserved();
    }

    private RecoveryRecoveredTemporaryArchive committed() {
        var committed=new AtomicInteger();
        var result=c.restorer().withVerifiedArchive(c.request(),archive -> {
            calls.incrementAndGet(); assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.hasResource(c.jdbc().getDataSource())).isTrue();
            insert(1,archive); assertThat(outsideCount()).isZero();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { committed.incrementAndGet(); }
            });
            return archive;
        });
        assertThat(calls).hasValue(1); assertThat(committed).hasValue(1); assertThat(outsideCount()).isEqualTo(1);
        assertThat(result.payload()).isEqualTo(c.source().read().original("payload"));
        assertThat(result.terminalReceipt()).isEqualTo(c.source().read().original("result"));
        assertThat(result.stopProof()).isEqualTo(RecoveryUnsubmittedEvidence.expected(c.request().evidence()).stopProofBytes());
        return result;
    }

    private void duplicate(JsonNode complete) throws Exception {
        var result=c.restorer().withVerifiedArchive(c.request(),archive -> {
            calls.incrementAndGet();
            return c.jdbc().update("INSERT IGNORE INTO recovery_archive_consumer_probe VALUES(1,?)",archive.provenance().sourceManifestSha256());
        });
        assertThat(result).isZero(); assertThat(calls).hasValue(2); preserved(complete);
    }

    private void callbackFailure(JsonNode complete) throws Exception {
        assertThatThrownBy(() -> c.restorer().withVerifiedArchive(c.request(),archive -> {
            insert(2,archive); throw new IllegalStateException("consumer failed after write");
        })).hasMessage("consumer failed after write");
        preserved(complete);
    }

    private void sqlFailure(JsonNode complete) throws Exception {
        assertThatThrownBy(() -> c.restorer().withVerifiedArchive(c.request(),archive -> {
            insert(2,archive); return c.jdbc().update("INSERT INTO recovery_archive_consumer_probe VALUES(3,NULL)");
        })).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        preserved(complete);
    }

    private void originalRowMutation(JsonNode complete) throws Exception {
        assertThatThrownBy(() -> c.restorer().withVerifiedArchive(c.request(),archive -> {
            insert(2,archive);
            c.jdbc().update("UPDATE source_evidence_object SET file_key='consumer-changed.bin' WHERE id=?",
                com.acrylic.outbound.common.Ids.bytes(archive.stopObjectId())); return true;
        })).hasMessage("RECOVERY_ARCHIVE_RESTORED_ROWS_REQUIRED");
        preserved(complete);
    }

    private void changedPin(JsonNode complete,boolean atCommit) throws Exception {
        Path file=pinFile(); byte[] original=Files.readAllBytes(file); var mutated=new AtomicInteger();
        try {
            assertThatThrownBy(() -> c.restorer().withVerifiedArchive(c.request(),archive -> {
                insert(2,archive);
                if(atCommit) TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void beforeCommit(boolean readOnly) { change(file,mutated); }
                });
                else change(file,mutated);
                return true;
            })).hasMessage("RECOVERY_EVIDENCE_ROWS_PIN_MISMATCH");
            assertThat(mutated).hasValue(1);
        } finally { Files.write(file,original); }
        preserved(complete);
    }

    private void badPinNeverCalls(JsonNode complete) throws Exception {
        Path file=pinFile(); byte[] original=Files.readAllBytes(file); int before=calls.get();
        try {
            Files.writeString(file,"bad-pin-before-consumer");
            assertThatThrownBy(() -> c.restorer().withVerifiedArchive(c.request(),archive -> calls.incrementAndGet()))
                .hasMessage("RECOVERY_EVIDENCE_ROWS_PIN_MISMATCH");
            assertThat(calls).hasValue(before);
        } finally { Files.write(file,original); }
        preserved(complete);
    }

    private void outerTransaction(JsonNode complete) throws Exception {
        var outer=new TransactionTemplate(new DataSourceTransactionManager(c.jdbc().getDataSource())); int before=calls.get();
        outer.execute(status -> {
            assertThatThrownBy(() -> c.restorer().withVerifiedArchive(c.request(),archive -> calls.incrementAndGet()))
                .hasMessage("CONTROL_OUTER_TRANSACTION_FORBIDDEN"); return null;
        });
        assertThat(calls).hasValue(before); preserved(complete);
    }

    private void insert(int id,RecoveryRecoveredTemporaryArchive archive) {
        assertThat(c.jdbc().update("INSERT INTO recovery_archive_consumer_probe VALUES(?,?)",id,
            archive.provenance().sourceManifestSha256())).isEqualTo(1);
    }

    private int outsideCount() {
        try(var connection=c.jdbc().getDataSource().getConnection(); var query=connection.createStatement();
                var rows=query.executeQuery("SELECT COUNT(*) FROM recovery_archive_consumer_probe")) {
            assertThat(rows.next()).isTrue(); return rows.getInt(1);
        } catch(java.sql.SQLException failure) { throw new IllegalStateException(failure); }
    }

    private Path pinFile() { return c.request().rows().materialRoot().resolve(c.request().rows().independentSha256()+".json"); }
    private static void change(Path file,AtomicInteger mutated) {
        try { Files.writeString(file,"changed-by-consumer"); mutated.incrementAndGet(); }
        catch(java.io.IOException failure) { throw new IllegalStateException(failure); }
    }
    private void preserved(JsonNode expected) throws Exception { assertThat(c.snapshots().capture()).isEqualTo(expected); }
    private static void conserved(JsonNode before,JsonNode after) {
        before.path("tables").fields().forEachRemaining(table -> {
            if(!table.getKey().equals("recovery_archive_consumer_probe"))
                assertThat(after.path("tables").path(table.getKey())).as(table.getKey()).isEqualTo(table.getValue());
        });
        for(String field:List.of("files","schema","flywayHistory")) assertThat(after.path(field)).as(field).isEqualTo(before.path(field));
    }

    private void save(RecoveryRecoveredTemporaryArchive archive,JsonNode complete) throws Exception {
        Files.writeString(c.attempt().resolve("archive-consumer-after.json"),complete.toPrettyString());
        var result=SubmissionJson.object().put("evidenceSetId",archive.evidenceSetId().toString()).put("stopObjectId",archive.stopObjectId().toString())
            .put("payloadBase64",Base64.getEncoder().encodeToString(archive.payload())).put("terminalReceiptBase64",Base64.getEncoder().encodeToString(archive.terminalReceipt()))
            .put("stopProofBase64",Base64.getEncoder().encodeToString(archive.stopProof())).put("ordinaryWritesAllowed",false).put("localDeletionAllowed",false);
        result.set("provenance",new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(archive.provenance()));
        Files.write(c.attempt().resolve("recovered-archive-read.json"),RecoveryControlJson.encode(result));
    }
}
