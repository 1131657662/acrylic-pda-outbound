package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.TemporaryStore;
import com.acrylic.outbound.temporary.review.TemporaryDecisionCandidates;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CommandDecisionEffectTest {
    @Test void closeReplayReportsCancelledWithoutReevaluatingCandidates() {
        var candidates = mock(TemporaryDecisionCandidates.class);
        var source = mock(TemporaryConfirmationSource.class);
        var effect = new CommandDecisionEffect(candidates, mock(com.acrylic.outbound.submissions.SubmissionFiles.class),
            mock(TemporaryStore.class), source);
        ObjectNode receipt = SubmissionJson.object();
        receipt.putObject("businessDecision").put("kind", "CLOSE_DISPOSITION");
        var reasons = new ArrayList<String>();
        var result = SubmissionJson.object();
        effect.observations(new CommandContext(SubmissionJson.object()), receipt, reasons);
        effect.complete(receipt, result, reasons);
        assertThat(result.path("evaluation").asText()).isEqualTo("CANCELLED");
        assertThat(reasons).contains("SOURCE_CLOSED", "ORIGINAL_CLOSE_COMMITTED");
        org.mockito.Mockito.verifyNoInteractions(candidates, source);
    }
}
