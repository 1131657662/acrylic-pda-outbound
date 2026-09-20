package com.acrylic.outbound.reports;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class SnapshotValidationTest {
    @Test void emptySelectionIsValid() { assertDoesNotThrow(() -> SnapshotValidator.validate(Samples.report(List.of()))); }
    @Test void correctedTotalsStayOnShanghaiOriginalShipmentDay() {
        var snapshot = Samples.report(List.of(Samples.corrected()));
        SnapshotValidator.validate(snapshot);
        var total = ReportTotals.all(snapshot);
        assertEquals(3, total.originalCount()); assertEquals(-1, total.adjustedCount()); assertEquals(2, total.netCount());
        assertEquals("901.503", Values.kg(total.originalGrams())); assertEquals("601.002", Values.kg(total.netGrams()));
        assertEquals("2026-09-18", Values.shipmentDay(snapshot.orders().getFirst()).toString());
    }
    @Test void rejectsUnprovenCrossDeviceSequence() {
        var o = Samples.order(2);
        var lines = List.of(o.originalLines().getFirst(), Samples.changed(o.originalLines().getLast(), "device", "device-B"));
        assertThrows(IllegalArgumentException.class, () -> SnapshotValidator.validate(Samples.report(List.of(Samples.withLines(o, lines)))));
    }
    @Test void acceptsSequenceGapsWithoutRenumbering() {
        var o = Samples.order(3);
        var snapshot = Samples.report(List.of(Samples.withLines(o, List.of(o.originalLines().getFirst(), o.originalLines().getLast()))));
        assertDoesNotThrow(() -> SnapshotValidator.validate(snapshot));
        assertEquals(3, snapshot.orders().getFirst().originalLines().getLast().orderScanSeq());
    }
    @Test void rejectsReversedOriginalRows() {
        var o = Samples.order(2);
        assertThrows(IllegalArgumentException.class, () -> SnapshotValidator.validate(Samples.report(List.of(Samples.withLines(o, o.originalLines().reversed())))));
    }
    @Test void rejectsDuplicateSequence() {
        var o = Samples.order(1);
        assertThrows(IllegalArgumentException.class, () -> SnapshotValidator.validate(Samples.report(List.of(Samples.withLines(o,
                List.of(o.originalLines().getFirst(), o.originalLines().getFirst()))))));
    }
    @ParameterizedTest @ValueSource(strings = {"1.5", "-1", "NaN", "1e3", "01", "", "1000000000000000000000000000000"})
    void rejectsNonIntegerGramInputs(String weight) {
        var o = Samples.order(1);
        var row = Samples.changed(o.originalLines().getFirst(), "weight", weight);
        assertThrows(IllegalArgumentException.class, () -> SnapshotValidator.validate(Samples.report(List.of(Samples.withLines(o, List.of(row))))));
    }
    @ParameterizedTest @ValueSource(strings = {"bad\u0000text", "bad\u001btext", "bad\ud800text"})
    void rejectsInvalidXmlCharacters(String text) {
        var o = Samples.order(1);
        var row = Samples.changed(o.originalLines().getFirst(), "remark", text);
        assertThrows(IllegalArgumentException.class, () -> SnapshotValidator.validate(Samples.report(List.of(Samples.withLines(o, List.of(row))))));
    }
    @Test void snapshotCopiesCallerLists() {
        var list = new ArrayList<>(List.of(Samples.order(1)));
        var snapshot = Samples.report(list); list.clear();
        assertEquals(1, snapshot.orders().size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.orders().clear());
    }
}
