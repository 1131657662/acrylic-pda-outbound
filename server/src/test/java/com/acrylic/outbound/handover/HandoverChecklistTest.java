package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class HandoverChecklistTest {
    private final HandoverChecklist subject = new HandoverChecklist();
    private final UUID bale = UUID.randomUUID(), capture = UUID.randomUUID();

    @Test void retainedSetUsesOnlyEffectiveScansAndOriginalBarcode() {
        var body = body(); var ignored = body.withArray("scans").get(0).deepCopy();
        ((ObjectNode) ignored).put("captureId", UUID.randomUUID().toString()).put("status", "VOID");
        body.withArray("scans").add(ignored);
        var row = subject.dispositions(body, lines()).path("dispositions").get(0);
        assertThat(row.path("barcode").asText()).isEqualTo("BAR-1");
        assertThat(row.path("action").asText()).isEqualTo("RETAIN");
        assertThat(row.path("expectedDispatchCycle").asText()).isEqualTo("3");
    }
    @Test void allPreviouslyDisposedOrVoidScansPermitExactEmptySet() {
        var body = body();
        body.putArray("dispositions").addObject().put("captureId", capture.toString());
        assertThat(subject.dispositions(body, SubmissionJson.object().putArray("lines")).path("dispositions").isEmpty()).isTrue();
        subject.compareStored(List.of(), SubmissionJson.object().putArray("lines"), true);
    }
    @Test void removedRowReferencesEvidenceButCannotChooseItsAction() {
        var lines = lines(); var evidence = UUID.randomUUID();
        ((ObjectNode) lines.get(0)).put("state", "REMOVED").put("approvedEvidenceId", evidence.toString());
        var row = subject.dispositions(body(), lines).path("dispositions").get(0);
        assertThat(row.path("approvedEvidenceId").asText()).isEqualTo(evidence.toString());
        assertThat(row.has("action")).isFalse();
        ((ObjectNode) lines.get(0)).put("action", "RELEASE"); invalid(lines, "HANDOVER_CHECKLIST_REQUIRED");
    }
    @Test void strictFieldsRejectMissingEvidenceAndExtraRetainedEvidence() {
        var removed = lines(); ((ObjectNode) removed.get(0)).put("state", "REMOVED");
        invalid(removed, "HANDOVER_CHECKLIST_REQUIRED");
        var retained = lines(); ((ObjectNode) retained.get(0)).put("approvedEvidenceId", UUID.randomUUID().toString());
        invalid(retained, "HANDOVER_CHECKLIST_REQUIRED");
    }
    @Test void mismatchRejectsMissingDuplicateForeignOrChangedOriginalFacts() {
        invalid(SubmissionJson.object().putArray("lines"), "HANDOVER_CHECKLIST_MISMATCH");
        var duplicate = lines(); duplicate.add(duplicate.get(0)); invalid(duplicate, "HANDOVER_CHECKLIST_MISMATCH");
        for (var field : List.of("baleId", "deliveryCode", "dispatchCycle")) {
            var changed = lines(); ((ObjectNode) changed.get(0)).put(field,
                field.equals("baleId") ? UUID.randomUUID().toString() : field.equals("dispatchCycle") ? "4" : "D2");
            invalid(changed, "HANDOVER_CHECKLIST_MISMATCH");
        }
    }
    @Test void persistedChecklistMustKeepEveryFieldAndOrder() {
        var stored = new HashMap<String, Object>(Map.of("bale_id", bale.toString(), "delivery_code", "D1",
            "dispatch_cycle", 3L, "state", "RETAINED", "item_no", 0));
        subject.compareStored(List.of(stored), lines(), true);
        for (var field : List.of("bale_id", "delivery_code", "dispatch_cycle", "state", "item_no")) {
            var changed = new HashMap<>(stored); changed.put(field, Set.of("dispatch_cycle", "item_no").contains(field) ? 99L : "changed");
            assertThatThrownBy(() -> subject.compareStored(List.of(changed), lines(), true))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("HANDOVER_CHECKLIST_MISMATCH"));
        }
        assertThatThrownBy(() -> subject.compareStored(List.of(), lines(), true)).isInstanceOf(BusinessException.class);
    }
    private void invalid(ArrayNode lines, String code) {
        assertThatThrownBy(() -> subject.dispositions(body(), lines))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo(code));
    }
    private ObjectNode body() {
        var body = SubmissionJson.object();
        body.putArray("scans").addObject().put("baleId", bale.toString()).put("captureId", capture.toString())
            .put("deliveryCode", "D1").put("barcode", "BAR-1").put("expectedDispatchCycle", "3").put("status", "ACTIVE");
        return body;
    }
    private ArrayNode lines() {
        var lines = SubmissionJson.object().putArray("lines");
        lines.addObject().put("baleId", bale.toString()).put("deliveryCode", "D1").put("state", "RETAINED").put("dispatchCycle", "3");
        return lines;
    }
}