package com.acrylic.outbound.relay;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.inventory.ImportFiles;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RelayPhysicalPolicyTest {
    @Test void completeLoadedObservationRemainsEvidenceOnly() {
        var f=new RelayPhysicalFixture();
        assertThatCode(()->{RelayPhysicalPolicy.validate(f.request);RelayPhysicalPolicy.complete(f.request,f.context,f.supervisor,f.NOW);})
            .doesNotThrowAnyException();
    }
    @ParameterizedTest @ValueSource(strings={"UNLOADED","NOT_FOUND"})
    void exceptionalPhysicalObservationNeedsExplanation(String status) {
        var f=new RelayPhysicalFixture();f.observation().put("status",status);
        assertThatThrownBy(()->RelayPhysicalPolicy.validate(f.request)).isInstanceOf(BusinessException.class);
        f.observation().put("note","合成异常说明");assertThatCode(()->RelayPhysicalPolicy.validate(f.request)).doesNotThrowAnyException();
    }
    @Test void originalCollectorCannotCertifyOwnTask() {
        var f=new RelayPhysicalFixture();
        assertThatThrownBy(()->RelayPhysicalPolicy.complete(f.request,f.context,f.collector,f.NOW))
            .isInstanceOfSatisfying(BusinessException.class,error->assertThat(error.code()).isEqualTo("INDEPENDENT_PHYSICAL_SELF_CHECK"));
    }
    @Test void changedFullContextRequiresAnotherReview() {
        var f=new RelayPhysicalFixture();f.context.put("hiddenCandidateVersion","2");
        assertThatThrownBy(()->RelayPhysicalPolicy.complete(f.request,f.context,f.supervisor,f.NOW))
            .isInstanceOfSatisfying(BusinessException.class,error->assertThat(error.code()).isEqualTo("INDEPENDENT_REVIEW_CONTEXT_CHANGED"));
    }
    @Test void observationsMustCoverExactlyAllActiveCaptures() {
        var f=new RelayPhysicalFixture();f.observation().put("captureId",UUID.randomUUID().toString());
        assertThatThrownBy(()->RelayPhysicalPolicy.complete(f.request,f.context,f.supervisor,f.NOW)).isInstanceOf(BusinessException.class);
        ((ArrayNode)f.request.path("packages")).removeAll();
        assertThatThrownBy(()->RelayPhysicalPolicy.complete(f.request,f.context,f.supervisor,f.NOW)).isInstanceOf(BusinessException.class);
    }
    @Test void duplicatesAndExtraFieldsCannotBeSilentlyDiscarded() {
        var f=new RelayPhysicalFixture();((ArrayNode)f.request.path("packages")).add(f.observation().deepCopy());
        assertThatThrownBy(()->RelayPhysicalPolicy.validate(f.request)).isInstanceOf(BusinessException.class);
        var extra=new RelayPhysicalFixture();extra.request.put("stockAvailable",true);
        assertThatThrownBy(()->RelayPhysicalPolicy.validate(extra.request)).isInstanceOf(BusinessException.class);
    }
    @ParameterizedTest @ValueSource(strings={"2026-10-13T05:59:59Z","2026-10-14T06:02:01Z","unknown"})
    void checkTimeMustBeRecentAndParsable(String time) {
        var f=new RelayPhysicalFixture();f.request.put("checkedAt",time);
        assertThatThrownBy(()->RelayPhysicalPolicy.complete(f.request,f.context,f.supervisor,f.NOW)).isInstanceOf(BusinessException.class);
    }
    @Test void attachmentBytesCannotTrustDeclaredDigestOrLength() {
        byte[] bytes={1,2,3};
        assertThatCode(()->RelayPhysicalPolicy.bytes(bytes,ImportFiles.sha256(bytes))).doesNotThrowAnyException();
        assertThatThrownBy(()->RelayPhysicalPolicy.bytes(bytes,"a".repeat(64))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->RelayPhysicalPolicy.bytes(new byte[0],ImportFiles.sha256(new byte[0]))).isInstanceOf(BusinessException.class);
        byte[] large=new byte[RelayPhysicalPolicy.MAX_BYTES+1];
        assertThatThrownBy(()->RelayPhysicalPolicy.bytes(large,ImportFiles.sha256(large))).isInstanceOf(BusinessException.class);
    }
    @ParameterizedTest @ValueSource(strings={"0","10485761","99999999","1.0","-1"})
    void attachmentReferenceCapacityIsBounded(String size) {
        var f=new RelayPhysicalFixture();f.attachment().put("sizeBytes",size);
        assertThatThrownBy(()->RelayPhysicalPolicy.validate(f.request)).isInstanceOf(BusinessException.class);
    }
    @Test void noMoreThanFiveDistinctAttachments() {
        var f=new RelayPhysicalFixture();var rows=(ArrayNode)f.request.path("attachments");
        for(int i=0;i<5;i++)rows.add(f.attachment().deepCopy().put("attachmentId",UUID.randomUUID().toString()));
        assertThatThrownBy(()->RelayPhysicalPolicy.validate(f.request)).isInstanceOf(BusinessException.class);
    }
}
