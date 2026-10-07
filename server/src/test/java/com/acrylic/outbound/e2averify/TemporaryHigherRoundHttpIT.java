package com.acrylic.outbound.e2averify;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="B4_RETENTION_VERIFY",matches="true")
class TemporaryHigherRoundHttpIT {
    @Test void thirdRoundRevalidatesEveryOriginalQualificationBeforeLinkedConfirmation() throws Exception {
        try(var f=new TemporaryRoundConfirmationHttpFixture(false)) {
            f.acknowledge();var second=f.body.deepCopy();var stop=new TemporaryHigherRoundStopSeed(f);
            var commands=new ContinuationReceiptHttpCommands(f.http);
            var reopen=commands.request(null,uuid(second,"submissionId"),stop.set,6);
            f.reject(reopen.deepCopy().put("nextOrderScanSeq","5"),"CONTINUATION_HIGH_WATERMARK_CHANGED","third-wrong-high-watermark");
            byte[] issued=f.http.accepted(reopen,"third-ticket");
            var activate=commands.request(uuid(parse(issued).path("businessDecision"),"ticketId"),uuid(second,"submissionId"),stop.set,6);
            var atomicity=new TemporaryHigherRoundAtomicity(f);atomicity.activation(activate);
            byte[] activated=f.http.accepted(activate,"third-activation");
            var next=new TemporaryRoundIntakeBody(f.http.database).next(second,parse(activated).path("businessDecision"),false);
            f.body.removeAll();f.body.setAll(next);f.acknowledge(parse(activated),"third-ack");
            var forged=next.deepCopy();var last=(ObjectNode)array(forged,"events").get(array(forged,"events").size()-1);
            last.put("authorizationId",text(second,"authorizationId")).set("source",second.path("source"));
            var state=new TemporaryRoundConfirmationState(f);String before=state.snapshot();
            var rejected=f.http.continuation(forged,f.http.token,"third-borrowed-qualification");
            assertThat(rejected.statusCode()).as(new String(rejected.body())).isEqualTo(400);
            assertThat(parse(rejected.body()).path("message").asText()).isEqualTo("审计原来源错误");
            assertThat(state.snapshot()).isEqualTo(before);
            var accepted=f.http.continuation(next,f.http.token,"third-intake");
            assertThat(accepted.statusCode()).as(new String(accepted.body())).isEqualTo(200);
            assertThat(parse(accepted.body()).path("data").path("status").asText()).isEqualTo("REVIEW_REQUIRED");
            var linked=new TemporaryRoundLinkedFixture(f);var link=f.command("LINK");f.http.accepted(link,"third-link");
            var confirm=f.command("NORMAL_CONFIRM");new TemporaryRoundConfirmationRejections(f,state).verify(confirm);
            byte[] success=atomicity.confirmation(confirm);linked.effects();verify(f,second,success);
            String after=state.snapshot();assertThat(f.http.accepted(confirm,"third-confirm-replay")).isEqualTo(success);
            assertThat(f.http.accepted(activate,"third-activation-replay")).isEqualTo(activated);
            assertThat(state.snapshot()).isEqualTo(after);
            Files.writeString(f.http.database.profile.attempt.resolve("test-completed.marker"),
                "Real HTTP second/third continuation, qualification prefixes, LINK, confirmation and SQL rollback. Both STOP inputs synthetic. No Android or STOP protocol claim.\n");
        }
    }
    private void verify(TemporaryRoundConfirmationHttpFixture f,ObjectNode second,byte[] success) {
        var j=f.http.database.jdbc;var receipt=parse(success).at("/businessDecision/submissionReceipt");
        assertThat(receipt.path("workRound").asText()).isEqualTo("3");
        assertThat(receipt.path("source")).isEqualTo(f.body.path("source"));
        assertThat(receipt.path("localDeletionAllowed").asBoolean(true)).isFalse();
        assertThat(j.queryForObject("SELECT COUNT(*) FROM dispatch_record WHERE submission_id=UUID_TO_BIN(?)",Integer.class,text(f.body,"submissionId"))).isEqualTo(4);
        assertThat(j.queryForObject("SELECT status FROM submission WHERE id=UUID_TO_BIN(?)",String.class,text(second,"submissionId"))).isEqualTo("ABORTED");
        assertThat(j.queryForObject("SELECT payload_sha256 FROM submission_payload WHERE submission_id=UUID_TO_BIN(?)",String.class,text(second,"submissionId"))).isEqualTo(sha(second));
        assertThat(j.queryForObject("SELECT COUNT(*) FROM temporary_source_continuation_closure WHERE target_submission_id=UUID_TO_BIN(?)",Integer.class,text(f.body,"submissionId"))).isEqualTo(1);
    }
}
