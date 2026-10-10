package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

class RecoveryTemporaryEvidenceMaterialTest {
    @Test void pinnedAllNativeRowsRemainExact() throws Exception {
        var f=new Material(); var bytes=Files.readAllBytes(f.root.resolve(f.pin+".json"));
        assertThat(f.read()).isEqualTo(f.value);
        assertThat(Files.readAllBytes(f.root.resolve(f.pin+".json"))).containsExactly(bytes);
    }
    @Test void absentIndependentPinIsRejected() throws Exception {
        var f=new Material(); f.pin=null;
        assertThatThrownBy(f::read).hasMessage("RECOVERY_EVIDENCE_ROWS_PIN");
    }
    @Test void changedBytesUnderOriginalPinAreRejected() throws Exception {
        var f=new Material(); Files.writeString(f.root.resolve(f.pin+".json"),"{}");
        assertThatThrownBy(f::read).hasMessage("RECOVERY_EVIDENCE_ROWS_PIN_MISMATCH");
    }
    @ParameterizedTest @ValueSource(strings={"databaseSchema","sourceRecoveryEpoch","observationKind"})
    void repinnedMaterialCannotChangeOriginalDatabaseOrPurpose(String field) throws Exception {
        var f=new Material(); f.value.put(field,"other"); f.publish();
        assertThatThrownBy(f::read).hasMessage("RECOVERY_EVIDENCE_ROWS_DATABASE");
    }
    @Test void anotherFiveObjectSourceCannotProvideNativeRows() throws Exception {
        var f=new Material(); ((ObjectNode)f.value.path("sourceObjects")).put("payload","a".repeat(64)); f.publish();
        assertThatThrownBy(f::read).hasMessage("RECOVERY_EVIDENCE_ROWS_SOURCE");
    }
    @Test void anotherOccupancyPinCannotProvideNativeRows() throws Exception {
        var f=new Material(); f.value.put("occupancyPin","a".repeat(64)); f.publish();
        assertThatThrownBy(f::read).hasMessage("RECOVERY_EVIDENCE_ROWS_SOURCE");
    }
    @Test void observationAfterIndependentDeadlineIsRejected() throws Exception {
        var f=new Material(); f.value.put("observedAt",f.deadline.plusMillis(1).toString()); f.publish();
        assertThatThrownBy(f::read).hasMessage("RECOVERY_EVIDENCE_ROWS_TIME");
    }
    @Test void materialInsideRestoredSetCannotBecomeIndependentPin() throws Exception {
        var f=new Material(); f.root=Files.createDirectory(f.rows.storage.resolve("claimed-independent")); f.publish();
        assertThatThrownBy(f::read).hasMessage("RECOVERY_SOURCE_INSIDE_RESTORE_SET");
    }

    private static final class Material {
        final RecoveryTemporaryEvidenceRowsFixture rows=new RecoveryTemporaryEvidenceRowsFixture();
        final Instant deadline=rows.observed;
        final ObjectNode value=SubmissionJson.object();
        Path root=rows.source.initial.state.authority.source.materials;
        String pin;
        Material() throws Exception {
            var source=rows.source.initial.source(); var database=rows.source.database;
            value.put("formatVersion",1).put("observationKind","ORIGINAL_TEMP_EVIDENCE_ROWS").put("mysqlServerUuid",database.mysqlServerUuid().toString())
                .put("databaseSchema",database.schema()).put("sourceRecoveryEpoch",database.sourceRecoveryEpoch()).put("observedAt",deadline.toString())
                .put("occupancyPin",rows.source.pin);
            value.set("source",RecoveryOriginalOccupancyFacts.source(source.identity())); value.set("rows",rows.rows.deepCopy());
            var hashes=value.putObject("sourceObjects"); for(String kind:RecoverySourceManifest.OBJECTS) hashes.put(kind,sha256(source.original(kind)));
            publish();
        }
        void publish() throws Exception {
            byte[] bytes=RecoveryTemporaryEvidenceMaterial.encode(value); pin=sha256(bytes); Files.write(root.resolve(pin+".json"),bytes);
        }
        com.fasterxml.jackson.databind.JsonNode read() {
            var source=rows.source.initial.source(); var control=rows.source.initial.state.authority.source.control;
            return new RecoveryTemporaryEvidenceMaterial(control.location(),control.checkpoint)
                .read(new RecoveryTemporaryEvidenceMaterial.Request(root,pin,rows.source.database,deadline),source,rows.source.pin);
        }
    }
}
