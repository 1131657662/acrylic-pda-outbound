package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Explicit NORMAL original read-only capture of native E2A/reservation rows; never grants restoration authority. */
public final class RecoveryTemporaryEvidenceCapture {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    public RecoveryTemporaryEvidenceCapture(DataSource database) {
        jdbc=new JdbcTemplate(Objects.requireNonNull(database)); transaction=new TransactionTemplate(new DataSourceTransactionManager(database));
        transaction.setReadOnly(true); transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    public Observation capture(Request request) {
        require(request!=null && !TransactionSynchronizationManager.isActualTransactionActive(),"RECOVERY_EVIDENCE_CAPTURE_TRANSACTION");
        verifyOriginal(request);
        return transaction.execute(status -> captureLocked(request));
    }

    private void verifyOriginal(Request request) {
        var source=request.source(); var original=request.occupancy();
        require(original.originalSha256().matches("[a-f0-9]{64}") && sha256(original.originalBytes()).equals(original.originalSha256()),
            "RECOVERY_EVIDENCE_CAPTURE_OCCUPANCY");
        var metadata=readCanonical(original.originalBytes());
        require(uuid(metadata,"mysqlServerUuid").equals(request.database().mysqlServerUuid())
            && text(metadata,"databaseSchema").equals(request.database().schema())
            && text(metadata,"sourceRecoveryEpoch").equals(request.database().sourceRecoveryEpoch()),"RECOVERY_EVIDENCE_CAPTURE_DATABASE");
        require(RecoverySourceIdentity.read(metadata.path("source")).equals(source.identity())
            && metadata.path("occupancy").equals(SubmissionJson.parse(source.original("occupancy")))
            && text(metadata,"terminalReceipt").equals(new String(source.original("result"),java.nio.charset.StandardCharsets.UTF_8)),
            "RECOVERY_EVIDENCE_CAPTURE_SOURCE");
        new RecoverySourceOccupancy(request.keys()).verifyUnsubmittedTemporary(source,metadata.path("occupancy"),request.evidence());
    }

    private Observation captureLocked(Request request) {
        var source=request.source(); var original=request.occupancy(); var metadata=readCanonical(original.originalBytes());
        var occupancy=new RecoverySourceOccupancy(request.keys()).verifyUnsubmittedTemporary(source,metadata.path("occupancy"),request.evidence());
        var value=state(request.database()); new RecoveryTemporaryEvidenceHead(jdbc).requireOriginal(source);
        var rows=new RecoveryTemporaryEvidenceRows(jdbc).readOriginal(source.identity());
        RecoveryTemporaryEvidenceFacts.verify(rows,source.identity(),request.evidence(),occupancy,instant(value,"observedAt"));
        RecoveryTemporaryEvidenceFiles.verify(request.storage(),rows,request.evidence());
        var set=new RecoveryUnsubmittedEvidenceRows(jdbc).read(source.identity(),RecoveryUnsubmittedEvidence.expected(request.evidence()));
        require(set.equals(metadata.path("evidenceSet")) && new RecoveryUnsubmittedOccupancyRows(jdbc).read(source.identity(),
            SubmissionJson.parse(source.original("payload")),occupancy.sourceEvidenceSetId()).equals(metadata.path("occupancy").path("rows")),
            "RECOVERY_EVIDENCE_CAPTURE_CHANGED");
        value.set("source",RecoveryOriginalOccupancyFacts.source(source.identity())); value.set("rows",rows);
        var hashes=value.putObject("sourceObjects");
        for(String kind:RecoverySourceManifest.OBJECTS) hashes.put(kind,sha256(source.original(kind)));
        value.put("occupancyPin",original.originalSha256());
        byte[] bytes=RecoveryTemporaryEvidenceMaterial.encode(value); return new Observation(bytes,sha256(bytes),instant(value,"observedAt"));
    }

    private com.fasterxml.jackson.databind.node.ObjectNode state(RecoveryDeviceRegistrationCapture.Database database) {
        var rows=jdbc.query("SELECT @@server_uuid,DATABASE(),recovery_epoch,service_mode,UTC_TIMESTAMP(3) FROM server_state WHERE id=1",
            (rs,n) -> {
                require(rs.getString(1).equals(database.mysqlServerUuid().toString()) && rs.getString(2).equals(database.schema())
                    && rs.getString(3).equals(database.sourceRecoveryEpoch()) && rs.getString(4).equals("NORMAL"),"RECOVERY_EVIDENCE_CAPTURE_DATABASE");
                return SubmissionJson.object().put("formatVersion",1).put("observationKind","ORIGINAL_TEMP_EVIDENCE_ROWS")
                    .put("mysqlServerUuid",rs.getString(1)).put("databaseSchema",rs.getString(2)).put("sourceRecoveryEpoch",rs.getString(3))
                    .put("observedAt",rs.getTimestamp(5).toInstant().toString());
            });
        require(rows.size()==1,"RECOVERY_EVIDENCE_CAPTURE_STATE"); return rows.getFirst();
    }
    public record Request(RecoveryDeviceRegistrationCapture.Database database,RecoveryPinnedSource source,
            RecoveryUnsubmittedOccupancyCapture.Observation occupancy,RecoveryOriginalEvidence.Request evidence,RecoveryReceiptKeys keys,Path storage) {
        public Request { require(database!=null && source!=null && occupancy!=null && evidence!=null && keys!=null && storage!=null,"RECOVERY_EVIDENCE_CAPTURE_REQUEST"); }
    }
    public record Observation(byte[] originalBytes,String independentSha256,Instant observedAt) {
        public Observation { originalBytes=originalBytes.clone(); }
        @Override public byte[] originalBytes() { return originalBytes.clone(); }
        public boolean ordinaryWritesAllowed() { return false; }
        public boolean localDeletionAllowed() { return false; }
    }
}
