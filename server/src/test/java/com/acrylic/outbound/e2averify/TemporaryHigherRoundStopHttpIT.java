package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.temporary.command.PhysicalDispositionVerification;
import java.nio.file.Files;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="B4_RETENTION_VERIFY",matches="true")
class TemporaryHigherRoundStopHttpIT {
    @Test void verifiedHigherRoundStopKeepsOwnersAndEnablesNextReviewedRound() throws Exception {
        try(var f=new TemporaryRoundConfirmationHttpFixture(false)) {
            f.acknowledge();var protocol=new TemporaryRoundStopHttp(f);var request=protocol.verify();
            var state=new TemporaryRoundConfirmationState(f);String before=state.snapshot();
            var invalid=request.deepCopy().put("verificationId",UUID.randomUUID().toString());
            assertThat(protocol.stop(invalid,f.http.token).statusCode()).isEqualTo(409);
            assertThat(protocol.stop(request,f.http.otherDeviceToken).statusCode()).isIn(403,404);
            assertThat(state.snapshot()).isEqualTo(before);
            rollback(f,protocol,request);
            byte[] stopped=TemporaryRoundStopHttp.ok(protocol.stop(request,f.http.token),"technical stop");
            var receipt=parse(stopped).path("data");assertThat(receipt.path("status").asText()).isEqualTo("ABORTED");
            for(String flag:List.of("localDeletionAllowed","stockReleased","mayResumeScanning"))assertThat(receipt.path(flag).asBoolean(true)).isFalse();
            assertThat(parse(TemporaryRoundStopHttp.ok(protocol.stop(request,f.http.token),"stop replay")).path("data")).isEqualTo(receipt);
            assertThat(protocol.stop(request.deepCopy().put("reason","changed"),f.http.token).statusCode()).isEqualTo(409);
            next(f,request);
            var third=new TemporaryRoundStopHttp(f,protocol.key);var thirdRequest=third.verify();
            TemporaryRoundStopHttp.ok(third.stop(thirdRequest,f.http.token),"third technical stop");next(f,thirdRequest);
            var linked=new TemporaryRoundLinkedFixture(f);f.http.accepted(f.command("LINK"),"protocol-final-link");
            f.http.accepted(f.command("NORMAL_CONFIRM"),"protocol-final-confirm");linked.effects();
            Files.writeString(f.http.database.profile.attempt.resolve("test-completed.marker"),
                "Real HTTP high-round audit/OPEN/RAW/PAGE/STOP/VERIFY/technical stop and next linked confirmation. Synthetic EC device identity and first STOP; no physical-device claim.\n");
        }
    }
    private void rollback(TemporaryRoundConfirmationHttpFixture f,TemporaryRoundStopHttp protocol,
        com.fasterxml.jackson.databind.node.ObjectNode request) throws Exception {
        var j=f.http.database.jdbc;var state=new TemporaryRoundConfirmationState(f);String before=state.snapshot();
        j.execute("CREATE TRIGGER continuation_stop_failure BEFORE INSERT ON operation_event FOR EACH ROW BEGIN "
            +"IF NEW.event_type='SUBMISSION_ABORTED' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='stop last audit failure'; END IF; END");
        try { assertThat(protocol.stop(request,f.http.token).statusCode()).isEqualTo(500);assertThat(state.snapshot()).isEqualTo(before); }
        finally { j.execute("DROP TRIGGER continuation_stop_failure"); }
    }
    private void next(TemporaryRoundConfirmationHttpFixture f,com.fasterxml.jackson.databind.JsonNode request) throws Exception {
        var db=f.http.database;var previous=f.body.deepCopy();UUID set=uuid(request,"sourceEvidenceSetId");
        db.tx.executeWithoutResult(status->{
            var proof=new PhysicalDispositionVerification(db.jdbc).readForContinuation(db.id("warehouseId"),db.id("mappedScopeId"),
                uuid(previous,"submissionId"),number(previous,"workRound"),set,uuid(request,"verificationId"),text(request,"setSha256"),1,1);
            db.apply(proof,Collections.nCopies(proof.activeBales().size(),"RETAIN"));
        });
        var commands=new ContinuationReceiptHttpCommands(f.http);long next=number(previous,"captureHighWatermark")+1;
        String label="protocol-after-"+text(previous,"workRound");
        var issued=parse(f.http.accepted(commands.request(null,uuid(previous,"submissionId"),set,next),label+"-ticket"));
        var activated=parse(f.http.accepted(commands.request(uuid(issued.path("businessDecision"),"ticketId"),uuid(previous,"submissionId"),set,next),label+"-activation"));
        var body=new TemporaryRoundIntakeBody(db).next(previous,activated.path("businessDecision"),false);
        ((com.fasterxml.jackson.databind.node.ObjectNode)array(body,"events").get(array(body,"events").size()-1))
            .put("localSeq",Long.toString(number(array(previous,"events").get(array(previous,"events").size()-1),"localSeq")+2));
        f.body.removeAll();f.body.setAll(body);f.acknowledge(activated,label+"-ack");
        TemporaryRoundStopHttp.ok(f.http.continuation(body,f.http.token,label+"-intake"),"next intake");
    }
}
