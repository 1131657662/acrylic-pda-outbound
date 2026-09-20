package com.acrylic.outbound.reports;

import java.util.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.acrylic.outbound.reports.ConfirmedV1Fixture.*;

class ConfirmedV1AdapterTest {
    @ParameterizedTest @EnumSource(ConfirmedV1Input.Channel.class)
    void projectsOriginalFactsWithoutRenumberingOrChangingText(ConfirmedV1Input.Channel channel) {
        var source = order(channel);
        var report = ConfirmedV1Adapter.adapt(input(source));
        var actual = report.orders().getFirst();
        assertEquals(List.of(1L, 3L, 5L), actual.originalLines().stream().map(ReportLine::orderScanSeq).toList());
        assertEquals(List.of("003", "007", "003"), actual.originalLines().stream().map(ReportLine::productCode).toList());
        assertEquals(channel == ConfirmedV1Input.Channel.PC ? "00000000101" : "00000000001", actual.originalLines().getFirst().barcode());
        assertEquals("300501", actual.effectiveLines().getFirst().weightGrams());
        assertEquals(source.header(), actual.effectiveHeader());
        assertEquals(source.exceptions(), actual.exceptions());
        assertEquals("2026-09-18", Values.shipmentDay(actual).toString());
        assertTrue(actual.adjustments().isEmpty());
        assertEquals("FORMAL_" + channel, actual.source());
    }

    @Test void retainsInputMembershipAndEmptySelection() {
        var source = input(order(ConfirmedV1Input.Channel.PC), order(ConfirmedV1Input.Channel.PDA));
        assertEquals(List.of("order-PC", "order-PDA"), ConfirmedV1Adapter.adapt(source).orders().stream().map(OrderSnapshot::id).toList());
        assertEquals(source.metadata(), ConfirmedV1Adapter.adapt(source).metadata());
        assertTrue(ConfirmedV1Adapter.adapt(input()).orders().isEmpty());
    }

    @Test void neverPresentsV1AsTheCurrentEffectiveResult() {
        var source = new ConfirmedV1Input(Samples.report(List.of()).metadata(), input(order(ConfirmedV1Input.Channel.PDA)).orders());
        assertThrows(IllegalArgumentException.class, () -> ConfirmedV1Adapter.adapt(source));
    }

    @ParameterizedTest @CsvSource({"orderId,another-order", "submissionId,another-submission", "status,REVIEW_REQUIRED",
            "resultVersion,2", "actualCount,2", "actualWeightGrams,901504", "firstOrderScanSeq,2", "lastOrderScanSeq,6",
            "orderStatus,LOADING", "orderNo,another-number", "freezeDeviceId,another-device", "captureHighWatermark,4",
            "completedAt,2026-09-17T15:59:59Z"})
    void rejectsMismatchedPdaReceipt(String field, String value) {
        var source = order(ConfirmedV1Input.Channel.PDA);
        var receipt = (ObjectNode) source.successReceipt(); receipt.put(field, value);
        assertThrows(IllegalArgumentException.class, () -> ConfirmedV1Adapter.adapt(input(withReceipt(source, receipt))));
    }

    @ParameterizedTest @CsvSource({"channel,PDA", "workId,another-work", "weightGrams,1"})
    void rejectsMismatchedPcReceipt(String field, String value) {
        var source = order(ConfirmedV1Input.Channel.PC);
        var receipt = (ObjectNode) source.successReceipt(); receipt.put(field, value);
        assertThrows(IllegalArgumentException.class, () -> ConfirmedV1Adapter.adapt(input(withReceipt(source, receipt))));
    }

    @Test void rejectsNumericReceiptWeightsInsteadOfCoercingToStrings() {
        var source = order(ConfirmedV1Input.Channel.PDA);
        var receipt = (ObjectNode) source.successReceipt(); receipt.put("actualWeightGrams", 901503);
        assertThrows(IllegalArgumentException.class, () -> ConfirmedV1Adapter.adapt(input(withReceipt(source, receipt))));
    }

    @Test void reconcilesReceiptProductsEvenWhenGrandTotalMatches() {
        var source = order(ConfirmedV1Input.Channel.PDA);
        var receipt = (ObjectNode) source.successReceipt();
        ((ObjectNode) receipt.path("items").get(0)).put("actualWeightGrams", "601001");
        ((ObjectNode) receipt.path("items").get(1)).put("actualWeightGrams", "300502");
        assertThrows(IllegalArgumentException.class, () -> ConfirmedV1Adapter.adapt(input(withReceipt(source, receipt))));
    }

