package com.acrylic.outbound.reports;

import java.time.Instant;
import java.util.*;
import static com.acrylic.outbound.reports.Values.*;

final class AdjustmentValidator {
    static void validate(OrderSnapshot o, Instant cutoff) {
        var expected = new HashMap<String, OrderSnapshot.EffectiveLine>();
        o.originalLines().forEach(r -> expected.put(r.dispatchId(),
                new OrderSnapshot.EffectiveLine(r.dispatchId(), 1, r.weightGrams())));
        var headers = new HashMap<>(Map.of("vehicle", o.originalHeader().vehicle(),
                "container", o.originalHeader().container(), "remark", o.originalHeader().remark()));
        var keys = new HashSet<List<Object>>(); var effects = new HashSet<List<Object>>();
        var approved = new HashMap<List<String>,Adjustment>(); var withdrawn = new HashSet<List<String>>();
        long lastVersion = 1;
        for (var a : o.adjustments()) {
            require(a.resultVersion() >= 2 && a.resultVersion() <= o.resultVersion(), "调整版本越界");
            require(a.resultVersion() >= lastVersion && a.resultVersion() <= lastVersion + 1, "调整版本链不完整或乱序");
            lastVersion = a.resultVersion(); fields(a, cutoff, o.confirmedAt());
            require(keys.add(List.of(a.resultVersion(), a.correctionId(), a.lineId())), "调整凭据行重复");
            var key = List.of(a.correctionId(), a.lineId());
            if (a.kind() == Adjustment.Kind.CORRECTION_WITHDRAWAL) withdrawal(a, approved.get(key), withdrawn.add(key));
            else require(approved.putIfAbsent(key, a) == null, "原调整凭据跨版本重复");
            if (a.kind() == Adjustment.Kind.HEADER_CORRECTION) header(a, headers);
            else {
                require(effects.add(List.of(a.resultVersion(), a.dispatchId())), "一个结果版本重复调整同一出库行");
                effect(a, expected);
            }
        }
        require(lastVersion == o.resultVersion(), "缺少结果版本对应的调整凭据");
        require(headers.equals(Map.of("vehicle", o.effectiveHeader().vehicle(), "container",
                o.effectiveHeader().container(), "remark", o.effectiveHeader().remark())), "净抬头与调整不符");
        reconcile(o, expected);
    }
    private static void fields(Adjustment a, Instant cutoff, Instant confirmed) {
        required(a.correctionId()); required(a.lineId()); Objects.requireNonNull(a.kind());
        required(a.reason()); required(a.approver()); required(a.evidenceReference());
        require(!a.occurredAt().isAfter(cutoff) && !a.occurredAt().isBefore(confirmed), "调整时间不在确认至截点之间");
        text(a.dispatchId()); text(a.headerField()); text(a.beforeText()); text(a.afterText());
        grams(a.beforeWeightGrams()); grams(a.afterWeightGrams());
    }
    private static void effect(Adjustment a, Map<String, OrderSnapshot.EffectiveLine> expected) {
        var before = expected.get(a.dispatchId());
        require(before != null, "调整未关联原始出库行");
        require(a.headerField().isEmpty() && a.beforeText().isEmpty() && a.afterText().isEmpty(), "实物调整混入抬头字段");
        require(a.beforeCount() == before.count() && a.beforeWeightGrams().equals(before.weightGrams()), "调整前值不匹配");
        if (a.kind() == Adjustment.Kind.CORRECTION_WITHDRAWAL)
            require(a.afterCount() == 1 && grams(a.afterWeightGrams()).signum() > 0, "撤销须恢复原有效贡献");
        else if (a.kind() == Adjustment.Kind.WEIGHT_CORRECTION) {
            require(a.beforeCount() == 1, "不能再次减少已退出净结果的包");
            require(a.afterCount() == 1 && grams(a.afterWeightGrams()).signum() > 0, "重量更正不能改数量");
        } else {
            require(a.beforeCount() == 1, "不能再次减少已退出净结果的包");
            require(a.afterCount() == 0 && grams(a.afterWeightGrams()).signum() == 0, "退货/错误出库必须为零贡献");
        }
        expected.put(a.dispatchId(), new OrderSnapshot.EffectiveLine(a.dispatchId(), a.afterCount(), a.afterWeightGrams()));
    }
    private static void withdrawal(Adjustment a, Adjustment original, boolean first) {
        require(first && original != null && original.resultVersion() < a.resultVersion(), "撤销缺少原调整或重复撤销");
        require(original.kind() != Adjustment.Kind.HEADER_CORRECTION && a.dispatchId().equals(original.dispatchId()),
                "撤销未关联原实物调整");
        require(a.beforeCount() == original.afterCount() && a.afterCount() == original.beforeCount()
                && a.beforeWeightGrams().equals(original.afterWeightGrams())
                && a.afterWeightGrams().equals(original.beforeWeightGrams()), "撤销前后值未完整反向原调整");
    }
    private static void header(Adjustment a, Map<String, String> headers) {
        require(a.dispatchId().isEmpty() && a.beforeCount() == 0 && a.afterCount() == 0
                && grams(a.beforeWeightGrams()).signum() == 0 && grams(a.afterWeightGrams()).signum() == 0,
                "抬头调整不能携带包或重量效果");
        require(headers.containsKey(a.headerField()) && headers.get(a.headerField()).equals(a.beforeText()), "抬头调整前值不符");
        headers.put(a.headerField(), a.afterText());
    }
    private static void reconcile(OrderSnapshot o, Map<String, OrderSnapshot.EffectiveLine> expected) {
        require(o.effectiveLines().size() == expected.size(), "净结果必须包含每条原事实，不能把缺失当作零");
        var ids = new HashSet<String>();
        for (var line : o.effectiveLines()) {
            require(ids.add(line.dispatchId()), "净结果行重复");
            grams(line.weightGrams());
            require(line.equals(expected.get(line.dispatchId())), "净结果与原事实及调整效果不一致");
        }
    }
    private AdjustmentValidator() { }
}
