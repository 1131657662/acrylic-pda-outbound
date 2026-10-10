package com.acrylic.outbound.recovery;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/**
 * Insert-only original verification restore. All received head/E2A/owner/file prerequisites must
 * already match the independently pinned original. Does not restore missing canonical, authority,
 * permissions or intake receipts; does not grant normal work, signing or handover admission.
 */
public final class RecoveryReceivedTemporaryVerificationRestore {
    private final JdbcTemplate jdbc;
    private final RecoveryClosedAuthority authority;
    private final RecoveryReceiptKeys keys;
    public RecoveryReceivedTemporaryVerificationRestore(DataSource database,RecoveryClosedAuthority authority,RecoveryReceiptKeys keys) {
        jdbc=new JdbcTemplate(Objects.requireNonNull(database)); this.authority=Objects.requireNonNull(authority); this.keys=Objects.requireNonNull(keys);
    }

    public Result restore(Request request) {
        Objects.requireNonNull(request); authority.read(); var source=source(request);
        var original=RecoveryReceivedTemporaryMaterial.read(request.rows(),authority,source);
        new RecoverySourceTerminal(new RecoveryReceiptVerifier(keys)).verify(source);
        return new RecoveryClosedTransaction(jdbc.getDataSource(),authority).execute(source,status -> {
            prerequisites(request,source,original);
            int inserted=insert(original.path("verification"));
            Runnable recheck=() -> {
                var after=source(request); require(after.control().equals(source.control()),"RECOVERY_RECEIVED_SOURCE_CHANGED");
                require(RecoveryReceivedTemporaryMaterial.read(request.rows(),authority,after).equals(original),"RECOVERY_RECEIVED_SOURCE_CHANGED");
                prerequisites(request,after,original);
                require(new RecoveryTemporaryNativeVerification(jdbc).readLocked(uuid(original.path("verification"),"id"))
                    .equals(original.path("verification")),"RECOVERY_RECEIVED_VERIFICATION_CHANGED");
            };
            recheck.run();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void beforeCommit(boolean readOnly) { recheck.run(); }
            });
            return new Result(inserted,uuid(original.path("verification"),"id"),sha256(RecoveryTemporaryEvidenceMaterial.encode(original.path("verification"))));
        });
    }

    private RecoveryPinnedSource source(Request request) {
        return new RecoverySourceReader(authority.location(),authority.checkpoint()).read(request.sourceRoot(),authority.sourcePinSha256(),authority.sourceIdentity());
    }

    private void prerequisites(Request request, RecoveryPinnedSource source, JsonNode original) {
        require(new RecoveryReceivedTemporaryRows(jdbc).locked(source.identity(),source.original("payload")).equals(original.path("rows")),
            "RECOVERY_RECEIVED_PREREQUISITES_CHANGED");
        try {
            Path storage=RecoveryControlPaths.checked(request.storage());
            require(authority.location().restoreRoots().stream().anyMatch(storage::startsWith),"RECOVERY_RECEIVED_TARGET_STORAGE");
        } catch(java.io.IOException error) { throw new RecoveryControlException("RECOVERY_RECEIVED_TARGET_STORAGE",error); }
        RecoveryReceivedTemporaryFiles.verify(request.storage(),original.path("rows"),source.original("payload"));
    }

    private int insert(JsonNode original) {
        var id=Ids.bytes(uuid(original,"id"));
        var existing=jdbc.queryForList("SELECT id FROM source_evidence_verification WHERE id=? FOR UPDATE",id);
        if(!existing.isEmpty()) {
            require(new RecoveryTemporaryNativeVerification(jdbc).readLocked(uuid(original,"id")).equals(original),"RECOVERY_RECEIVED_VERIFICATION_CONFLICT");
            return 0;
        }
        require(jdbc.update("""
            INSERT INTO source_evidence_verification(id,set_id,scope_id,submission_id,work_round,verification_version,algorithm_version,
                set_sha256,result,known_facts_sha256,findings_bytes,verified_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
            """,id,Ids.bytes(uuid(original,"set_id")),Ids.bytes(uuid(original,"scope_id")),Ids.bytes(uuid(original,"submission_id")),
            revision(original,"work_round"),revision(original,"verification_version"),revision(original,"algorithm_version"),
            text(original,"set_sha256"),text(original,"result"),text(original,"known_facts_sha256"),
            Base64.getDecoder().decode(text(original,"findings_bytes")),java.sql.Timestamp.valueOf(java.time.LocalDateTime.parse(text(original,"verified_at"))))==1,
            "RECOVERY_RECEIVED_VERIFICATION_WRITE");
        return 1;
    }

    public record Request(Path sourceRoot,RecoveryReceivedTemporaryMaterial.Request rows,Path storage) {
        public Request { require(sourceRoot!=null && rows!=null && storage!=null,"RECOVERY_RECEIVED_RESTORE_REQUEST"); }
    }
    public record Result(int insertedRows,UUID verificationId,String verificationSha256) {
        public boolean ordinaryWritesAllowed() { return false; }
        public boolean localDeletionAllowed() { return false; }
    }
}
