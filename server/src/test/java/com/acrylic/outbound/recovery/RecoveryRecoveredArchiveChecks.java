package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Actual new-target read-only checks. Missing native receipts/verification are never synthesized. */
final class RecoveryRecoveredArchiveChecks {
    static void verify(RecoveryTemporaryEvidenceTargetChecks.Context c,JsonNode complete) throws Exception {
        var archive=c.restorer().verifyArchive(c.request()); var proof=new RecoveryOriginalEvidence().read(c.request().evidence());
        assertThat(archive.source()).isEqualTo(c.request().evidence().expected());
        assertThat(archive.payload()).isEqualTo(c.source().read().original("payload"));
        assertThat(archive.terminalReceipt()).isEqualTo(c.source().read().original("result"));
        assertThat(archive.stopProof()).isEqualTo(RecoveryUnsubmittedEvidence.expected(c.request().evidence()).stopProofBytes());
        assertThat(archive.provenance()).isEqualTo(new RecoveryRecoveredTemporaryArchive.Provenance(c.source().read().control().recordSha256(),
            c.source().pin,c.request().rows().independentSha256(),c.request().occupancy().independentSha256(),
            c.request().registration().independentSha256(),proof.setHash(),proof.payloadSha256()));
        assertThat(archive.ordinaryWritesAllowed()).isFalse(); assertThat(archive.localDeletionAllowed()).isFalse();
        assertThat(c.snapshots().capture()).isEqualTo(complete);
        assertThat(c.restorer().verifyArchive(c.request()).evidenceSetId()).isEqualTo(archive.evidenceSetId());
        assertThat(c.snapshots().capture()).isEqualTo(complete);
        row(c); file(c); pin(c); beforeCommit(c); assertThat(c.snapshots().capture()).isEqualTo(complete);
        var output=SubmissionJson.object().put("evidenceSetId",archive.evidenceSetId().toString()).put("stopObjectId",archive.stopObjectId().toString())
            .put("payloadBase64",Base64.getEncoder().encodeToString(archive.payload())).put("terminalReceiptBase64",Base64.getEncoder().encodeToString(archive.terminalReceipt()))
            .put("stopProofBase64",Base64.getEncoder().encodeToString(archive.stopProof())).put("setSha256",proof.setHash())
            .put("ordinaryWritesAllowed",false).put("localDeletionAllowed",false);
        output.set("provenance",new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(archive.provenance()));
        Files.write(c.attempt().resolve("recovered-archive-read.json"),RecoveryControlJson.encode(output));
    }

    private static void row(RecoveryTemporaryEvidenceTargetChecks.Context c) throws Exception {
        var row=new RecoveryTemporaryEvidenceRows(c.jdbc()).readOriginal(c.source().identity).path("source_evidence_object").get(0);
        byte[] id=Base64.getDecoder().decode(text(row,"id"));
        c.jdbc().update("UPDATE source_evidence_object SET file_key='changed.bin' WHERE id=?",id);
        try { refused(c,"RECOVERY_ARCHIVE_RESTORED_ROWS_REQUIRED"); }
        finally { c.jdbc().update("UPDATE source_evidence_object SET file_key=? WHERE id=?",text(row,"file_key"),id); }
    }

    private static void file(RecoveryTemporaryEvidenceTargetChecks.Context c) throws Exception {
        var row=new RecoveryTemporaryEvidenceRows(c.jdbc()).readOriginal(c.source().identity).path("source_evidence_object").get(0);
        Path file=c.request().storage().resolve(text(row,"file_key")); byte[] original=Files.readAllBytes(file); Files.writeString(file,"damaged");
        try { refused(c,"RECOVERY_EVIDENCE_FILE_CHANGED"); }
        finally { Files.write(file,original); }
    }

    private static void pin(RecoveryTemporaryEvidenceTargetChecks.Context c) throws Exception {
        Path file=c.request().registration().materialRoot().resolve(c.request().registration().independentSha256()+".json");
        byte[] original=Files.readAllBytes(file); Files.writeString(file,"changed-root");
        try { refused(c,"RECOVERY_DEVICE_REGISTRATION_PIN_MISMATCH"); }
        finally { Files.write(file,original); }
    }

    private static void beforeCommit(RecoveryTemporaryEvidenceTargetChecks.Context c) throws Exception {
        Path file=c.request().rows().materialRoot().resolve(c.request().rows().independentSha256()+".json"); byte[] original=Files.readAllBytes(file);
        var boundary=new RecoveryArchiveMutationDataSource(c.jdbc().getDataSource(),() -> {
            try { Files.writeString(file,"changed-before-commit"); } catch(java.io.IOException error) { throw new IllegalStateException(error); }
        });
        try {
            assertThatThrownBy(() -> new RecoveryTemporaryEvidenceRestore(boundary,c.supply().authority(),c.keys()).verifyArchive(c.request()))
                .hasMessage("RECOVERY_EVIDENCE_ROWS_PIN_MISMATCH"); assertThat(boundary.mutated()).isTrue();
        } finally { Files.write(file,original); }
    }

    private static void refused(RecoveryTemporaryEvidenceTargetChecks.Context c,String message) throws Exception {
        var before=c.snapshots().capture();
        assertThatThrownBy(() -> c.restorer().verifyArchive(c.request())).hasMessage(message);
        assertThat(c.snapshots().capture()).isEqualTo(before);
    }
}
