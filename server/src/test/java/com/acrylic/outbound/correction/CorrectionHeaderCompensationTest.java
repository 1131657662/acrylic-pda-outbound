package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CorrectionHeaderCompensationTest {
    private JsonNode header(String vehicle, String remark) {
        return SubmissionJson.object().put("vehicle", vehicle).put("remark", remark).put("container", "unchanged");
    }

    @Test void restoresOnlyActualChangesWhileKeepingOtherSuccessors() {
        var before = header("old", "first"); var applied = header("new", "first"); var current = header("new", "later");
        var restored = CorrectionHeaderCompensation.restore(before, applied, List.of(applied, current));
        assertThat(restored).isEqualTo(header("old", "later"));
        assertThat(current).isEqualTo(header("new", "later"));
    }

    @Test void rejectsSameFieldSuccessorEvenWhenItReturnsToApprovedValue() {
        var before = header("old", "first"); var applied = header("new", "first");
        assertThatThrownBy(() -> CorrectionHeaderCompensation.restore(before, applied,
            List.of(applied, header("intermediate", "first"), applied)))
            .isInstanceOfSatisfying(BusinessException.class, failure -> assertThat(failure.code()).isEqualTo("CORRECTION_LEDGER_CONFLICT"));
    }

    @Test void rejectsMissingOriginalContribution() {
        var before = header("old", "first");
        assertThatThrownBy(() -> CorrectionHeaderCompensation.restore(before, before, List.of(before)))
            .isInstanceOfSatisfying(BusinessException.class, failure -> assertThat(failure.code()).isEqualTo("CORRECTION_RECEIPT_INTEGRITY_FAILURE"));
    }

    @Test void restoresNullScalarWithoutOverwritingUntouchedPatchedField() {
        var before = SubmissionJson.object().putNull("vehicle").put("remark", "same");
        var applied = SubmissionJson.object().put("vehicle", "new").put("remark", "same");
        var current = applied.deepCopy().put("remark", "later");
        var restored = CorrectionHeaderCompensation.restore(before, applied, List.of(applied, current));
        assertThat(restored.path("vehicle").isNull()).isTrue(); assertThat(restored.path("remark").asText()).isEqualTo("later");
    }

    @Test void proofBindsOriginalAndCurrentHeadersAndActualFields() {
        var before = header("old", "same"); var applied = header("new", "same"); var current = header("new", "later");
        var restored = CorrectionHeaderCompensation.restore(before, applied, List.of(applied, current));
        var proof = CorrectionHeaderCompensation.proof(new CorrectionHeaderCompensation.ProofInput("create", "approve", 1, 2, 3,
            before, applied, current, restored));
        assertThat(proof.path("restoredFields")).containsExactly(SubmissionJson.parse("\"vehicle\""));
        assertThat(proof.path("compensatedHeaderSha256").asText()).isEqualTo(CorrectionHeaderCompensation.hash(restored));
        assertThat(proof.path("previousHeaderSha256").asText()).isNotEqualTo(proof.path("effectiveHeaderSha256").asText());
    }
}
