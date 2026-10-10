package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiFunction;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/**
 * Explicit closed consumer of fully pre-restored received ABORTED TEMP originals. Trusted callbacks
 * use this database's bound connection, preserve all source rows/files and have no external effects.
 * No repair, signing, ordinary/handover admission or production Bean/HTTP is provided.
 */
public final class RecoveryReceivedTemporaryArchiveConsumer {
    private final JdbcTemplate jdbc;
    private final RecoveryClosedAuthority authority;
    private final RecoveryReceivedTemporaryArchiveInputs inputs;
    public RecoveryReceivedTemporaryArchiveConsumer(DataSource database,RecoveryClosedAuthority authority,RecoveryReceiptKeys keys) {
        jdbc=new JdbcTemplate(Objects.requireNonNull(database)); this.authority=Objects.requireNonNull(authority);
        inputs=new RecoveryReceivedTemporaryArchiveInputs(authority,Objects.requireNonNull(keys));
    }

    public <T> T consume(Request request,JsonNode nativeFacts,
            BiFunction<RecoveryRecoveredTemporaryArchive,RecoveryTemporaryNativeHandoverFacts.Facts,T> consumer) {
        Objects.requireNonNull(consumer);
        return consumeAfterIdentityLocks(request,nativeFacts,new IdentityConsumer<T>() {
            @Override public void lockIdentity() { }
            @Override public T apply(RecoveryRecoveredTemporaryArchive archive,RecoveryTemporaryNativeHandoverFacts.Facts facts) {
                return consumer.apply(archive,facts);
            }
        });
    }

    /** Trusted identity locks precede original business rows; this callback may only lock/read, never emit effects. */
    public <T> T consumeAfterIdentityLocks(Request request,JsonNode nativeFacts,IdentityConsumer<T> consumer) {
        Objects.requireNonNull(request); Objects.requireNonNull(nativeFacts); Objects.requireNonNull(consumer);
        authority.read(); var original=inputs.read(request); var facts=nativeFacts.deepCopy();
        return new RecoveryClosedTransaction(jdbc.getDataSource(),authority).execute(original.source(),status -> {
            inputs.unchanged(original,request); consumer.lockIdentity(); target(request,original,consumer);
            T result=new RecoveryTemporaryNativeArchiveConsumer(jdbc).consume(archive(request,original),facts,consumer::apply);
            Runnable recheck=() -> { inputs.unchanged(original,request); target(request,original,consumer); };
            recheck.run();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void beforeCommit(boolean readOnly) { recheck.run(); }
            });
            return result;
        });
    }

    public interface IdentityConsumer<T> {
        void lockIdentity();
        T apply(RecoveryRecoveredTemporaryArchive archive,RecoveryTemporaryNativeHandoverFacts.Facts facts);
        /** A business consumer must independently prove its signed outbox effect before accepting a live order version change. */
        default void requireOrderVersion(JsonNode originalHead,JsonNode currentHead) {
            require(originalHead.path("orderVersion").equals(currentHead.path("orderVersion")),"RECOVERY_RECEIVED_PREREQUISITES_CHANGED");
        }
    }

    private void target(Request request,RecoveryReceivedTemporaryArchiveInputs.Original original,IdentityConsumer<?> consumer) {
        try {
            Path storage=RecoveryControlPaths.checked(request.storage());
            require(authority.location().restoreRoots().stream().anyMatch(storage::startsWith),"RECOVERY_RECEIVED_TARGET_STORAGE");
        } catch(java.io.IOException error) { throw new RecoveryControlException("RECOVERY_RECEIVED_TARGET_STORAGE",error); }
        var source=original.source(); var rows=original.material().path("rows");
        var current=new RecoveryReceivedTemporaryRows(jdbc).locked(request.evidence().expected(),source.original("payload"));
        consumer.requireOrderVersion(rows.path("head").deepCopy(),current.path("head").deepCopy());
        ((com.fasterxml.jackson.databind.node.ObjectNode)current.path("head")).set("orderVersion",rows.path("head").path("orderVersion"));
        require(current.equals(rows),"RECOVERY_RECEIVED_PREREQUISITES_CHANGED");
        require(rows.path("owners").equals(SubmissionJson.parse(source.original("occupancy")).path("rows")),
            "RECOVERY_RECEIVED_ARCHIVE_OWNER");
        var evidence=RecoveryUnsubmittedEvidence.expected(request.evidence());
        new RecoveryUnsubmittedEvidenceRows(jdbc).read(request.evidence().expected(),evidence);
        RecoveryReceivedTemporaryFiles.verify(request.storage(),rows,source.original("payload"));
        RecoveryTemporaryEvidenceFiles.verify(request.storage(),rows.path("evidence"),request.evidence());
        require(new RecoveryTemporaryNativeVerification(jdbc).readLocked(uuid(original.material().path("verification"),"id"))
            .equals(original.material().path("verification")),"RECOVERY_RECEIVED_VERIFICATION_CHANGED");
    }

    private RecoveryRecoveredTemporaryArchive archive(Request request,RecoveryReceivedTemporaryArchiveInputs.Original original) {
        var source=original.source(); var set=original.material().path("rows").path("evidence").path("source_evidence_set").get(0);
        var expected=RecoveryUnsubmittedEvidence.expected(request.evidence());
        var provenance=new RecoveryRecoveredTemporaryArchive.Provenance(source.control().recordSha256(),source.manifestSha256(),
            request.rows().independentSha256(),original.occupancy().originalSha256(),original.registration().originalSha256(),
            expected.proof().setHash(),expected.proof().payloadSha256());
        return new RecoveryRecoveredTemporaryArchive(request.evidence().expected(),rowId(set,"id"),rowId(set,"stop_object_id"),provenance,
            source.original("payload"),source.original("result"),expected.stopProofBytes());
    }

    private static UUID rowId(JsonNode row,String field) {
        var buffer=java.nio.ByteBuffer.wrap(Base64.getDecoder().decode(text(row,field))); return new UUID(buffer.getLong(),buffer.getLong());
    }

    public record Request(Path sourceRoot,RecoveryOriginalTemporaryPreparation.Request preparation,
            RecoveryDeviceRegistration.Request registration,RecoveryOriginalEvidence.Request evidence,RecoveryOriginalOccupancy.Request occupancy,
            List<RecoveryPermissionContinuity.Request> permissions,RecoveryReceivedTemporaryMaterial.Request rows,Path storage) {
        public Request {
            require(sourceRoot!=null && preparation!=null && registration!=null && evidence!=null && occupancy!=null
                && permissions!=null && rows!=null && storage!=null,"RECOVERY_RECEIVED_ARCHIVE_REQUEST");
            permissions=List.copyOf(permissions); require(!permissions.isEmpty() && permissions.size()<=512,"RECOVERY_EVIDENCE_PERMISSION_WINDOWS");
        }
    }
}
