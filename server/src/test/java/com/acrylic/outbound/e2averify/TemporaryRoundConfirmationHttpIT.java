package com.acrylic.outbound.e2averify;

import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="B4_RETENTION_VERIFY",matches="true")
class TemporaryRoundConfirmationHttpIT {
    @Test void actualConfirmationSettlesCumulativeRoundAndCompletesTicketAtomically() throws Exception {
        try(var f=new TemporaryRoundConfirmationHttpFixture(false)) {
            var state=new TemporaryRoundConfirmationState(f);var command=f.command("NORMAL_CONFIRM");var before=state.snapshot();
            f.reject(command,"TEMPORARY_ACTIVATION_UNACKNOWLEDGED","confirmation-no-ack");assertThat(state.snapshot()).isEqualTo(before);
            f.acknowledge();new TemporaryRoundConfirmationRejections(f,state).verify(command);
            byte[] receipt=f.http.accepted(command,"confirmation-succeeded");state.completed(parse(receipt),4);
            var history=RetentionDatabaseFixture.historical(f.http.database.jdbc);
            var after=state.snapshot();assertThat(f.http.accepted(command,"confirmation-replay")).isEqualTo(receipt);
            f.reject(command.deepCopy().put("reason","changed"),"IDEMPOTENCY_CONTENT_MISMATCH","confirmation-different-text");
            f.reject(command,f.http.otherToken,"COMMAND_CONTEXT_CHANGED","confirmation-cross-person");
            assertThat(f.http.accepted(f.reopen,"confirmation-old-ticket")).isEqualTo(f.issued);
            assertThat(f.http.accepted(f.activate,"confirmation-old-activation")).isEqualTo(f.activated);
            assertThat(state.snapshot()).isEqualTo(after);
            assertThat(RetentionDatabaseFixture.historical(f.http.database.jdbc)).isEqualTo(history);
            Files.writeString(f.http.database.profile.attempt.resolve("test-completed.marker"),
                "Real HTTP acknowledgement and normal confirmation; actual dispatch/result/closure, fixed non-deducting policy, rollback and historical replay. Synthetic stopped identity/source.\n");
        }
    }

}
