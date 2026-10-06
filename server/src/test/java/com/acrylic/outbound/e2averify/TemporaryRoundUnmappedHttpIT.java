package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.submissions.SubmissionFiles;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="B4_RETENTION_VERIFY",matches="true")
class TemporaryRoundUnmappedHttpIT {
    @Test void retainedOnlyRoundCreatesTargetOnceFromUnmappedStoppedSource() throws Exception {
        try(var f=new ContinuationReceiptHttpFixture("66",true,true)) {
            var original=parse(f.app.getBean(SubmissionFiles.class).read(f.database.seed.get("intakePredecessorHash")));
            var commands=new ContinuationReceiptHttpCommands(f);var reopen=commands.request(null);
            var issued=f.accepted(reopen,"unmapped-ticket");var activate=commands.request(uuid(parse(issued).path("businessDecision"),"ticketId"));
            var activated=f.accepted(activate,"unmapped-activation");
            var body=new TemporaryRoundIntakeBody(f.database).next(original,parse(activated).path("businessDecision"));retainedOnly(body);
            var j=f.database.jdbc;var history=RetentionDatabaseFixture.historical(j);
            long orders=j.queryForObject("SELECT COUNT(*) FROM outbound_order",Long.class);
            var response=f.continuation(body,f.token,"unmapped-intake");assertThat(response.statusCode()).as(new String(response.body())).isEqualTo(200);
            var receipt=parse(response.body()).path("data");assertThat(receipt.path("status").asText()).isEqualTo("REVIEW_REQUIRED");
            assertThat(receipt.path("issues").findValuesAsText("code")).contains("SHORT_SHIPMENT_APPROVAL_REQUIRED","TEMPORARY_ACTIVATION_UNACKNOWLEDGED");
            assertThat(parse(f.continuation(body,f.token,"unmapped-replay").body()).path("data")).isEqualTo(receipt);
            assertThat(j.queryForObject("SELECT COUNT(*) FROM outbound_order",Long.class)).isEqualTo(orders+1);
            assertThat(j.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE submission_id=UUID_TO_BIN(?) AND state='REVIEW_RESERVED'",Integer.class,text(body,"submissionId"))).isEqualTo(3);
            assertThat(j.queryForObject("SELECT COUNT(*) FROM order_submission_slot WHERE current_submission_id=UUID_TO_BIN(?) AND work_round=2 AND phase='FROZEN'",Integer.class,text(body,"submissionId"))).isEqualTo(1);
            assertThat(j.queryForObject("SELECT COUNT(*) FROM submission_payload WHERE submission_id=UUID_TO_BIN(?)",Integer.class,text(original,"submissionId"))).isZero();
            assertThat(RetentionDatabaseFixture.historical(j)).hasSize(history.size()+1).containsAll(history);
            assertThat(f.accepted(reopen,"unmapped-ticket-replayed")).isEqualTo(issued);
            assertThat(f.accepted(activate,"unmapped-activation-replayed")).isEqualTo(activated);
            Files.writeString(f.database.profile.attempt.resolve("test-completed.marker"),"Actual retained-only HTTP intake creates one target and keeps command receipts immutable; synthetic stopped source.\n");
        }
    }
    private void retainedOnly(ObjectNode body) {
        array(body,"captures").remove(4);array(body,"scans").remove(3);
        for(int i=0;i<3;i++)array(body,"events").remove(13);
        ((ObjectNode)array(body,"events").get(13)).put("localSeq","14");
        body.put("captureHighWatermark","4").put("expectedCount","3").put("expectedWeightGrams","3000").put("shortShipmentReason","synthetic retained only");
        ((ObjectNode)body.path("captureSummary")).put("received","4").put("saved","3");
    }
}
