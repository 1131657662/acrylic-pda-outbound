package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.submissions.SubmissionArchiveProofs;
import com.acrylic.outbound.submissions.SubmissionFiles;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.review.TemporaryDecisionCandidates;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CommandBusinessIntegrityTest {
    @Test void acceptsCloseReceiptWhenTheStoredReasonMatchesTheSignedDecision() {
        var jdbc = matchingJdbc("supervisor confirmed closure");

        assertThatCode(() -> integrity(jdbc).verify(receipt("supervisor confirmed closure"))).doesNotThrowAnyException();
    }

    @Test void rejectsCloseReceiptWhenTheStoredReasonDiffersFromTheSignedDecision() {
        var jdbc = matchingJdbc("stored reason");

        assertThatThrownBy(() -> integrity(jdbc).verify(receipt("signed reason")))
            .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("COMMAND_RECEIPT_INTEGRITY_FAILURE");
    }

    private static CommandBusinessIntegrity integrity(JdbcTemplate jdbc) {
        return new CommandBusinessIntegrity(jdbc, mock(SubmissionFiles.class), mock(SubmissionArchiveProofs.class),
            mock(TemporaryDecisionCandidates.class));
    }

    private static JdbcTemplate matchingJdbc(String reason) {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(startsWith("SELECT close_mode"), any(Object[].class)))
            .thenReturn(List.of(Map.of("close_mode", true, "close_reason", reason)));
        when(jdbc.queryForList(startsWith("SELECT BIN_TO_UUID(i.bale_id)"), any(Object[].class))).thenReturn(List.of());
        return jdbc;
    }

    private static ObjectNode receipt(String reason) {
        UUID scope = UUID.randomUUID(), submission = UUID.randomUUID();
        String evidence = UUID.randomUUID().toString(), verification = UUID.randomUUID().toString();
        ObjectNode receipt = SubmissionJson.object().put("commandType", "CLOSE_DISPOSITION");
        receipt.putObject("expected").put("evidenceSetId", evidence).put("verificationId", verification)
            .put("dispositionManifestSha256", "a".repeat(64)).put("dispositionManifestVersion", "1");
        receipt.putObject("originalContext").put("scopeId", scope.toString()).put("submissionId", submission.toString())
            .put("workRound", "1").putNull("canonicalOrderId");
        ObjectNode decision = receipt.putObject("businessDecision");
        decision.put("kind", "CLOSE_DISPOSITION").put("status", "CANCELLED")
            .put("scopeClosed", true).put("slotClosed", false).putArray("products");
        decision.put("dispositionId", UUID.randomUUID().toString()).put("evidenceSetId", evidence)
            .put("verificationId", verification).put("closeReason", reason).put("manifestSha256", "a".repeat(64))
            .put("manifestVersion", "1");
        return receipt;
    }
}
