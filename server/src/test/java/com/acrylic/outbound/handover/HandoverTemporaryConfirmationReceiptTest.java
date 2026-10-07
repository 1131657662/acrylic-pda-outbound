package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.BusinessException;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

class HandoverTemporaryConfirmationReceiptTest {
    @TempDir Path directory;

    @Test void unchangedIntentReturnsSameSignedBytesAcrossTrustedKeyRotation() throws Exception {
        var f = new HandoverTemporaryConfirmationFixture(directory); var row = f.stored();
        assertThat(f.receipts.replay(row, f.parsed(f.command), f.actor)).isEqualTo((byte[]) row.get("receipt_bytes"));
        var rotated = HandoverTemporaryCredentialFixture.signer(directory, "rotated", directory.resolve("confirmation-public.der").toString());
        var reader = new HandoverTemporaryConfirmationReceipt(new HandoverStopSignature(rotated));
        assertThat(reader.replay(row, f.parsed(f.command), f.actor)).isEqualTo((byte[]) row.get("receipt_bytes"));
    }

    @Test void sameKeyWithChangedIntentDoesNotReplaceOriginalReceipt() throws Exception {
        var f = new HandoverTemporaryConfirmationFixture(directory); var row = f.stored();
        var original = ((byte[]) row.get("receipt_bytes")).clone();
        expect("IDEMPOTENCY_CONTENT_MISMATCH", () -> f.receipts.replay(row,
            f.parsed(f.command.deepCopy().put("expectedCaseVersion", "5")), f.actor));
        assertThat((byte[]) row.get("receipt_bytes")).isEqualTo(original);
    }

    @Test void mysqlDatetimeUsesUtcWithoutReplacingOriginalBytes() throws Exception {
        var f = new HandoverTemporaryConfirmationFixture(directory); var row = f.stored();
        row.put("recorded_at", ((java.sql.Timestamp) row.get("recorded_at")).toInstant().atOffset(java.time.ZoneOffset.UTC).toLocalDateTime());
        assertThat(f.receipts.replay(row, f.parsed(f.command), f.actor)).isEqualTo((byte[]) row.get("receipt_bytes"));
    }

    @ParameterizedTest @ValueSource(strings = {"id", "case_id", "command_id", "authorization_id", "command_sha256", "receipt_sha256", "recorded_at"})
    void alteredStorageBindingCannotReplay(String field) throws Exception {
        var f = new HandoverTemporaryConfirmationFixture(directory); var row = f.stored(); row.put(field, "changed");
        expect("HANDOVER_RECEIPT_INTEGRITY_FAILURE", () -> f.receipts.replay(row, f.parsed(f.command), f.actor));
    }

    @Test void malformedBytesAndUnsignedCapabilitiesAreEvidenceFailure() throws Exception {
        var f = new HandoverTemporaryConfirmationFixture(directory); var row = f.stored();
        row.put("command_bytes", new byte[] {0}); row.put("command_sha256", HandoverCommand.sha(new byte[] {0}));
        expect("HANDOVER_RECEIPT_INTEGRITY_FAILURE", () -> f.receipts.replay(row, f.parsed(f.command), f.actor));
        var intact = f.stored(); var receipt = (com.fasterxml.jackson.databind.node.ObjectNode) parse((byte[]) intact.get("receipt_bytes"));
        receipt.withObject("capabilities").put("localDeletionAllowed", true);
        byte[] tampered = canonical(receipt).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        intact.put("receipt_bytes", tampered); intact.put("receipt_sha256", HandoverCommand.sha(tampered));
        expect("HANDOVER_RECEIPT_INTEGRITY_FAILURE", () -> f.receipts.replay(intact, f.parsed(f.command), f.actor));
    }

    @Test void anotherRecipientCannotReadOriginalReceipt() throws Exception {
        var f = new HandoverTemporaryConfirmationFixture(directory); var row = f.stored(); row.put("actor_id", UUID.randomUUID().toString());
        expect("HANDOVER_CASE_NOT_FOUND", () -> f.receipts.replay(row, f.parsed(f.command), f.actor));
    }

    private void expect(String code, org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(BusinessException.class,
            failure -> assertThat(failure.code()).isEqualTo(code));
    }
}
