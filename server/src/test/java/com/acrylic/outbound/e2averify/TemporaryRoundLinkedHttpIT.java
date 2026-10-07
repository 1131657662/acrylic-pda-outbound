package com.acrylic.outbound.e2averify;

import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="B4_RETENTION_VERIFY",matches="true")
class TemporaryRoundLinkedHttpIT {
    @Test void reviewedFormalTargetConfirmsCumulativeRoundWithoutChangingOriginalSource() throws Exception {
        try(var f=new TemporaryRoundConfirmationHttpFixture(false)) {
            f.acknowledge();var linked=new TemporaryRoundLinkedFixture(f);var state=new TemporaryRoundConfirmationState(f);
            var link=f.command("LINK").put("reason","synthetic manual complete candidate review");
            byte[] applied=f.http.accepted(link,"round-link-applied");
            assertThat(parse(applied).at("/businessDecision/submissionReceipt/targetOrderId").asText()).isEqualTo(linked.target);
            assertThat(f.http.accepted(link,"round-link-replay")).isEqualTo(applied);
            var confirm=f.command("NORMAL_CONFIRM");assertThat(confirm.at("/expected/targetRevision").asText()).isEqualTo("2");
            linked.changedPlan(1);var before=state.snapshot();
            try { f.reject(confirm,"TEMPORARY_LINK_TARGET_PLAN_DIFFERS","linked-round-plan-changed");assertThat(state.snapshot()).isEqualTo(before); }
            finally { linked.changedPlan(-1); }
            new TemporaryRoundConfirmationRejections(f,state).verify(confirm);
            byte[] result=f.http.accepted(confirm,"linked-round-confirmed");state.completed(parse(result),4);linked.effects();
            var after=state.snapshot();assertThat(f.http.accepted(confirm,"linked-round-confirm-replay")).isEqualTo(result);
            f.reject(confirm.deepCopy().put("reason","altered"),"IDEMPOTENCY_CONTENT_MISMATCH","linked-round-confirm-altered");
            assertThat(f.http.accepted(link,"linked-round-historical-link")).isEqualTo(applied);
            assertThat(state.snapshot()).isEqualTo(after);
            Files.writeString(f.http.database.profile.attempt.resolve("test-completed.marker"),
                "Real HTTP cumulative round controlled LINK and formal confirmation, independent target revision/header, exact original source, owner transfer, SQL rollback and immutable replay. Synthetic identity/STOP.\n");
        }
    }
}
