package com.acrylic.outbound.reports;

import java.math.BigInteger;
import java.util.*;

final class ReportTotals {
    record Total(long originalCount, BigInteger originalGrams, long netCount, BigInteger netGrams) {
        long adjustedCount() { return netCount - originalCount; }
        BigInteger adjustedGrams() { return netGrams.subtract(originalGrams); }
        Total plus(Total other) {
            return new Total(originalCount + other.originalCount, originalGrams.add(other.originalGrams),
                    netCount + other.netCount, netGrams.add(other.netGrams));
        }
    }
    static final Total ZERO = new Total(0, BigInteger.ZERO, 0, BigInteger.ZERO);
    static Map<String, OrderSnapshot.EffectiveLine> effective(OrderSnapshot order) {
        var map = new HashMap<String, OrderSnapshot.EffectiveLine>();
        order.effectiveLines().forEach(r -> map.put(r.dispatchId(), r));
        return map;
    }
    static Map<String, Total> products(OrderSnapshot order) {
        var result = new LinkedHashMap<String, Total>();
        order.products().forEach(p -> result.put(p.code(), ZERO));
        var effective = effective(order);
        for (var r : order.originalLines()) {
            var net = effective.get(r.dispatchId());
            var row = new Total(1, Values.grams(r.weightGrams()), net.count(), Values.grams(net.weightGrams()));
            result.put(r.productCode(), result.get(r.productCode()).plus(row));
        }
        return result;
    }
    static Total order(OrderSnapshot order) {
        return products(order).values().stream().reduce(ZERO, Total::plus);
    }
    static Total all(ReportSnapshot snapshot) {
        return snapshot.orders().stream().map(ReportTotals::order).reduce(ZERO, Total::plus);
    }
    private ReportTotals() { }
}
