package com.acrylic.outbound.temporary.evidence;

import java.util.Comparator;
import java.util.List;

final class SourceEvidenceIntakeAuditCoverage {
    private SourceEvidenceIntakeAuditCoverage() { }

    static boolean complete(List<Range> ranges, long throughSeq) {
        if (throughSeq < 1 || ranges.isEmpty()) return false;
        long next = 1;
        for (var range : ranges.stream().sorted(Comparator.comparingLong(Range::first)).toList()) {
            if (range.first() != next || range.last() < next || range.last() > throughSeq) return false;
            next = range.last() + 1;
        }
        return next == throughSeq + 1;
    }

    record Range(long first, long last) { }
}
