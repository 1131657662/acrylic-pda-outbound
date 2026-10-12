package com.acrylic.outbound.reports;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeaderWithdrawalValidationTest {
    private Adjustment original() {
        return new Adjustment("doc", "", 2, Adjustment.Kind.HEADER_CORRECTION, "", 0, 0, "0", "0", "orderNo", "TEST-R08-001", "NEW",
            Samples.CONFIRMED.plusSeconds(60), "更正", "approver", "create-receipt");
    }
    private Adjustment withdrawal(String after) {
        return new Adjustment("doc", "", 3, Adjustment.Kind.CORRECTION_WITHDRAWAL, "", 0, 0, "0", "0", "orderNo", "NEW", after,
            Samples.CONFIRMED.plusSeconds(120), "撤销补偿", "maker", "withdrawal-receipt");
    }
    private ReportSnapshot snapshot(List<Adjustment> adjustments) {
        var o = Samples.order(1);
        return Samples.report(List.of(new OrderSnapshot(o.id(), o.number(), o.revision(), 3, o.source(), o.originalHeader(),
            o.effectiveHeader(), o.confirmedAt(), o.sequenceProof(), o.products(), o.originalLines(), o.effectiveLines(), adjustments, o.exceptions())));
    }

    @Test void acceptsProvenHeaderCompensationWithoutPhysicalEffects() throws Exception {
        var snapshot = snapshot(List.of(original(), withdrawal("TEST-R08-001")));
        assertDoesNotThrow(() -> SnapshotValidator.validate(snapshot));
        assertEquals("TEST-R08-001", Values.displayNumber(snapshot.orders().getFirst()));
        String html = PdfHtml.document(snapshot); assertTrue(html.contains("CORRECTION_WITHDRAWAL"));
        assertTrue(html.contains("NEW → TEST-R08-001"));
    }
    @Test void rejectsCompensationThatDoesNotRestoreOriginalField() {
        assertThrows(IllegalArgumentException.class,
            () -> SnapshotValidator.validate(snapshot(List.of(original(), withdrawal("OTHER")))));
    }
    @Test void rejectsCompensationWithoutItsOriginalAdjustment() {
        assertThrows(IllegalArgumentException.class, () -> SnapshotValidator.validate(snapshot(List.of(withdrawal("TEST-R08-001")))));
    }
}
