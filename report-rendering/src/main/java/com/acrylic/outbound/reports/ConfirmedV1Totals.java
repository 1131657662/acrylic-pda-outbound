package com.acrylic.outbound.reports;

import java.math.BigInteger;
import java.util.*;
import static com.acrylic.outbound.reports.Values.*;

final class ConfirmedV1Totals {
    record Total(BigInteger count, BigInteger weight) {
        static final Total ZERO = new Total(BigInteger.ZERO, BigInteger.ZERO);
        Total add(Total other) { return new Total(count.add(other.count), weight.add(other.weight)); }
    }

    static Map<String, Total> reconcile(ConfirmedV1Input.Order input) {
        var actual = new LinkedHashMap<String, Total>();
        for (var product : input.products())
            require(actual.putIfAbsent(required(product.code()), Total.ZERO) == null, "duplicate planned product");
        for (var dispatch : input.dispatches()) {
            var line = dispatch.line();
            require(actual.containsKey(line.productCode()), "dispatch product missing from fixed plan");
            actual.compute(line.productCode(), (code, total) -> total.add(new Total(BigInteger.ONE, grams(line.weightGrams()))));
        }
        matchProducts(actual, input.result().products());
        var total = actual.values().stream().reduce(Total.ZERO, Total::add);
        match(total, input.result().actualCount(), input.result().weightGrams());
        return Map.copyOf(actual);
    }

    static void matchProducts(Map<String, Total> expected, List<ConfirmedV1Input.ProductTotal> products) {
        var seen = new HashSet<String>();
        for (var product : products) {
            require(seen.add(required(product.productCode())), "duplicate confirmed product total");
            require(expected.containsKey(product.productCode()), "unknown confirmed product total");
            match(expected.get(product.productCode()), product.actualCount(), product.weightGrams());
        }
        require(seen.equals(expected.keySet()), "confirmed product totals must include every planned product, including zero");
    }

    static void match(Total expected, String count, String weight) {
        require(count != null && count.matches("0|[1-9][0-9]{0,29}"), "count must be a nonnegative integer string");
        require(expected.equals(new Total(new BigInteger(count), grams(weight))), "confirmed counts or weights disagree");
    }
    private ConfirmedV1Totals() { }
}
