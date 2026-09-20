package com.acrylic.outbound.reports;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AdjustmentValidationTest {
    private OrderSnapshot changed(List<OrderSnapshot.EffectiveLine> effective, List<Adjustment> changes, long version) {
        var o = Samples.order(3);
        return new OrderSnapshot(o.id(), o.number(), o.revision(), version, o.source(), o.originalHeader(), o.effectiveHeader(),
                o.confirmedAt(), o.sequenceProof(), o.products(), o.originalLines(), effective, changes, o.exceptions());
    }
    @Test void rejectsMissingEffectiveRowInsteadOfTreatingItAsReturn() {
        var o = Samples.order(3);
        var incomplete = changed(o.effectiveLines().subList(0, 2), List.of(), 1);
        assertThrows(IllegalArgumentException.class, () -> SnapshotValidator.validate(Samples.report(List.of(incomplete))));
    }
    @Test void rejectsNetWeightWithoutCorrectionEvidence() {
        var effective = new ArrayList<>(Samples.order(3).effectiveLines());
        effective.set(0, new OrderSnapshot.EffectiveLine("dispatch-1", 1, "1"));
        assertThrows(IllegalArgumentException.class, () -> SnapshotValidator.validate(Samples.report(List.of(changed(effective, List.of(), 1)))));
    }
    @Test void rejectsDuplicateEffectLines() {
        var a = Samples.adjustment("c1", 2, Adjustment.Kind.WEIGHT_CORRECTION, "dispatch-1", 1, "301502");
        var o = changed(Samples.order(3).effectiveLines(), List.of(a, a), 2);
        assertThrows(IllegalArgumentException.class, () -> SnapshotValidator.validate(Samples.report(List.of(o))));
    }
    @Test void rejectsVersionsWithMissingHistory() {
        var o = changed(Samples.order(3).effectiveLines(), List.of(), 2);
        assertThrows(IllegalArgumentException.class, () -> SnapshotValidator.validate(Samples.report(List.of(o))));
    }
    @Test void rejectsWrongAdjustmentBeforeWeight() {
        var a = Samples.adjustment("c1", 2, Adjustment.Kind.WEIGHT_CORRECTION, "dispatch-1", 1, "301502");
        var b = Samples.adjustment("c2", 3, Adjustment.Kind.PHYSICAL_RETURN, "dispatch-1", 0, "0");
        var o = changed(Samples.order(3).effectiveLines(), List.of(a, b), 3);
        assertThrows(IllegalArgumentException.class, () -> SnapshotValidator.validate(Samples.report(List.of(o))));
    }
    @Test void rejectsOriginalBasisForAdjustedSnapshot() {
        var s = Samples.report(List.of(Samples.corrected())); var m = s.metadata();
        var original = new ReportSnapshot.Metadata(m.snapshotId(), m.dataVersion(), m.cutoff(), m.generatedAt(), m.fromDate(), m.throughDate(),
                ReportSnapshot.Basis.ORIGINAL_CONFIRMED, m.provenance(), m.sourceDescription(), m.orderSortDescription());
        assertThrows(IllegalArgumentException.class, () -> SnapshotValidator.validate(new ReportSnapshot(original, s.orders())));
    }
    @Test void rejectsPeriodThatOnlyContainsAdjustmentDay() {
        var s = Samples.report(List.of(Samples.corrected())); var m = s.metadata();
        var period = new ReportSnapshot.Metadata(m.snapshotId(), m.dataVersion(), m.cutoff(), m.generatedAt(), m.fromDate().plusDays(1), m.throughDate().plusDays(2),
                m.basis(), m.provenance(), m.sourceDescription(), m.orderSortDescription());
        assertThrows(IllegalArgumentException.class, () -> SnapshotValidator.validate(new ReportSnapshot(period, s.orders())));
    }
}
