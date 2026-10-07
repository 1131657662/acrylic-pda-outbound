package com.acrylic.outbound.temporary.continuation;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.temporary.command.TemporaryRetainedManifest;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

class TemporaryRoundEvidenceTest {
    @Test void validatesOldAndNewWindowsWithoutRewritingAnyBytes() {
        var f=new TemporaryRoundEvidenceFixture();String original=canonical(f.original),next=canonical(f.body);
        f.validate();
        assertThat(canonical(f.original)).isEqualTo(original);assertThat(canonical(f.body)).isEqualTo(next);
        assertThat(f.oldAuthorization.expiresAt()).isBefore(f.newAuthorization.issuedAt());
    }
    @Test void initialEvidenceStillValidatesWithOneAuthorizationOnly() {
        var f=new TemporaryRoundEvidenceFixture();var input=f.input();
        f.ordinary.validate(input.previous(),input.originalPlan(),f.original);
        assertThatThrownBy(()->f.ordinary.validate(input.current(),input.plan(),f.body)).isInstanceOf(BusinessException.class);
    }
    @Test void rejectsConsistentOldIdentityOnANewFreezeEvent() {
        var f=new TemporaryRoundEvidenceFixture();var last=row(f,"events",10);
        last.put("authorizationId",f.oldAuthorization.id().toString()).set("source",f.original.get("source"));
        assertThatThrownBy(f::validate).isInstanceOfSatisfying(BusinessException.class,
            e->assertThat(e.getMessage()).isEqualTo("审计原来源错误"));
    }
    @Test void rejectsConsistentOldQualificationOnANewCaptureEvenInsideItsOriginalWindow() {
        var f=new TemporaryRoundEvidenceFixture();var capture=row(f,"captures",2);
        capture.put("authorizationId",f.oldAuthorization.id().toString()).put("authorizationEpoch",f.oldAuthorization.authorizationEpoch())
            .put("clientOrderId",f.oldClient.toString()).put("confirmedAt","2026-10-05T00:00:30Z");
        row(f,"scans",1).put("savedAt","2026-10-05T00:01:00Z");
        for(int i=7;i<11;i++)row(f,"events",i).put("authorizationId",f.oldAuthorization.id().toString()).set("source",f.original.get("source"));
        assertThatThrownBy(f::validate).isInstanceOfSatisfying(BusinessException.class,
            e->assertThat(e.getMessage()).isEqualTo("输入原临时单错误"));
    }
    @ParameterizedTest @ValueSource(strings={"captures","scans","events"})
    void rejectsMissingOrReorderedOriginalPrefix(String key) {
        var f=new TemporaryRoundEvidenceFixture();array(f.body,key).remove(0);
        assertThatThrownBy(f::validate).isInstanceOf(BusinessException.class);
    }
    @ParameterizedTest @MethodSource("mutations")
    void rejectsNewRoundAttemptsToChangeOldFactsOrBorrowOldAuthority(Consumer<TemporaryRoundEvidenceFixture> change) {
        var f=new TemporaryRoundEvidenceFixture();change.accept(f);
        assertThatThrownBy(f::validate).isInstanceOf(BusinessException.class);
    }
    static Stream<Consumer<TemporaryRoundEvidenceFixture>> mutations() {
        return Stream.of(
            f->row(f,"captures",0).put("authorizationId",f.newAuthorization.id().toString()),
            f->row(f,"scans",0).put("savedAt","2026-10-06T00:01:00Z"),
            f->row(f,"captures",2).put("authorizationId",f.oldAuthorization.id().toString()),
            f->row(f,"captures",2).put("clientOrderId",f.oldClient.toString()),
            f->row(f,"captures",2).put("orderScanSeq","4"),
            f->row(f,"captures",2).put("deviceCaptureSeq","2"),
            f->row(f,"scans",1).put("captureId",f.oldCapture.toString()),
            f->row(f,"events",7).put("captureId",f.oldCapture.toString()).put("type","VOID"),
            f->row(f,"events",7).put("authorizationId",f.oldAuthorization.id().toString()),
            f->row(f,"events",7).set("source",f.original.path("source")),
            f->row(f,"events",7).put("localSeq","7"),
            f->row(f,"scans",1).put("savedAt","2026-10-05T00:01:00Z"),
            f->f.body.put("policyVersion","2"),f->f.body.put("autoDeductInventory",false),
            f->f.body.put("captureHighWatermark","2"),f->f.body.put("expectedCount","1"),
            f->{ ((ObjectNode)f.body.get("temporaryOrder")).put("vehicleNo","changed");f.body.put("planSha256",sha(f.body.get("temporaryOrder"))); });
    }
    @ParameterizedTest @ValueSource(strings={"digest","empty","duplicate"})
    void rejectsMismatchedRetainedSetDespiteUnchangedOldPrefix(String kind) {
        var f=new TemporaryRoundEvidenceFixture();var input=f.input();var old=input.retained().getFirst();
        var retained=switch(kind) {
            case "empty" -> List.<TemporaryRetainedManifest.Expected>of();
            case "duplicate" -> List.of(old,old);
            default -> List.of(new TemporaryRetainedManifest.Expected(old.baleId(),old.cycle(),"a".repeat(64)));
        };
        var changed=new TemporaryRoundEvidence.Input(input.current(),input.previous(),input.plan(),input.originalPlan(),
            input.original(),input.source(),retained);
        assertThatThrownBy(()->f.validator.validate(changed,f.body)).isInstanceOfSatisfying(BusinessException.class,
            e->assertThat(e.code()).isEqualTo("TEMP_CONTINUATION_RETAINED_SET_MISMATCH"));
    }
    @Test void refusesATicketThatUsesSavedCountInsteadOfOriginalCaptureSequence() {
        var f=new TemporaryRoundEvidenceFixture();var input=f.input();var s=input.source();
        var wrong=new TemporaryRoundFacts.Source(s.scope(),s.warehouse(),s.previous(),s.previousAuthorization(),s.fromRound(),s.toRound(),
            s.epoch(),s.evidenceSet(),2,s.retainedHash(),s.status(),s.authorization(),s.client(),s.device(),s.operator());
        var changed=new TemporaryRoundEvidence.Input(input.current(),input.previous(),input.plan(),input.originalPlan(),
            input.original(),wrong,input.retained());
        assertThatThrownBy(()->f.validator.validate(changed,f.body)).isInstanceOfSatisfying(BusinessException.class,
            e->assertThat(e.code()).isEqualTo("CONTINUATION_HIGH_WATERMARK_CHANGED"));
    }
    @Test void supportsOnlyTheRetainedSetWithANewFrozenEvent() {
        var f=new TemporaryRoundEvidenceFixture();
        array(f.body,"captures").remove(2);array(f.body,"scans").remove(1);
        var finalEvent=array(f.body,"events").get(10).deepCopy();
        while(array(f.body,"events").size()>7)array(f.body,"events").remove(7);
        array(f.body,"events").add(finalEvent);
        f.body.put("captureHighWatermark","2").put("expectedCount","1");
        f.body.set("captureSummary",f.original.get("captureSummary").deepCopy());f.validate();
    }
    private static ObjectNode row(TemporaryRoundEvidenceFixture f,String key,int index) {
        return (ObjectNode)f.body.path(key).get(index);
    }
}
