package com.acrylic.outbound.temporary.continuation;

import com.acrylic.outbound.handover.TemporaryHandoverControl;
import com.acrylic.outbound.submissions.*;
import com.acrylic.outbound.temporary.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

class TemporaryHandoverEvidenceTest {
    @Test void acceptsRecipientLocalCountersBelowSourceCountersWithoutChangingOriginalFacts() {
        var f=fixture();String before=canonical(f.original());validate(f);
        assertThat(canonical(f.original())).isEqualTo(before);
        assertThat(number(f.body().path("events").get(7),"localSeq")).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"eventDevice","captureDevice","localRegression","r06Borrow","formalBorrow"})
    void rejectsForeignOriginsAndCounterRegression(String mutation) {
        var f=fixture();var body=f.body();
        switch(mutation) {
            case "eventDevice" -> ((ObjectNode)body.path("events").get(7)).put("originDeviceId",UUID.randomUUID().toString());
            case "captureDevice" -> ((ObjectNode)body.path("captures").get(2)).put("originDeviceId",UUID.randomUUID().toString());
            case "localRegression" -> ((ObjectNode)body.path("events").get(8)).put("localSeq","1");
            case "r06Borrow" -> body.set("temporaryContinuation",object());
            default -> body.put("assignmentId",UUID.randomUUID().toString());
        }
        assertThatThrownBy(()->validate(f)).isInstanceOf(com.acrylic.outbound.common.BusinessException.class);
    }
    @Test void sameDeviceR06CannotResetDeviceCaptureOrEventCounters() {
        var f=new TemporaryRoundEvidenceFixture();
        ((ObjectNode)f.body.path("captures").get(2)).put("deviceCaptureSeq","1");
        assertThatThrownBy(f::validate).isInstanceOf(com.acrylic.outbound.common.BusinessException.class);
    }
    private void validate(Fixture f) {
        var control=TemporaryHandoverControl.read(f.body()).control();
        var head=new SubmissionStore.Head(control,f.authorization().userId(),f.previous().warehouse(),"REGISTERED",null,null,null);
        var plan=new TemporarySubmissionPlan.Plan(f.authorization(),object(),f.body().get("temporaryOrder"),List.of());
        var oldPlan=new TemporarySubmissionPlan.Plan(f.oldAuthorization(),object(),f.original().get("temporaryOrder"),List.of());
        var origins=TemporaryEvidenceOrigins.initial(f.previous(),oldPlan).append(head,plan,f.original());
        new TemporarySubmissionEvidence().validate(head,plan,f.body(),origins);
    }
    private Fixture fixture() {
        var f=new TemporaryRoundEvidenceFixture();var input=f.input();UUID device=UUID.randomUUID(),user=UUID.randomUUID();
        var old=f.newAuthorization;
        var a=new TemporaryStore.Stored(old.id(),old.warehouseId(),device,user,old.authorizationEpoch(),old.userVersion(),old.deviceVersion(),
            old.recoveryEpoch(),old.issuedAt(),old.expiresAt(),old.receipt());
        var body=f.body.deepCopy();body.remove("temporaryContinuation");body.put("freezeDeviceId",device.toString());
        ((ObjectNode)body.get("source")).put("creatorDeviceId",device.toString());
        ((ObjectNode)body.get("temporaryOrder")).put("creatorDeviceId",device.toString());body.put("planSha256",sha(body.get("temporaryOrder")));
        var capture=(ObjectNode)body.path("captures").get(2);
        capture.put("originDeviceId",device.toString()).put("operatorId",user.toString()).put("deviceCaptureSeq","1");
        for(int index=7;index<array(body,"events").size();index++) {
            var event=(ObjectNode)body.path("events").get(index);
            event.put("originDeviceId",device.toString()).put("operatorId",user.toString()).put("localSeq",Integer.toString(index-6));
            event.set("source",body.get("source").deepCopy());
        }
        ((ObjectNode)body.path("scans").get(1)).put("localSeq","3");
        body.putObject("temporaryHandover").put("version",1).put("ticketId",UUID.randomUUID().toString()).put("caseId",UUID.randomUUID().toString())
            .put("predecessorSubmissionId",f.oldSubmission.toString()).put("predecessorPayloadSha256",sha(f.original)).put("sourcePrefixSha256","a".repeat(64));
        return new Fixture(f.original,body,input.previous(),f.oldAuthorization,a);
    }
    private record Fixture(ObjectNode original,ObjectNode body,SubmissionStore.Head previous,TemporaryStore.Stored oldAuthorization,
        TemporaryStore.Stored authorization) { }
}
