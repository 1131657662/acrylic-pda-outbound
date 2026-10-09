package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RecoveryAuditArchiveProofTest {
    @Test void rejectsAnotherSignaturePurposeEvenWhenTheContentAndSignatureMatch() throws Exception {
        var f = new RecoveryAuditFixture(); f.archivePrefix();
        f.resignPrefix((ObjectNode)f.prefix.getFirst().proof(), "acrylic-pda-archive");

        assertThatThrownBy(() -> f.verifier().verify(f.bytes(), f.context())).hasMessage("RECOVERY_AUDIT_ARCHIVE_PURPOSE");
    }

    @ParameterizedTest @ValueSource(strings = {"warehouseId", "originDeviceId"})
    void refusesAnotherArchivedSourceEvenWhenSigned(String field) throws Exception {
        var f = new RecoveryAuditFixture(); f.archivePrefix(); var proof = (ObjectNode)f.prefix.getFirst().proof();
        proof.put(field, java.util.UUID.randomUUID().toString()); f.resignPrefix(proof, "acrylic-pda-audit-archive");

        assertThatThrownBy(() -> f.verifier().verify(f.bytes(), f.context())).hasMessage("RECOVERY_AUDIT_ARCHIVE_SOURCE");
    }

    @Test void refusesAnOldTransportEpochAsTheOriginalAuditProof() throws Exception {
        var f = new RecoveryAuditFixture(); f.archivePrefix(); var proof = (ObjectNode)f.prefix.getFirst().proof();
        proof.put("recoveryEpoch", "0"); f.resignPrefix(proof, "acrylic-pda-audit-archive");

        assertThatThrownBy(() -> f.verifier().verify(f.bytes(), f.context())).hasMessage("RECOVERY_AUDIT_ARCHIVE_SOURCE");
    }

    @Test void refusesADeletionPermitEvenWhenItWasSigned() throws Exception {
        var f = new RecoveryAuditFixture(); f.archivePrefix(); var proof = (ObjectNode)f.prefix.getFirst().proof();
        proof.put("localDeletionAllowed", true); f.resignPrefix(proof, "acrylic-pda-audit-archive");

        assertThatThrownBy(() -> f.verifier().verify(f.bytes(), f.context())).hasMessage("RECOVERY_AUDIT_ARCHIVE_RELEASE");
    }

    @Test void refusesShortenedOriginalRetention() throws Exception {
        var f = new RecoveryAuditFixture(); f.archivePrefix(); var proof = (ObjectNode)f.prefix.getFirst().proof();
        proof.put("retainUntil", "2031-09-19T23:59:59Z"); f.resignPrefix(proof, "acrylic-pda-audit-archive");

        assertThatThrownBy(() -> f.verifier().verify(f.bytes(), f.context())).hasMessage("RECOVERY_AUDIT_ARCHIVE_POLICY");
    }

    @Test void refusesAFormatNumberWhoseTruncatedIntWouldLookSupported() throws Exception {
        var f = new RecoveryAuditFixture(); f.archivePrefix(); var proof = (ObjectNode)f.prefix.getFirst().proof();
        proof.put("archiveVersion", 4294967297L); f.resignPrefix(proof, "acrylic-pda-audit-archive");

        assertThatThrownBy(() -> f.verifier().verify(f.bytes(), f.context())).hasMessage("RECOVERY_AUDIT_ARCHIVE_POLICY");
    }

    @Test void doesNotAcceptTheUploadedSigningKeyInsteadOfTheExplicitTrustedKey() throws Exception {
        var f = new RecoveryAuditFixture(); f.archivePrefix(); var another = new RecoveryReceiptFixture();
        var verifier = new RecoverySourceAudit(new RecoveryReceiptKeys(java.util.List.of(another.pair.getPublic().getEncoded())));

        assertThatThrownBy(() -> verifier.verify(f.bytes(), f.context())).hasMessage("RECOVERY_RECEIPT_KEY_UNKNOWN");
    }
}
