package com.acrylic.outbound.inventory.master;

import com.acrylic.outbound.inventory.parser.ParsedBale;
import java.util.*;

public final class InventoryComparison {
    private InventoryComparison() { }
    public static Result compare(List<ParsedBale> previous, List<ParsedBale> incoming) {
        Map<String, ParsedBale> old = new HashMap<>();
        previous.forEach(bale -> old.put(bale.barcode(), bale));
        List<Change> changes = new ArrayList<>();
        int added = 0; int changed = 0;
        for (ParsedBale next : incoming) {
            ParsedBale before = old.remove(next.barcode());
            if (before == null) { added++; changes.add(new Change(next.barcode(), "ADDED", null, next)); }
            else if (!same(before, next)) { changed++; changes.add(new Change(next.barcode(), kind(before, next), before, next)); }
        }
        old.values().forEach(bale -> changes.add(new Change(bale.barcode(), "UPSTREAM_MISSING", bale, null)));
        changes.sort(Comparator.comparing(Change::barcode));
        return new Result(added, old.size(), changed, incoming.size() - added - changed, List.copyOf(changes));
    }
    private static boolean same(ParsedBale a, ParsedBale b) {
        return a.model().equals(b.model()) && a.name().equals(b.name()) && a.weightGrams().equals(b.weightGrams());
    }
    private static String kind(ParsedBale a, ParsedBale b) {
        return !a.model().equals(b.model()) ? "PRODUCT_CHANGED" : "WEIGHT_OR_NAME_CHANGED";
    }
    public record Change(String barcode, String type, ParsedBale before, ParsedBale after) { }
    public record Result(int added, int removed, int changed, int unchanged, List<Change> changes) { }
}