    @ParameterizedTest @ValueSource(strings = {"order", "submission", "version", "total", "missing-product", "duplicate-product", "zero-product"})
    void rejectsInconsistentPersistedResult(String problem) {
        var source = order(ConfirmedV1Input.Channel.PDA); var r = source.result();
        var products = new ArrayList<>(r.products());
        if (problem.equals("missing-product")) products.removeLast();
        if (problem.equals("duplicate-product")) products.add(products.getFirst());
        if (problem.equals("zero-product")) products.set(2, new ConfirmedV1Input.ProductTotal("009", "1", "1"));
        var result = new ConfirmedV1Input.Result(problem.equals("order") ? "other" : r.orderId(),
                problem.equals("submission") ? "other" : r.submissionId(), problem.equals("version") ? 2 : 1,
                r.actualCount(), problem.equals("total") ? "901504" : r.weightGrams(), products);
        assertThrows(IllegalArgumentException.class, () -> ConfirmedV1Adapter.adapt(input(withResult(source, result))));
    }

    @ParameterizedTest @ValueSource(strings = {"order", "submission", "device", "reverse"})
    void rejectsMixedOrReorderedDispatches(String problem) {
        var source = order(ConfirmedV1Input.Channel.PDA); var rows = new ArrayList<>(source.dispatches()); var d = rows.get(1);
        rows.set(1, new ConfirmedV1Input.Dispatch(problem.equals("order") ? "other" : d.orderId(),
                problem.equals("submission") ? "other" : d.submissionId(),
                problem.equals("device") ? Samples.changed(d.line(), "device", "other") : d.line()));
        if (problem.equals("reverse")) Collections.reverse(rows);
        assertThrows(IllegalArgumentException.class, () -> ConfirmedV1Adapter.adapt(input(withDispatches(source, rows))));
    }

    @Test void retainsReceiptImmutability() {
        var source = order(ConfirmedV1Input.Channel.PDA); var receipt = (ObjectNode) source.successReceipt();
        var copied = withReceipt(source, receipt); receipt.put("status", "ABORTED");
        ((ObjectNode) copied.successReceipt()).put("status", "ABORTED");
        assertEquals("SUCCEEDED", copied.successReceipt().path("status").textValue());
        assertEquals(1, ConfirmedV1Adapter.adapt(input(copied)).orders().size());
    }

    @Test void rejectsTemporarySourceAndUnsupportedReceiptVersion() {
        var source = order(ConfirmedV1Input.Channel.PDA); var receipt = (ObjectNode) source.successReceipt();
        ((ObjectNode) receipt.path("source")).put("kind", "TEMPORARY");
        assertThrows(IllegalArgumentException.class, () -> ConfirmedV1Adapter.adapt(input(withReceipt(source, receipt))));
        var versioned = (ObjectNode) source.successReceipt(); versioned.put("receiptVersion", 2);
        assertThrows(IllegalArgumentException.class, () -> ConfirmedV1Adapter.adapt(input(withReceipt(source, versioned))));
    }

    @Test void reconcilesGramTotalsBeyondExcelNumericPrecision() {
        var source = order(ConfirmedV1Input.Channel.PDA); var rows = new ArrayList<>(source.dispatches()); var d = rows.getFirst();
        rows.set(0, new ConfirmedV1Input.Dispatch(d.orderId(), d.submissionId(), Samples.changed(d.line(), "weight", "9007199254740993")));
        var result = new ConfirmedV1Input.Result(source.id(), source.result().submissionId(), 1, "3", "9007199255341995",
                List.of(new ConfirmedV1Input.ProductTotal("003", "2", "9007199255041494"),
                        source.result().products().get(1), source.result().products().get(2)));
        var receipt = (ObjectNode) source.successReceipt(); receipt.put("actualWeightGrams", result.weightGrams());
        ((ObjectNode) receipt.path("items").get(0)).put("actualWeightGrams", "9007199255041494");
        var report = ConfirmedV1Adapter.adapt(input(withReceipt(withResult(withDispatches(source, rows), result), receipt)));
        assertEquals("9007199254740993", report.orders().getFirst().effectiveLines().getFirst().weightGrams());
    }

    @Test void rejectsReceiptAfterCutoffEvenWhenItHasTheSameShipmentDay() {
        var source = order(ConfirmedV1Input.Channel.PDA); var receipt = (ObjectNode) source.successReceipt();
        receipt.put("completedAt", Samples.CONFIRMED.plusSeconds(60).toString());
        var m = input().metadata();
        var metadata = new ReportSnapshot.Metadata(m.snapshotId(), m.dataVersion(), Samples.CONFIRMED.plusSeconds(1),
                m.generatedAt(), m.fromDate(), m.throughDate(), m.basis(), m.provenance(), m.sourceDescription(), m.orderSortDescription());
        var input = new ConfirmedV1Input(metadata, List.of(withReceipt(source, receipt)));
        assertThrows(IllegalArgumentException.class, () -> ConfirmedV1Adapter.adapt(input));
    }
}
