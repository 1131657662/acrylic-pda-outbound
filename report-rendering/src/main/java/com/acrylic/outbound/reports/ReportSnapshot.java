package com.acrylic.outbound.reports;

import java.time.*;
import java.util.List;

/** Module-local immutable input; deliberately not an HTTP or database contract. */
public record ReportSnapshot(Metadata metadata, List<OrderSnapshot> orders) {
    public ReportSnapshot { orders = List.copyOf(orders); }
    public static final String TEMPLATE = "r08-general-2";
    public enum Basis { ORIGINAL_CONFIRMED, CURRENT_EFFECTIVE }
    public enum Provenance { TEST_SAMPLE, SOURCE_SNAPSHOT }
    public record Metadata(String snapshotId, String dataVersion, Instant cutoff,
                           Instant generatedAt, LocalDate fromDate, LocalDate throughDate,
                           Basis basis, Provenance provenance, String sourceDescription,
                           String orderSortDescription) { }
}
