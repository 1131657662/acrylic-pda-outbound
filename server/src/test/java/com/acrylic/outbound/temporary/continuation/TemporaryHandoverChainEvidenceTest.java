package com.acrylic.outbound.temporary.continuation;

import com.acrylic.outbound.common.BusinessException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.UUID;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

class TemporaryHandoverChainEvidenceTest {
    @Test void twoHandoffsKeepEachDeviceLocalCounterAndOriginalPrefix() {
        var f = new TemporaryHandoverChainFixture();
        var originals = java.util.List.of(canonical(f.first.body()), canonical(f.second.body()), canonical(f.third.body()));
        var origins = f.validate();
        assertThat(origins.capture(f.third.body().path("captures").get(2), 2).authorization()).isEqualTo(f.second.plan().authorization());
        assertThat(origins.capture(f.third.body().path("captures").get(3), 3).authorization()).isEqualTo(f.third.plan().authorization());
        assertThat(number(f.second.body().path("captures").get(2), "deviceCaptureSeq")).isEqualTo(1);
        assertThat(number(f.third.body().path("captures").get(3), "deviceCaptureSeq")).isEqualTo(1);
        assertThat(java.util.List.of(canonical(f.first.body()), canonical(f.second.body()), canonical(f.third.body()))).isEqualTo(originals);
    }

    @ParameterizedTest @ValueSource(strings = {"missingCapture", "missingScan", "rewrittenFirst", "rewrittenSecond", "wrongDevice", "wrongAuthorization", "wrongEpoch", "duplicateGlobalId", "voidInheritedScan"})
    void rejectsLostOrRewrittenFactsAndUnqualifiedNewInputs(String mutation) {
        var f = new TemporaryHandoverChainFixture(); var body = f.third.body();
        switch (mutation) {
            case "missingCapture" -> array(body, "captures").remove(0);
            case "missingScan" -> array(body, "scans").remove(0);
            case "rewrittenFirst" -> ((ObjectNode) body.path("captures").get(0)).put("rawInput", "changed");
            case "rewrittenSecond" -> ((ObjectNode) body.path("events").get(7)).put("detail", "changed");
            case "wrongDevice" -> ownCapture(body).put("originDeviceId", UUID.randomUUID().toString());
            case "wrongAuthorization" -> ownCapture(body).put("authorizationId", f.second.plan().authorization().id().toString());
            case "wrongEpoch" -> ownCapture(body).put("sourceRecoveryEpoch", "2");
            case "duplicateGlobalId" -> ownCapture(body).put("id", text(body.path("captures").get(0), "id"));
            case "voidInheritedScan" -> ((ObjectNode) body.path("scans").get(0)).put("status", "VOID");
            default -> throw new IllegalArgumentException(mutation);
        }
        assertThatThrownBy(f::validate).isInstanceOf(BusinessException.class);
    }

    @Test void thirdRoundCannotRevalidateItsSecondRoundUsingOnlyCurrentDeviceQualification() {
        var f = new TemporaryHandoverChainFixture();
        assertThatThrownBy(() -> new com.acrylic.outbound.temporary.TemporarySubmissionEvidence()
            .validate(f.second.head(), f.second.plan(), f.second.body())).isInstanceOf(BusinessException.class);
        assertThatCode(f::validate).doesNotThrowAnyException();
    }

    private ObjectNode ownCapture(ObjectNode body) { return (ObjectNode) body.path("captures").get(3); }
}
