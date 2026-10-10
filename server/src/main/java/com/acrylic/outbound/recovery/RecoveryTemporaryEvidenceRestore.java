package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Explicit closed restoration of original E2A/reservations. Does not admit normal work or issue a new result. */
public final class RecoveryTemporaryEvidenceRestore {
    private final DataSource database;
    private final RecoveryClosedAuthority authority;
    private final RecoveryReceiptKeys keys;
    public RecoveryTemporaryEvidenceRestore(DataSource database,RecoveryClosedAuthority authority,RecoveryReceiptKeys keys) {
        this.database=Objects.requireNonNull(database); this.authority=Objects.requireNonNull(authority); this.keys=Objects.requireNonNull(keys);
    }

    public Result restore(Request request) {
        Objects.requireNonNull(request); authority.read(); var original=read(request); var jdbc=new JdbcTemplate(database);
        var rows=new RecoveryTemporaryEvidenceRows(jdbc);
        return new RecoveryClosedTransaction(database,authority).execute(original.source(),status -> {
            unchanged(request,original); target(request,original,jdbc);
            int inserted=rows.restore(original.material().path("rows"),original.source().identity());
            requireOwners(jdbc,original,request);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void beforeCommit(boolean readOnly) {
                    unchanged(request,original); target(request,original,jdbc);
                    require(rows.read(original.source().identity()).equals(original.material().path("rows")),"RECOVERY_EVIDENCE_ROW_SET");
                    requireOwners(jdbc,original,request);
                }
            });
            return new Result(inserted);
        });
    }

    private Original read(Request request) {
        var source=new RecoverySourceReader(authority.location(),authority.checkpoint())
            .read(request.source().materialRoot(),request.source().sourcePinSha256(),authority.sourceIdentity());
        require(source.manifestSha256().equals(authority.sourcePinSha256()),"CONTROL_CLOSED_SOURCE_MISMATCH");
        var preparation=new RecoveryOriginalTemporaryPreparation(authority.location(),authority.checkpoint(),keys).read(request.source().original(),source);
        var registration=new RecoveryDeviceRegistration(authority.location(),authority.checkpoint()).read(request.registration());
        var occupancy=new RecoveryUnsubmittedOccupancy(authority.location(),authority.checkpoint(),keys).read(request.occupancy(),source,request.evidence());
        new RecoveryInitialUnsubmittedSource(new RecoverySourceReader(authority.location(),authority.checkpoint()),
            new RecoveryUnsubmittedOccupancy(authority.location(),authority.checkpoint(),keys),keys).read(new RecoveryInitialUnsubmittedSource.Request(
                new RecoveryInitialSource.Request(request.source().materialRoot(),request.source().sourcePinSha256(),source.identity(),List.of()),
                request.evidence(),request.occupancy()));
        var permission=new RecoverySourcePermission(keys).verify(source);
        RecoveryTemporaryEvidenceTrust.verify(source,registration,request.evidence(),permission,request.permissions(),request.rows().database());
        require(request.rows().database().equals(request.occupancy().database()) && request.rows().database().equals(request.source().original().database()),
            "RECOVERY_EVIDENCE_ROWS_DATABASE");
        var material=new RecoveryTemporaryEvidenceMaterial(authority.location(),authority.checkpoint()).read(request.rows(),source,occupancy.originalSha256());
        RecoveryTemporaryEvidenceFacts.verify(material.path("rows"),source.identity(),request.evidence(),occupancy.occupancy(),instant(material,"observedAt"));
        return new Original(source,preparation,material,occupancy);
    }

    private void unchanged(Request request,Original expected) {
        var actual=read(request);
        require(expected.preparation().equals(actual.preparation()) && expected.material().equals(actual.material())
            && expected.occupancy().equals(actual.occupancy()) && expected.source().control().equals(actual.source().control()),"RECOVERY_EVIDENCE_SOURCE_CHANGED");
        for(String kind:RecoverySourceManifest.OBJECTS)
            require(Arrays.equals(expected.source().original(kind),actual.source().original(kind)),"RECOVERY_EVIDENCE_SOURCE_CHANGED");
    }

    private void target(Request request,Original original,JdbcTemplate jdbc) {
        try {
            Path storage=RecoveryControlPaths.checked(request.storage());
            require(authority.location().restoreRoots().stream().anyMatch(storage::startsWith),"RECOVERY_EVIDENCE_TARGET_STORAGE");
        } catch(java.io.IOException unavailable) { throw new RecoveryControlException("RECOVERY_EVIDENCE_TARGET_STORAGE",unavailable); }
        var material=RecoveryTemporaryRestoreMaterial.read(original.source(),original.preparation(),keys);
        new RecoveryTemporaryRestoreAuthorization(jdbc).requireMatches(material); new RecoveryTemporaryRestoreRows(jdbc).requireComplete(material);
        new RecoveryTemporaryEvidenceHead(jdbc).requireMatches(original.source());
        for(var row:original.occupancy().occupancy().rows()) {
            byte[] id=com.acrylic.outbound.common.Ids.bytes(row.baleId());
            require(jdbc.queryForList("SELECT id FROM bale WHERE id=? FOR UPDATE",id).size()==1,"RECOVERY_EVIDENCE_TARGET_BALE");
            require(jdbc.queryForList("SELECT bale_id FROM inventory_position WHERE bale_id=? FOR UPDATE",id).size()==1,
                "RECOVERY_EVIDENCE_TARGET_STOCK");
        }
        RecoveryTemporaryEvidenceFiles.verify(request.storage(),original.material().path("rows"),request.evidence());
    }

    private void requireOwners(JdbcTemplate jdbc,Original original,Request request) {
        var source=original.source();
        var expected=SubmissionJson.parse(source.original("occupancy")).path("rows");
        require(new RecoveryUnsubmittedOccupancyRows(jdbc).readLocked(source.identity(),SubmissionJson.parse(source.original("payload")),
            original.occupancy().occupancy().sourceEvidenceSetId()).equals(expected),"RECOVERY_EVIDENCE_TARGET_OWNER");
        new RecoveryUnsubmittedEvidenceRows(jdbc).read(source.identity(),RecoveryUnsubmittedEvidence.expected(request.evidence()));
    }

    public record Request(RecoveryTemporaryPreparationRestore.Request source,RecoveryDeviceRegistration.Request registration,
            RecoveryOriginalEvidence.Request evidence,RecoveryUnsubmittedOccupancy.Request occupancy,
            List<RecoveryPermissionContinuity.Request> permissions,RecoveryTemporaryEvidenceMaterial.Request rows,Path storage) {
        public Request {
            require(source!=null && registration!=null && evidence!=null && occupancy!=null && permissions!=null && rows!=null && storage!=null,
                "RECOVERY_EVIDENCE_RESTORE_REQUEST"); permissions=List.copyOf(permissions);
            require(permissions.size()<=512,"RECOVERY_EVIDENCE_PERMISSION_WINDOWS");
        }
    }
    public record Result(int insertedRows) {
        public boolean ordinaryWritesAllowed() { return false; }
        public boolean localDeletionAllowed() { return false; }
    }
    private record Original(RecoveryPinnedSource source,RecoveryOriginalTemporaryPreparation.Proof preparation,
            JsonNode material,RecoveryUnsubmittedOccupancy.Proof occupancy) { }
}
