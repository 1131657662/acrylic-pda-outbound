package com.acrylic.outbound.reports;

import java.time.Instant;
import java.util.List;

public record OrderSnapshot(String id, String number, long revision, long resultVersion,
        String source, Header originalHeader, Header effectiveHeader, Instant confirmedAt,
        SequenceProof sequenceProof, List<Product> products, List<ReportLine> originalLines,
        List<EffectiveLine> effectiveLines, List<Adjustment> adjustments, List<ExceptionRow> exceptions) {
    public OrderSnapshot {
        products = List.copyOf(products);
        originalLines = List.copyOf(originalLines);
        effectiveLines = List.copyOf(effectiveLines);
        adjustments = List.copyOf(adjustments);
        exceptions = List.copyOf(exceptions);
    }
    public record Header(String vehicle, String container, String remark) { }
    public record Product(String code, String name, String model, long plannedCount) { }
    public enum SequenceKind { PERSISTED_ORDER_SEQUENCE, VERIFIED_HANDOVER }
    public record SequenceProof(SequenceKind kind, String reference) { }
    /** Includes zero-contribution rows so omissions cannot masquerade as returns. */
    public record EffectiveLine(String dispatchId, int count, String weightGrams) { }
    public record ExceptionRow(String eventId, Long orderScanSeq, String originDeviceId,
            String kind, String barcode, String detail, String occurredAt) { }
}
