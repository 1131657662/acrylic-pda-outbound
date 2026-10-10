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

/** Read-only original NORMAL observation. Its pin must be retained independently; no history or admission is inferred. */
public final class RecoveryReceivedTemporaryCapture {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    public RecoveryReceivedTemporaryCapture(DataSource database) {
        jdbc=new JdbcTemplate(Objects.requireNonNull(database)); transaction=new TransactionTemplate(new DataSourceTransactionManager(database));
        transaction.setReadOnly(true); transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    public Observation capture(Request request) {
        require(request!=null && !TransactionSynchronizationManager.isActualTransactionActive(),"RECOVERY_RECEIVED_CAPTURE_TRANSACTION");
        return transaction.execute(status -> {
            var value=state(request.database()); var rows=new RecoveryReceivedTemporaryRows(jdbc).original(request.source(),request.payload());
            var verification=new RecoveryTemporaryNativeVerification(jdbc).readOriginal(request.verificationId());
            RecoveryReceivedTemporaryMaterial.verification(verification,request.source(),rows.path("evidence"));
            new RecoveryReceiptVerifier(request.keys()).verify(text(rows.path("head"),"receipt_text").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            value.set("source",RecoveryOriginalOccupancyFacts.source(request.source())); value.set("rows",rows); value.set("verification",verification);
            value.put("payloadSha256",SubmissionJson.sha(SubmissionJson.parse(request.payload())));
            RecoveryReceivedTemporaryFiles.verify(request.storage(),rows,request.payload());
            byte[] bytes=RecoveryTemporaryEvidenceMaterial.encode(value);
            return new Observation(bytes,sha256(bytes),instant(value,"observedAt"));
        });
    }

    private com.fasterxml.jackson.databind.node.ObjectNode state(RecoveryDeviceRegistrationCapture.Database database) {
        var rows=jdbc.query("SELECT @@server_uuid,DATABASE(),recovery_epoch,service_mode,UTC_TIMESTAMP(3) FROM server_state WHERE id=1",(rs,n) -> {
            require(rs.getString(1).equals(database.mysqlServerUuid().toString()) && rs.getString(2).equals(database.schema())
                && rs.getString(3).equals(database.sourceRecoveryEpoch()) && rs.getString(4).equals("NORMAL"),"RECOVERY_RECEIVED_CAPTURE_DATABASE");
            return SubmissionJson.object().put("formatVersion",1).put("observationKind","ORIGINAL_RECEIVED_TEMP_VERIFICATION")
                .put("mysqlServerUuid",rs.getString(1)).put("databaseSchema",rs.getString(2)).put("sourceRecoveryEpoch",rs.getString(3))
                .put("observedAt",rs.getTimestamp(5).toInstant().toString());
        });
        require(rows.size()==1,"RECOVERY_RECEIVED_CAPTURE_STATE"); return rows.getFirst();
    }

    public record Request(RecoveryDeviceRegistrationCapture.Database database,RecoverySourceIdentity source,byte[] payload,
            UUID verificationId,RecoveryReceiptKeys keys,Path storage) {
        public Request {
            require(database!=null && source!=null && payload!=null && verificationId!=null && keys!=null && storage!=null,"RECOVERY_RECEIVED_CAPTURE_REQUEST");
            require(database.sourceRecoveryEpoch().equals(source.sourceRecoveryEpoch()) && payload.length>0 && payload.length<=33554432,"RECOVERY_RECEIVED_CAPTURE_REQUEST");
            payload=payload.clone();
        }
        @Override public byte[] payload() { return payload.clone(); }
    }
    public record Observation(byte[] originalBytes,String independentSha256,Instant observedAt) {
        public Observation { originalBytes=originalBytes.clone(); }
        @Override public byte[] originalBytes() { return originalBytes.clone(); }
        public boolean ordinaryWritesAllowed() { return false; }
        public boolean localDeletionAllowed() { return false; }
    }
}
