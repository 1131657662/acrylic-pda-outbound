package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.BusinessException;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CorrectionContributionTest {
    private final UUID bale = UUID.randomUUID(), line = UUID.randomUUID();

    @Test void noPostingMeansNoContributionDespiteAResultCount() {
        assertThat(active(List.of(), 1, 100)).isEmpty();
    }
    @Test void originalDebitPlusWeightDifferencesMatchTheEffectiveShipment() {
        var original = root("ORIGINAL", -1, -100); var difference = root("WEIGHT_DIFF", 0, -20);
        assertThat(active(List.of(original, difference), 1, 120)).containsExactly(original, difference);
    }
    @Test void manualDebitIsARealContribution() {
        var manual = root("MANUAL_DEDUCT", -1, -100);
        assertThat(active(List.of(manual), 1, 100)).containsExactly(manual);
    }
    @Test void returnReversesEveryActualContribution() {
        var original = root("ORIGINAL", -1, -100); var difference = root("WEIGHT_DIFF", 0, -20);
        assertThat(active(List.of(original, difference, reverse(original), reverse(difference)), 0, 0)).isEmpty();
    }
    @Test void restoredReturnCanBeReturnedAndRestoredAgain() {
        var original = root("ORIGINAL", -1, -100); var first = reverse(original); var restored = restore(first);
        assertThat(active(List.of(original, first, restored), 1, 100)).containsExactly(restored);
        var second = reverse(restored); var twice = restore(second);
        assertThat(active(List.of(original, first, restored, second), 0, 0)).isEmpty();
        assertThat(active(List.of(original, first, restored, second, twice), 1, 100)).containsExactly(twice);
    }
    @Test void withdrawingWeightDifferenceLeavesTheOriginalDebit() {
        var original = root("ORIGINAL", -1, -100); var difference = root("WEIGHT_DIFF", 0, -20);
        assertThat(active(List.of(original, difference, restore(difference)), 1, 100)).containsExactly(original);
    }
    @Test void earlierWeightWithdrawalConsumesItsRestoredRepresentative() {
        var original = root("ORIGINAL", -1, -100); var difference = root("WEIGHT_DIFF", 0, -20);
        var returned = reverse(difference); var restored = restore(returned);
        var history = List.of(original, difference, returned, restored);
        assertThat(active(history, 1, 120)).containsExactly(original, restored);
        assertThat(CorrectionContribution.representative(history, difference)).isEqualTo(restored);
        var withdrawn = restore(restored, difference.line());
        assertThat(active(List.of(original, difference, returned, restored, withdrawn), 1, 100)).containsExactly(original);
    }
    @Test void restoredRepresentativeCannotBeWithdrawnByAnotherLine() {
        var original = root("ORIGINAL", -1, -100); var difference = root("WEIGHT_DIFF", 0, -20);
        var returned = reverse(difference); var restored = restore(returned);
        rejected(List.of(original, difference, returned, restored, restore(restored, UUID.randomUUID())), 1, 100);
    }
    @Test void ordinaryReversalCannotReverseAReversal() {
        var original = root("ORIGINAL", -1, -100); var reversed = reverse(original);
        rejected(List.of(original, reversed, reverse(reversed)), 1, 100);
    }
    @Test void wrongSourceBaleAndMissingSourceAndDuplicateConsumptionAreRejected() {
        var original = root("ORIGINAL", -1, -100); var reversed = reverse(original);
        rejected(List.of(reversed), 0, 0);
        rejected(List.of(original, reversed, reverse(original)), 0, 0);
        var alien = new CorrectionContribution.Entry(UUID.randomUUID(), UUID.randomUUID(), null, "ORIGINAL", -1, -100,
            null, null, null, true);
        rejected(List.of(alien), 1, 100);
    }
    @Test void incompleteWeightOrDoubleDebitIsRejected() {
        var original = root("ORIGINAL", -1, -100);
        rejected(List.of(original), 1, 120);
        rejected(List.of(original, root("MANUAL_DEDUCT", -1, -100)), 1, 100);
    }
    @Test void restoreRequiresSameLineAndExactInverse() {
        var original = root("ORIGINAL", -1, -100); var reversed = reverse(original);
        var invalid = new CorrectionContribution.Entry(UUID.randomUUID(), bale, UUID.randomUUID(), "WITHDRAWAL_RESTORE",
            -1, -100, null, null, reversed.id(), false);
        rejected(List.of(original, reversed, invalid), 1, 100);
    }

    private List<CorrectionContribution.Entry> active(List<CorrectionContribution.Entry> entries, long count, long weight) {
        return CorrectionContribution.active(entries, bale, new long[]{count, weight});
    }
    private void rejected(List<CorrectionContribution.Entry> entries, long count, long weight) {
        assertThatThrownBy(() -> active(entries, count, weight)).isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).code()).isEqualTo("CORRECTION_LEDGER_CONFLICT");
    }
    private CorrectionContribution.Entry root(String kind, long quantity, long weight) {
        return new CorrectionContribution.Entry(UUID.randomUUID(), bale, line, kind, quantity, weight, null, null, null,
            "ORIGINAL".equals(kind));
    }
    private CorrectionContribution.Entry reverse(CorrectionContribution.Entry source) {
        return new CorrectionContribution.Entry(UUID.randomUUID(), bale, UUID.randomUUID(), "REVERSAL", -source.quantity(), -source.weight(),
            source.original() ? null : source.id(), source.original() ? source.id() : null, null, false);
    }
    private CorrectionContribution.Entry restore(CorrectionContribution.Entry source) { return restore(source, source.line()); }
    private CorrectionContribution.Entry restore(CorrectionContribution.Entry source, UUID owner) {
        return new CorrectionContribution.Entry(UUID.randomUUID(), bale, owner, "WITHDRAWAL_RESTORE",
            -source.quantity(), -source.weight(), null, null, source.id(), false);
    }
}
