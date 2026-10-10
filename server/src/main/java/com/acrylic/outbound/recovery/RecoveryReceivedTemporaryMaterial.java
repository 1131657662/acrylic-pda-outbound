package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Independent original row pin, restricted to received ABORTED TEMP prerequisites. */
public final class RecoveryReceivedTemporaryMaterial {
    private RecoveryReceivedTemporaryMaterial() { }

    static JsonNode read(Request request, RecoveryClosedAuthority authority, RecoveryPinnedSource source) {
        return read(request,authority,source,source.identity());
    }

    /** The signed next STOP supplies the row boundary; the pinned/control source keeps its frozen identity. */
    static JsonNode readAtStop(Request request, RecoveryClosedAuthority authority, RecoveryPinnedSource source,
            RecoveryOriginalEvidence.Request evidence) {
        RecoveryNativeTemporaryAudit.verify(source,evidence);
        return read(request,authority,source,evidence.expected());
    }

    private static JsonNode read(Request request, RecoveryClosedAuthority authority, RecoveryPinnedSource source,RecoverySourceIdentity rowIdentity) {
        require(request.independentSha256()!=null && request.independentSha256().matches("[a-f0-9]{64}")
            && !ZERO_HASH.equals(request.independentSha256()),"RECOVERY_RECEIVED_PIN_REQUIRED");
        try {
            var location=authority.location().checked(); Path root=RecoveryControlPaths.checked(request.materialRoot());
            require(!RecoveryControlPaths.overlap(root,location.root()) && !RecoveryControlPaths.overlap(root,authority.authorityRoot())
                && !RecoveryControlPaths.overlap(root,location.checkpoint(authority.checkpoint())),"RECOVERY_RECEIVED_PIN_LOCATION");
            for(Path restored:location.restoreRoots()) require(!RecoveryControlPaths.overlap(root,restored),"RECOVERY_RECEIVED_PIN_LOCATION");
            byte[] bytes;
            try(var input=Files.newInputStream(RecoveryControlPaths.file(root.resolve(request.independentSha256()+".json")),LinkOption.NOFOLLOW_LINKS)) {
                bytes=input.readNBytes(RecoveryTemporaryEvidenceMaterial.MAX_BYTES+1);
            }
            require(bytes.length>0 && bytes.length<=RecoveryTemporaryEvidenceMaterial.MAX_BYTES
                && sha256(bytes).equals(request.independentSha256()),"RECOVERY_RECEIVED_PIN_MISMATCH");
            var value=RecoveryTemporaryEvidenceMaterial.decode(bytes); binding(value,request,source,rowIdentity); return value;
        } catch(java.io.IOException error) { throw new RecoveryControlException("RECOVERY_RECEIVED_PIN_UNAVAILABLE",error); }
    }

    private static void binding(JsonNode value, Request request, RecoveryPinnedSource source,RecoverySourceIdentity rowIdentity) {
        fields(value,"formatVersion observationKind mysqlServerUuid databaseSchema sourceRecoveryEpoch observedAt source rows verification payloadSha256"); version(value);
        var database=request.database();
        require(text(value,"observationKind").equals("ORIGINAL_RECEIVED_TEMP_VERIFICATION")
            && uuid(value,"mysqlServerUuid").equals(database.mysqlServerUuid()) && text(value,"databaseSchema").equals(database.schema())
            && text(value,"sourceRecoveryEpoch").equals(database.sourceRecoveryEpoch())
            && database.sourceRecoveryEpoch().equals(source.identity().sourceRecoveryEpoch())
            && RecoverySourceIdentity.read(value.path("source")).equals(rowIdentity)
            && !instant(value,"observedAt").isAfter(request.notAfter()),"RECOVERY_RECEIVED_PIN_SOURCE");
        var rows=value.path("rows"); fields(rows,"head evidence owners history");
        require(text(value,"payloadSha256").equals(SubmissionJson.sha(SubmissionJson.parse(source.original("payload"))))
            && text(rows.path("head"),"receipt_text").equals(new String(source.original("result"),java.nio.charset.StandardCharsets.UTF_8)),
            "RECOVERY_RECEIVED_PIN_SOURCE");
        verification(value.path("verification"),rowIdentity,rows.path("evidence"));
    }

    static void verification(JsonNode row, RecoverySourceIdentity id, JsonNode evidence) {
        fields(row,"id set_id scope_id submission_id work_round verification_version algorithm_version set_sha256 result known_facts_sha256 findings_bytes verified_at");
        var sets=SubmissionJson.array(evidence,"source_evidence_set"); require(sets.size()==1,"RECOVERY_RECEIVED_VERIFICATION"); var set=sets.get(0);
        for(var table:List.of(RecoveryTemporaryEvidenceTable.OBJECT,RecoveryTemporaryEvidenceTable.SET,RecoveryTemporaryEvidenceTable.ITEM))
            for(var original:SubmissionJson.array(evidence,table.table)) {
                RecoveryTemporaryEvidenceColumns.equalsId(original,"warehouse_id",id.warehouseId());
                RecoveryTemporaryEvidenceColumns.equalsId(original,"scope_id",id.scopeId());
                RecoveryTemporaryEvidenceColumns.equalsId(original,"submission_id",id.submissionId());
                require(revision(original,"work_round")==1,"RECOVERY_RECEIVED_VERIFICATION");
            }
        require(uuid(row,"scope_id").equals(id.scopeId()) && uuid(row,"submission_id").equals(id.submissionId())
            && revision(row,"work_round")==id.workRound() && revision(row,"verification_version")==1 && revision(row,"algorithm_version")==1
            && RecoveryTemporaryEvidenceColumns.uuidBytes(uuid(row,"set_id")).equals(text(set,"id"))
            && text(row,"set_sha256").equals(text(set,"set_sha256")) && text(row,"result").equals("VERIFIED_COMPLETE_AT_STOP"),
            "RECOVERY_RECEIVED_VERIFICATION");
        hashField(row,"known_facts_sha256"); uuid(row,"id");
        byte[] findings=Base64.getDecoder().decode(text(row,"findings_bytes"));
        require(findings.length>0 && findings.length<=262144 && Base64.getEncoder().encodeToString(findings).equals(text(row,"findings_bytes")),
            "RECOVERY_RECEIVED_VERIFICATION");
        var time=java.time.LocalDateTime.parse(text(row,"verified_at"));
        require(time.getNano()%1000000==0 && text(row,"verified_at").matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{6}"),
            "RECOVERY_RECEIVED_VERIFICATION");
    }

    public record Request(Path materialRoot,String independentSha256,RecoveryDeviceRegistrationCapture.Database database,Instant notAfter) {
        public Request { require(materialRoot!=null && database!=null && notAfter!=null,"RECOVERY_RECEIVED_MATERIAL_REQUEST"); }
    }
}
