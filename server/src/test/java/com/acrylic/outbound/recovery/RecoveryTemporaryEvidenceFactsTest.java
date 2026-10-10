package com.acrylic.outbound.recovery;

import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RecoveryTemporaryEvidenceFactsTest {
    @Test void completeOriginalIdsPagesOwnerColumnsAndTimesRemainUnchanged() throws Exception {
        var f=new RecoveryTemporaryEvidenceRowsFixture(); var before=f.rows.deepCopy();
        f.verify(); RecoveryTemporaryEvidenceFiles.verify(f.storage,f.rows,f.source.evidence.request());
        assertThat(f.rows).isEqualTo(before);
    }
    @Test void missingManifestPageCannotBecomeCompleteRecovery() throws Exception {
        var f=new RecoveryTemporaryEvidenceRowsFixture(); var objects=(com.fasterxml.jackson.databind.node.ArrayNode)f.rows.path("source_evidence_object");
        objects.remove(objects.size()-1);
        assertThatThrownBy(f::verify).hasMessage("RECOVERY_EVIDENCE_ROW_PAGE");
    }
    @Test void changedNativeRecordSequenceCannotBecomeCompleteRecovery() throws Exception {
        var f=new RecoveryTemporaryEvidenceRowsFixture(); f.first("source_evidence_item").put("original_device_seq","999");
        assertThatThrownBy(f::verify).hasMessage("RECOVERY_EVIDENCE_ROW_ITEMS");
    }
    @Test void alternativeOwnerCannotReplaceOriginalReservation() throws Exception {
        var f=new RecoveryTemporaryEvidenceRowsFixture(); f.first("bale_commitment").put("submission_id",RecoveryTemporaryEvidenceColumns.uuidBytes(UUID.randomUUID()));
        assertThatThrownBy(f::verify).hasMessage("RECOVERY_EVIDENCE_ROW_OWNER");
    }
    @Test void timeAfterOriginalObservationCannotBeInvented() throws Exception {
        var f=new RecoveryTemporaryEvidenceRowsFixture(); f.first("bale_commitment").put("created_at",f.observed.plusMillis(1).toString());
        assertThatThrownBy(f::verify).hasMessage("RECOVERY_EVIDENCE_ROW_TIME");
    }
    @Test void changedFileFailsWithoutWritingAnyRowOrFile() throws Exception {
        var f=new RecoveryTemporaryEvidenceRowsFixture(); var path=f.storage.resolve(f.first("source_evidence_object").path("file_key").textValue());
        Files.writeString(path,"damaged-original"); var before=Files.readAllBytes(path);
        assertThatThrownBy(() -> RecoveryTemporaryEvidenceFiles.verify(f.storage,f.rows,f.source.evidence.request())).hasMessage("RECOVERY_EVIDENCE_FILE_CHANGED");
        assertThat(Files.readAllBytes(path)).containsExactly(before);
    }
    @Test void redirectedFileKeyIsRejected() throws Exception {
        var f=new RecoveryTemporaryEvidenceRowsFixture(); f.first("source_evidence_object").put("file_key","../outside.bin");
        assertThatThrownBy(() -> RecoveryTemporaryEvidenceFiles.verify(f.storage,f.rows,f.source.evidence.request())).hasMessage("RECOVERY_EVIDENCE_FILE_KEY");
    }
    @Test void nativeTimestampAndBinaryColumnsRoundTripWithoutClockOrNewIds() {
        var row=com.acrylic.outbound.submissions.SubmissionJson.object(); var table=RecoveryTemporaryEvidenceTable.RESERVATION;
        for(String field:table.columns) row.putNull(field);
        UUID id=UUID.randomUUID(); row.put("bale_id",RecoveryTemporaryEvidenceColumns.uuidBytes(id)).put("dispatch_cycle","2")
            .put("state","SOURCE_RESERVED").put("created_at","2026-09-20T00:30:00.123Z");
        Object[] values=RecoveryTemporaryEvidenceColumns.values(row,table,table.columns);
        assertThat(values[0]).isEqualTo(com.acrylic.outbound.common.Ids.bytes(id));
        assertThat(((java.sql.Timestamp)values[5]).toInstant()).isEqualTo(Instant.parse("2026-09-20T00:30:00.123Z"));
    }
}
