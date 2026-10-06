package com.acrylic.outbound.e2averify;

import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="B4_RETENTION_VERIFY",matches="true")
class TemporaryRoundShortApprovalHttpIT {
    @Test void shortApprovalUsesIndependentReviewerBeforeNormalConfirmation() throws Exception {
        try(var f=new TemporaryRoundConfirmationHttpFixture(true)) {
            f.acknowledge();
            var approval=f.command("SHORT_APPROVE");
            var response=f.http.post(approval,f.http.otherToken,"confirmation-short-approval");
            assertThat(response.statusCode()).as(new String(response.body())).isEqualTo(200);
            assertThat(parse(response.body()).path("businessDecision").path("kind").asText()).isEqualTo("SHORT_APPROVE");
            var confirmation=f.command("NORMAL_CONFIRM");
            byte[] receipt=f.http.accepted(confirmation,"confirmation-short-succeeded");
            new TemporaryRoundConfirmationState(f).completed(parse(receipt),3);
            assertThat(f.http.accepted(confirmation,"confirmation-short-replay")).isEqualTo(receipt);
            Files.writeString(f.http.database.profile.attempt.resolve("test-short-completed.marker"),
                "Real HTTP independent reviewer SHORT_APPROVE followed by NORMAL_CONFIRM; synthetic stopped source.\n");
        }
    }
}
