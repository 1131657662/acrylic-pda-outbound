package com.acrylic.outbound.temporary.evidence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class SourceEvidenceIntakeCaptureCoverage {
    private SourceEvidenceIntakeCaptureCoverage() { }

    static boolean complete(List<Long> sequences, long highWatermark) {
        if (sequences == null || highWatermark < 0 || sequences.size() != highWatermark)
            return false;
        var ordered = new ArrayList<>(sequences);
        ordered.sort(Comparator.naturalOrder());
        long expected = 1;
        for (long sequence : ordered) {
            if (sequence != expected) return false;
            expected++;
        }
        return expected == highWatermark + 1;
    }
}
