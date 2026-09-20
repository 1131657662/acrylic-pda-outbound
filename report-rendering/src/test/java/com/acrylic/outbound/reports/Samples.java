package com.acrylic.outbound.reports;

import java.time.*;
import java.util.*;

final class Samples {
    static final Instant CONFIRMED = Instant.parse("2026-09-17T16:01:00Z");
    static ReportSnapshot report(List<OrderSnapshot> orders) {
        return new ReportSnapshot(new ReportSnapshot.Metadata("test-snapshot-r08", "fixture-v1",
                Instant.parse("2026-09-20T16:00:00Z"), Instant.parse("2026-09-20T16:05:00Z"),
                LocalDate.parse("2026-09-18"), LocalDate.parse("2026-09-18"), ReportSnapshot.Basis.CURRENT_EFFECTIVE,
                ReportSnapshot.Provenance.TEST_SAMPLE, "完全虚构测试样例，R06/R07字段仅验证接入提案", "测试订单成员清单顺序"), orders);
    }
    static OrderSnapshot order(int count) {
        var lines = new ArrayList<ReportLine>();
        for (int i = 0; i < count; i++) lines.add(line(i + 1));
        return new OrderSnapshot("order-1", "TEST-R08-001", 3, 1, "FORMAL_PDA",
                new OrderSnapshot.Header("0008", "003", "测试备注"), new OrderSnapshot.Header("0008", "003", "测试备注"), CONFIRMED,
                new OrderSnapshot.SequenceProof(OrderSnapshot.SequenceKind.PERSISTED_ORDER_SEQUENCE, "测试持久化订单序号样例"),
                List.of(new OrderSnapshot.Product("003", "透明亚克力", "1220×2440×3 mm", Math.max(1, count)),
                        new OrderSnapshot.Product("007", "磨砂亚克力", "1220×2440×5 mm", Math.max(1, count))), lines,
                lines.stream().map(r -> new OrderSnapshot.EffectiveLine(r.dispatchId(), 1, r.weightGrams())).toList(), List.of(), List.of());
    }
    static ReportLine line(long sequence) {
        return new ReportLine("dispatch-" + sequence, "capture-" + sequence, sequence, "device-A", sequence,
                "测试操作员", "BROADCAST", String.format(Locale.ROOT, "%011d", sequence), 1,
                sequence % 2 == 0 ? "007" : "003", "300501", "300.501", "2026-09-17T16:00:00.123Z",
                "2026-09-17T16:00:00.456Z", "DEVICE_UNVERIFIED", "");
    }
    static OrderSnapshot withLines(OrderSnapshot o, List<ReportLine> lines) {
        return new OrderSnapshot(o.id(), o.number(), o.revision(), o.resultVersion(), o.source(), o.originalHeader(),
                o.effectiveHeader(), o.confirmedAt(), o.sequenceProof(), o.products(), lines,
                lines.stream().map(r -> new OrderSnapshot.EffectiveLine(r.dispatchId(), 1, r.weightGrams())).toList(), o.adjustments(), o.exceptions());
    }
    static ReportLine changed(ReportLine r, String field, String value) {
        return new ReportLine(r.dispatchId(), r.captureId(), r.orderScanSeq(), field.equals("device") ? value : r.originDeviceId(),
                r.deviceCaptureSeq(), r.operatorName(), r.inputSource(), field.equals("barcode") ? value : r.barcode(), r.dispatchCycle(),
                r.productCode(), field.equals("weight") ? value : r.weightGrams(), r.rawWeight(), r.receivedAt(), r.savedAt(), r.timeQuality(),
                field.equals("remark") ? value : r.remark());
    }
    static OrderSnapshot corrected() {
        var o = order(3);
        var changes = List.of(
                adjustment("c1", 2, Adjustment.Kind.WEIGHT_CORRECTION, "dispatch-1", 1, "301502"),
                new Adjustment("c2", "line2", 3, Adjustment.Kind.PHYSICAL_RETURN, "dispatch-1", 1, 0, "301502", "0", "", "", "",
                        Instant.parse("2026-09-20T01:00:00Z"), "测试退货已收货", "测试审批员", "test-evidence-return"),
                new Adjustment("c3", "line3", 4, Adjustment.Kind.HEADER_CORRECTION, "", 0, 0, "0", "0", "vehicle", "0008", "0009",
                        Instant.parse("2026-09-20T02:00:00Z"), "测试车号更正", "测试审批员", "test-evidence-header"));
        return new OrderSnapshot(o.id(), o.number(), o.revision(), 4, "TEMPORARY_PROPOSAL", o.originalHeader(),
                new OrderSnapshot.Header("0009", "003", "测试备注"), o.confirmedAt(), o.sequenceProof(), o.products(), o.originalLines(),
                List.of(new OrderSnapshot.EffectiveLine("dispatch-1", 0, "0"), new OrderSnapshot.EffectiveLine("dispatch-2", 1, "300501"),
                        new OrderSnapshot.EffectiveLine("dispatch-3", 1, "300501")), changes,
                List.of(new OrderSnapshot.ExceptionRow("event-void", 4L, "device-A", "VOID", "00000000004", "测试提交前撤销，不计入原确认", "2026-09-17T16:00:01Z")));
    }
    static Adjustment adjustment(String id, long version, Adjustment.Kind kind, String dispatch, int after, String weight) {
        return new Adjustment(id, "line1", version, kind, dispatch, 1, after, "300501", weight, "", "", "",
                Instant.parse("2026-09-19T01:00:00Z"), "测试重量更正", "测试审批员", "test-evidence-weight");
    }
    private Samples() { }
}
