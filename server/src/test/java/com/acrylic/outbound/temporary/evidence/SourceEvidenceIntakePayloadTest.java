package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.handover.TemporaryHandoverEvidenceSource;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.continuation.TemporaryRoundEvidenceSource;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SourceEvidenceIntakePayloadTest {
    private final UUID device=UUID.randomUUID(),submission=UUID.randomUUID();
    private final TemporaryHandoverEvidenceSource handover=mock(TemporaryHandoverEvidenceSource.class);
    private final TemporaryRoundEvidenceSource continuation=mock(TemporaryRoundEvidenceSource.class);
    private final SourceEvidenceIntakePayload payloads=new SourceEvidenceIntakePayload(handover,continuation);

    @Test void mixedContinuationStopContainsOnlyTheCurrentRoundsSuffix() {
        var prefix=prefix();var body=next(prefix);
        when(continuation.predecessor(submission,body)).thenReturn(prefix);
        var original=body.deepCopy();

        var checked=payloads.require(binding(),body);

        assertThat(checked.orderHigh()).isEqualTo(3);
        for(String field:java.util.List.of("captures","scans","events"))
            assertThat(checked.own().path(field)).isEqualTo(SubmissionJson.object().putArray("value").add(body.path(field).get(2)));
        assertThat(body).isEqualTo(original);
        verifyNoInteractions(handover);
    }

    @Test void prefixReadFailureCannotFallBackToTrustingForeignCaptures() {
        var body=next(prefix());
        when(continuation.predecessor(submission,body)).thenThrow(new BusinessException(409,"UNAVAILABLE","missing original"));

        assertThatThrownBy(()->payloads.require(binding(),body)).isInstanceOfSatisfying(BusinessException.class,
            error->assertThat(error.code()).isEqualTo("UNAVAILABLE"));
    }

    @Test void cannotRewriteAnOlderCaptureFromTheSameCurrentDevice() {
        var prefix=prefix();var body=next(prefix);
        ((ObjectNode)body.path("captures").get(1)).put("deviceCaptureSeq","99");
        when(continuation.predecessor(submission,body)).thenReturn(prefix);

        assertCode("E2A_ORDER_SCAN_PREFIX_CONFLICT",body);
    }

    @Test void cannotAddAForeignCaptureAfterTheTrustedMixedPrefix() {
        var prefix=prefix();var body=next(prefix);
        ((ObjectNode)body.path("captures").get(2)).put("originDeviceId",UUID.randomUUID().toString());
        when(continuation.predecessor(submission,body)).thenReturn(prefix);

        assertCode("E2A_ORDER_SCAN_SOURCE_CONFLICT",body);
    }

    @Test void initialPayloadCannotClaimMixedHistoryWithoutAPredecessor() {
        var body=next(prefix());body.remove("temporaryContinuation");

        assertCode("E2A_ORDER_SCAN_SOURCE_CONFLICT",body);
        verifyNoInteractions(handover,continuation);
    }

    private void assertCode(String code,ObjectNode body) {
        assertThatThrownBy(()->payloads.require(binding(),body)).isInstanceOfSatisfying(BusinessException.class,
            error->assertThat(error.code()).isEqualTo(code));
    }

    private SourceEvidenceIntakePageObjects.Binding binding() {
        return new SourceEvidenceIntakePageObjects.Binding(UUID.randomUUID(),UUID.randomUUID(),submission,3,device,
            UUID.randomUUID(),UUID.randomUUID());
    }

    private ObjectNode prefix() {
        var body=SubmissionJson.object();
        for(String field:java.util.List.of("captures","scans","events"))body.putArray(field);
        append(body,1,UUID.randomUUID());append(body,2,device);return body;
    }

    private ObjectNode next(ObjectNode prefix) {
        var body=prefix.deepCopy();body.putObject("temporaryContinuation");append(body,3,device);return body;
    }

    private void append(ObjectNode body,long sequence,UUID origin) {
        String id=UUID.randomUUID().toString();
        body.withArray("captures").addObject().put("id",id).put("originDeviceId",origin.toString())
            .put("orderScanSeq",Long.toString(sequence)).put("deviceCaptureSeq",sequence==3?"2":"1");
        body.withArray("scans").addObject().put("captureId",id).put("orderScanSeq",Long.toString(sequence));
        body.withArray("events").addObject().put("id",UUID.randomUUID().toString());
    }
}
