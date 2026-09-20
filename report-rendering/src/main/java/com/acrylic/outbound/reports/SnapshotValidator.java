package com.acrylic.outbound.reports;

import java.time.Instant;
import java.util.*;
import static com.acrylic.outbound.reports.Values.*;

public final class SnapshotValidator {
    public static void validate(ReportSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        var m = Objects.requireNonNull(snapshot.metadata(), "metadata");
        required(m.snapshotId()); required(m.dataVersion()); required(m.sourceDescription());
        required(m.orderSortDescription()); Objects.requireNonNull(m.basis()); Objects.requireNonNull(m.provenance());
        require(!m.fromDate().isAfter(m.throughDate()), "期间倒置");
        require(!m.cutoff().isAfter(m.generatedAt()), "截点晚于生成时间");
        require(snapshot.orders().size() <= 10000, "一次最多10000单");
        var ids = new HashSet<String>(); var dispatches = new HashSet<String>();
        var captures = new HashSet<String>(); var deviceSequences = new HashSet<List<Object>>();
        long lines = 0;
        for (var order : snapshot.orders()) {
            require(ids.add(required(order.id())), "订单重复");
            validateOrder(order, m);
            for (var line : order.originalLines()) {
                require(dispatches.add(line.dispatchId()) && captures.add(line.captureId()), "跨单事实ID重复");
                require(deviceSequences.add(List.of(line.originDeviceId(), line.deviceCaptureSeq())), "原设备接收序号重复");
            }
            lines += order.originalLines().size();
        }
        require(lines <= 2_000_000, "一次最多2000000条原始明细，请上游固定成员后分批");
    }
    private static void validateOrder(OrderSnapshot o, ReportSnapshot.Metadata m) {
        required(o.number()); required(o.source());
        require(o.revision() > 0 && o.resultVersion() > 0, "版本缺失");
        require(!o.confirmedAt().isAfter(m.cutoff()), "确认晚于截点");
        var date = shipmentDay(o);
        require(!date.isBefore(m.fromDate()) && !date.isAfter(m.throughDate()), "订单不在原出库日期间");
        header(o.originalHeader()); header(o.effectiveHeader());
        required(o.sequenceProof().reference()); Objects.requireNonNull(o.sequenceProof().kind());
        require(!o.originalLines().isEmpty(), "已确认订单不能缺少原始明细，空筛选请传空订单集合");
        var products = new HashSet<String>();
        for (var p : o.products()) {
            require(products.add(required(p.code())), "产品代码重复");
            required(p.name()); text(p.model()); require(p.plannedCount() > 0, "计划必须为正整数");
        }
        validateLines(o, products);
        o.exceptions().forEach(SnapshotValidator::exception);
        AdjustmentValidator.validate(o, m.cutoff());
        if (m.basis() == ReportSnapshot.Basis.ORIGINAL_CONFIRMED)
            require(o.resultVersion() == 1 && o.adjustments().isEmpty(), "原确认文件只能使用结果版本1");
    }
    private static void validateLines(OrderSnapshot o, Set<String> products) {
        long previous = 0;
        var devices = new HashSet<String>();
        var cycles = new HashSet<List<Object>>();
        for (var r : o.originalLines()) {
            require(r.orderScanSeq() > previous, "原始行必须按唯一持久化订单序号递增，不能猜测重排");
            previous = r.orderScanSeq(); required(r.dispatchId()); required(r.captureId());
            devices.add(required(r.originDeviceId()));
            require(r.deviceCaptureSeq() > 0 && r.dispatchCycle() > 0, "来源序号/周期无效");
            required(r.barcode()); require(r.barcode().length() <= 256, "条码超过256字符");
            require(cycles.add(List.of(r.barcode(), r.dispatchCycle())), "同一出库周期重复");
            require(products.contains(r.productCode()), "明细产品不在计划中");
            require(grams(r.weightGrams()).signum() > 0, "原始重量必须为正");
            required(r.rawWeight()); required(r.operatorName()); required(r.inputSource());
            required(r.timeQuality()); text(r.remark()); timestamp(r.receivedAt()); timestamp(r.savedAt());
        }
        require(devices.size() <= 1 || o.sequenceProof().kind() == OrderSnapshot.SequenceKind.VERIFIED_HANDOVER,
                "跨设备缺少已核验交接顺序依据");
    }
    private static void timestamp(String value) {
        text(value);
        if (!value.isEmpty()) Instant.parse(value);
    }
    private static void exception(OrderSnapshot.ExceptionRow e) {
        required(e.eventId()); required(e.kind()); required(e.originDeviceId());
        text(e.barcode()); text(e.detail()); timestamp(e.occurredAt());
        require(e.orderScanSeq() == null || e.orderScanSeq() > 0, "异常序号无效");
    }
    private static void header(OrderSnapshot.Header h) {
        text(h.vehicle()); text(h.container()); text(h.remark());
    }
    private SnapshotValidator() { }
}
