package com.acrylic.outbound.reports;

import java.time.Instant;

/** R07 integration proposal: supplied approved result effects, never inventory ledger entries. */
public record Adjustment(String correctionId, String lineId, long resultVersion, Kind kind,
        String dispatchId, int beforeCount, int afterCount, String beforeWeightGrams,
        String afterWeightGrams, String headerField, String beforeText, String afterText,
        Instant occurredAt, String reason, String approver, String evidenceReference) {
    public enum Kind { WEIGHT_CORRECTION, PHYSICAL_RETURN, DISPATCH_CANCELLATION, HEADER_CORRECTION }
}
