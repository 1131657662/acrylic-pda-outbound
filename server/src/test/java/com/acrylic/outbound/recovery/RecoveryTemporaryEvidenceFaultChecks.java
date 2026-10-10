package com.acrylic.outbound.recovery;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.*;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Fault injection in one new target; rejected writes are compared against all tables and files. */
final class RecoveryTemporaryEvidenceFaultChecks {
    static void verify(RecoveryTemporaryEvidenceTargetChecks.Context c) throws Exception {
        pins(c); registration(c); files(c); stock(c); terminal(c); lastSql(c); originalChange(c); partial(c);
    }
    private static void pins(RecoveryTemporaryEvidenceTargetChecks.Context c) throws Exception {
        var request=c.request(); var rows=request.rows();
        var missing=new RecoveryTemporaryEvidenceMaterial.Request(rows.materialRoot(),null,rows.database(),rows.notAfter());
        assertThatThrownBy(() -> c.restorer().restore(new RecoveryTemporaryEvidenceRestore.Request(request.source(),request.registration(),request.evidence(),
            request.occupancy(),request.permissions(),missing,request.storage()))).hasMessage("RECOVERY_EVIDENCE_ROWS_PIN"); c.preserved();
        assertThatThrownBy(() -> c.restorer().restore(new RecoveryTemporaryEvidenceRestore.Request(request.source(),request.registration(),request.evidence(),
            request.occupancy(),List.of(),rows,request.storage()))).hasMessage("RECOVERY_EVIDENCE_PERMISSION_WINDOWS"); c.preserved();
    }
    private static void stock(RecoveryTemporaryEvidenceTargetChecks.Context c) throws Exception {
        byte[] bale=Ids.bytes(RecoveryControlFormat.uuid(SubmissionJson.parse(c.source().read().original("payload")).at("/scans/0"),"baleId"));
        c.jdbc().update("UPDATE inventory_position SET upstream_present=0 WHERE bale_id=?",bale); var before=c.snapshots().capture();
        try { assertThatThrownBy(() -> c.restorer().restore(c.request())).hasMessage("RECOVERY_UNSUBMITTED_UNAVAILABLE");
            assertThat(c.snapshots().capture()).isEqualTo(before); }
        finally { c.jdbc().update("UPDATE inventory_position SET upstream_present=1 WHERE bale_id=?",bale); } c.preserved();
    }
    private static void registration(RecoveryTemporaryEvidenceTargetChecks.Context c) throws Exception {
        var request=c.request(); var registration=request.registration(); var expected=registration.expected();
        var wrong=new RecoveryDeviceRegistration.Expected(UUID.randomUUID(),expected.databaseSchema(),expected.sourceRecoveryEpoch(),
            expected.warehouseId(),expected.deviceId(),expected.notAfter());
        var changed=new RecoveryDeviceRegistration.Request(registration.materialRoot(),registration.independentSha256(),wrong);
        assertThatThrownBy(() -> c.restorer().restore(new RecoveryTemporaryEvidenceRestore.Request(request.source(),changed,request.evidence(),
            request.occupancy(),request.permissions(),request.rows(),request.storage()))).hasMessage("RECOVERY_DEVICE_REGISTRATION_DATABASE"); c.preserved();
    }
    private static void files(RecoveryTemporaryEvidenceTargetChecks.Context c) throws Exception {
        var material=readCanonical(Files.readAllBytes(c.request().rows().materialRoot().resolve(c.request().rows().independentSha256()+".json")));
        Path file=c.request().storage().resolve(text(material.path("rows").path("source_evidence_object").get(0),"file_key"));
        byte[] original=Files.readAllBytes(file); Files.writeString(file,"changed-original-object");
        try { var before=c.snapshots().capture(); assertThatThrownBy(() -> c.restorer().restore(c.request())).hasMessage("RECOVERY_EVIDENCE_FILE_CHANGED");
            assertThat(c.snapshots().capture()).isEqualTo(before); }
        finally { Files.write(file,original); } c.preserved();
    }
    private static void terminal(RecoveryTemporaryEvidenceTargetChecks.Context c) throws Exception {
        byte[] id=Ids.bytes(c.source().identity.submissionId()); var receipt=c.source().read().original("result");
        c.jdbc().update("UPDATE submission SET receipt_text='another-original' WHERE id=?",id);
        try { assertThatThrownBy(() -> c.restorer().restore(c.request())).hasMessage("RECOVERY_EVIDENCE_HEAD_TERMINAL"); }
        finally { c.jdbc().update("UPDATE submission SET receipt_text=? WHERE id=?",new String(receipt,java.nio.charset.StandardCharsets.UTF_8),id); } c.preserved();
    }
    private static void lastSql(RecoveryTemporaryEvidenceTargetChecks.Context c) throws Exception {
        c.jdbc().execute("CREATE TRIGGER evidence_restore_tail BEFORE INSERT ON bale_commitment FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='EVIDENCE_RESTORE_TAIL'");
        try { assertThatThrownBy(() -> c.restorer().restore(c.request())).isInstanceOf(org.springframework.dao.DataAccessException.class)
            .hasMessageContaining("EVIDENCE_RESTORE_TAIL"); }
        finally { c.jdbc().execute("DROP TRIGGER evidence_restore_tail"); } c.preserved();
        Files.writeString(c.attempt().resolve("evidence-tail-sql.txt"),"PASS actual last reservation INSERT failure rolls all original E2A rows back\n");
    }
    private static void originalChange(RecoveryTemporaryEvidenceTargetChecks.Context c) throws Exception {
        Path file=c.request().rows().materialRoot().resolve(c.request().rows().independentSha256()+".json"); byte[] original=Files.readAllBytes(file);
        var boundary=new RecoveryTemporaryEvidenceMutationDataSource(c.jdbc().getDataSource(),() -> {
            try { Files.writeString(file,"changed-before-commit"); } catch(java.io.IOException error) { throw new IllegalStateException(error); }
        });
        try {
            assertThatThrownBy(() -> new RecoveryTemporaryEvidenceRestore(boundary,c.supply().authority(),c.keys()).restore(c.request()))
                .hasMessage("RECOVERY_EVIDENCE_ROWS_PIN_MISMATCH"); assertThat(boundary.mutated()).isTrue();
        } finally { Files.write(file,original); } c.preserved();
    }
    private static void partial(RecoveryTemporaryEvidenceTargetChecks.Context c) throws Exception {
        var material=readCanonical(Files.readAllBytes(c.request().rows().materialRoot().resolve(c.request().rows().independentSha256()+".json")));
        var row=material.path("rows").path("source_evidence_object").get(0); var table=RecoveryTemporaryEvidenceTable.OBJECT;
        c.jdbc().update(table.insert(),RecoveryTemporaryEvidenceColumns.values(row,table,table.columns)); var before=c.snapshots().capture();
        c.jdbc().update("UPDATE source_evidence_object SET file_key='different.bin' WHERE id=?",Base64.getDecoder().decode(text(row,"id")));
        try { assertThatThrownBy(() -> c.restorer().restore(c.request())).hasMessage("RECOVERY_EVIDENCE_ROW_CONFLICT"); }
        finally { c.jdbc().update("UPDATE source_evidence_object SET file_key=? WHERE id=?",text(row,"file_key"),Base64.getDecoder().decode(text(row,"id"))); }
        assertThat(c.snapshots().capture()).isEqualTo(before);
        c.jdbc().update("DELETE FROM source_evidence_object WHERE id=?",Base64.getDecoder().decode(text(row,"id"))); c.preserved();
    }
}
