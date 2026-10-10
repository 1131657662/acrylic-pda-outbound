package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Independently pinned original native rows, kept outside both the authority and restore set. */
public final class RecoveryTemporaryEvidenceMaterial {
    static final int MAX_BYTES=33554432;
    private final RecoveryControlLocation configured;
    private final Path checkpoint;
    public RecoveryTemporaryEvidenceMaterial(RecoveryControlLocation configured,Path checkpoint) {
        this.configured=Objects.requireNonNull(configured); this.checkpoint=Objects.requireNonNull(checkpoint);
    }

    JsonNode read(Request request,RecoveryPinnedSource source,String occupancyPin) {
        require(request!=null && request.independentSha256()!=null && request.independentSha256().matches("[a-f0-9]{64}")
            && !ZERO_HASH.equals(request.independentSha256()),"RECOVERY_EVIDENCE_ROWS_PIN");
        try {
            var location=configured.checked(); var reader=new RecoveryControlReader(location); var before=reader.read(checkpoint);
            require(before.equals(source.control()),"RECOVERY_EVIDENCE_ROWS_CONTROL");
            Path root=RecoveryControlPaths.checked(request.materialRoot()); outside(root,location);
            Path file=RecoveryControlPaths.file(root.resolve(request.independentSha256()+".json")); byte[] bytes;
            try(var input=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)) { bytes=input.readNBytes(MAX_BYTES+1); }
            require(bytes.length>0 && bytes.length<=MAX_BYTES && sha256(bytes).equals(request.independentSha256()),"RECOVERY_EVIDENCE_ROWS_PIN_MISMATCH");
            var value=decode(bytes); verify(value,request,source,occupancyPin);
            require(before.equals(reader.read(checkpoint)),"RECOVERY_EVIDENCE_ROWS_CONTROL"); return value;
        } catch(java.io.IOException unavailable) { throw new RecoveryControlException("RECOVERY_EVIDENCE_ROWS_UNAVAILABLE",unavailable); }
    }

    private void verify(JsonNode value,Request request,RecoveryPinnedSource source,String occupancyPin) {
        fields(value,"formatVersion observationKind mysqlServerUuid databaseSchema sourceRecoveryEpoch observedAt source sourceObjects occupancyPin rows"); version(value);
        var database=request.database();
        require(text(value,"observationKind").equals("ORIGINAL_TEMP_EVIDENCE_ROWS")
            && uuid(value,"mysqlServerUuid").equals(database.mysqlServerUuid()) && text(value,"databaseSchema").equals(database.schema())
            && text(value,"sourceRecoveryEpoch").equals(database.sourceRecoveryEpoch())
            && database.sourceRecoveryEpoch().equals(source.identity().sourceRecoveryEpoch()),"RECOVERY_EVIDENCE_ROWS_DATABASE");
        require(RecoverySourceIdentity.read(value.path("source")).equals(source.identity())
            && text(value,"occupancyPin").equals(occupancyPin),"RECOVERY_EVIDENCE_ROWS_SOURCE");
        fields(value.path("sourceObjects"),String.join(" ",RecoverySourceManifest.OBJECTS));
        for(String kind:RecoverySourceManifest.OBJECTS)
            require(text(value.path("sourceObjects"),kind).equals(sha256(source.original(kind))),"RECOVERY_EVIDENCE_ROWS_SOURCE");
        require(!instant(value,"observedAt").isAfter(request.notAfter()),"RECOVERY_EVIDENCE_ROWS_TIME");
        fields(value.path("rows"),"source_evidence_object source_evidence_set source_evidence_item bale_commitment");
    }

    private void outside(Path root,RecoveryControlLocation location) throws java.io.IOException {
        require(!RecoveryControlPaths.overlap(root,location.root()) && !RecoveryControlPaths.overlap(root,location.checkpoint(checkpoint)),
            "RECOVERY_SOURCE_INSIDE_AUTHORITY");
        for(Path restored:location.restoreRoots()) require(!RecoveryControlPaths.overlap(root,restored),"RECOVERY_SOURCE_INSIDE_RESTORE_SET");
    }
    static byte[] encode(JsonNode value) {
        byte[] bytes=(SubmissionJson.canonical(value)+"\n").getBytes(StandardCharsets.UTF_8);
        require(bytes.length<=MAX_BYTES,"RECOVERY_EVIDENCE_ROWS_SIZE"); return bytes;
    }
    static JsonNode decode(byte[] bytes) {
        var value=SubmissionJson.parse(bytes);
        require(Arrays.equals(encode(value),bytes),"RECOVERY_EVIDENCE_ROWS_CANONICAL"); return value;
    }
    public record Request(Path materialRoot,String independentSha256,RecoveryDeviceRegistrationCapture.Database database,Instant notAfter) {
        public Request { require(materialRoot!=null && database!=null && notAfter!=null,"RECOVERY_EVIDENCE_ROWS_REQUEST"); }
    }
}
