package com.acrylic.outbound.e2averify;

import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

/** A reviewed mapping never exempts the next round from plan, slot or transaction checks. */
final class TemporaryLinkedContinuationChecks {
    private final TemporaryRoundConfirmationHttpFixture f;
    private final TemporaryRoundLinkedFixture linked;
    TemporaryLinkedContinuationChecks(TemporaryRoundConfirmationHttpFixture f,TemporaryRoundLinkedFixture linked) {
        this.f=f;this.linked=linked;
    }
    void beforeIntake() throws Exception {
        var state=new TemporaryRoundConfirmationState(f);String before=state.snapshot();
        linked.changedPlan(1);
        try {
            var response=f.http.continuation(f.body,f.http.token,"linked-next-plan-changed");
            assertThat(response.statusCode()).as(new String(response.body())).isEqualTo(409);
            assertThat(parse(response.body()).path("code").asText()).isEqualTo("TEMPORARY_LINK_TARGET_PLAN_DIFFERS");
        } finally { linked.changedPlan(-1); }
        assertThat(state.snapshot()).isEqualTo(before);
        finalRollback(state,before);
        new TemporaryPendingContinuationChecks(f).resume();
    }
    private void finalRollback(TemporaryRoundConfirmationState state,String before) throws Exception {
        var j=f.http.database.jdbc;
        j.execute("CREATE TRIGGER linked_next_failure BEFORE INSERT ON operation_event FOR EACH ROW BEGIN "
            +"IF NEW.event_type='SUBMISSION_REVIEW_REQUIRED' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='linked next last write'; END IF; END");
        try {
            var response=f.http.continuation(f.body,f.http.token,"linked-next-last-write");
            assertThat(response.statusCode()).isEqualTo(500);assertThat(state.snapshot()).isEqualTo(before);
            assertThat(j.queryForObject("SELECT COUNT(*) FROM submission WHERE id=UUID_TO_BIN(?)",Integer.class,text(f.body,"submissionId"))).isZero();
        } finally { j.execute("DROP TRIGGER linked_next_failure"); }
    }
}
