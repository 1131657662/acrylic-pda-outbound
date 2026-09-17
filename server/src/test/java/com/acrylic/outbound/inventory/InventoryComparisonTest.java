package com.acrylic.outbound.inventory;

import com.acrylic.outbound.inventory.master.InventoryComparison;
import com.acrylic.outbound.inventory.parser.ParsedBale;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class InventoryComparisonTest {
    @Test void equivalentDecimalFormattingDoesNotChangeWeight() {
        var a = new ParsedBale("M", "N", "003B0000001", "003", "300.50", "300500");
        var b = new ParsedBale("M", "N", "003B0000001", "003", "300.5", "300500");
        var diff = InventoryComparison.compare(List.of(a), List.of(b));
        assertThat(diff.unchanged()).isEqualTo(1); assertThat(diff.changes()).isEmpty();
    }
    @Test void comparisonCountsAndOrderAreByBarcode() {
        var old = List.of(row("003B0000003", "M"), row("003B0000002", "M"));
        var incoming = List.of(row("003B0000001", "M"), row("003B0000002", "NEW"));
        var diff = InventoryComparison.compare(old, incoming);
        assertThat(diff.added()).isEqualTo(1); assertThat(diff.removed()).isEqualTo(1); assertThat(diff.changed()).isEqualTo(1);
        assertThat(diff.changes()).extracting(InventoryComparison.Change::type).containsExactly("ADDED", "PRODUCT_CHANGED", "UPSTREAM_MISSING");
    }
    private ParsedBale row(String barcode, String model) { return new ParsedBale(model, "N", barcode, "003", "300", "300000"); }
}
