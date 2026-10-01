package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.BusinessException;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CorrectionContributionExhaustedTest {
    @Test void exhaustedOriginalDebitCannotMasqueradeAsUndeductedShipment() {
        UUID bale = UUID.randomUUID(), id = UUID.randomUUID();
        var original = new CorrectionContribution.Entry(id, bale, null, "ORIGINAL", -1, -100, null, null, null, true);
        var reversal = new CorrectionContribution.Entry(UUID.randomUUID(), bale, UUID.randomUUID(), "REVERSAL", 1, 100,
            null, id, null, false);
        assertThatThrownBy(() -> CorrectionContribution.active(List.of(original, reversal), bale, new long[]{1, 100}))
            .isInstanceOf(BusinessException.class).extracting(error -> ((BusinessException) error).code())
            .isEqualTo("CORRECTION_LEDGER_CONFLICT");
    }
}
