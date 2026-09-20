package com.acrylic.outbound.reports;

import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;

final class ConfirmedV1Fixture {
    static ConfirmedV1Input input(ConfirmedV1Input.Order... orders) {
        var m = Samples.report(List.of()).metadata();
        return new ConfirmedV1Input(new ReportSnapshot.Metadata("test-confirmed-v1", "synthetic-confirmed-v1",
                m.cutoff(), m.generatedAt(), m.fromDate(), m.throughDate(), ReportSnapshot.Basis.ORIGINAL_CONFIRMED,
                ReportSnapshot.Provenance.TEST_SAMPLE, "Synthetic PDA/PC v1 facts; no business data or verified signatures",
                "Explicit sample order membership; persisted per-order sequences"), List.of(orders));
    }

    static ConfirmedV1Input.Order order(ConfirmedV1Input.Channel channel) {
        String id = "order-" + channel, submission = "submission-" + channel;
        var dispatches = new ArrayList<ConfirmedV1Input.Dispatch>();
        for (int i = 0; i < 3; i++) dispatches.add(new ConfirmedV1Input.Dispatch(id, submission, line(channel, i)));
        var totals = List.of(new ConfirmedV1Input.ProductTotal("003", "2", "601002"),
                new ConfirmedV1Input.ProductTotal("007", "1", "300501"), new ConfirmedV1Input.ProductTotal("009", "0", "0"));
        var result = new ConfirmedV1Input.Result(id, submission, 1, "3", "901503", totals);
        var products = new ArrayList<>(Samples.order(3).products());
        products.add(new OrderSnapshot.Product("009", "UNUSED", "No dispatches", 1));
        return new ConfirmedV1Input.Order(id, "TEST-R08-V1-" + channel, 3, Samples.order(3).originalHeader(),
                Samples.CONFIRMED, channel, "Synthetic fixed dispatch sequence", products, dispatches, result,
                receipt(channel, result), List.of(new OrderSnapshot.ExceptionRow("void-" + channel, 2L,
                        origin(channel), "VOID", "00000000009", "Synthetic void excluded from confirmed facts", "2026-09-17T16:00:00Z")));
    }

    private static ReportLine line(ConfirmedV1Input.Channel channel, int index) {
        var r = Samples.line(index + 1); long sequence = index * 2L + 1;
        boolean pc = channel == ConfirmedV1Input.Channel.PC;
        return new ReportLine(channel + "-" + r.dispatchId(), channel + "-" + r.captureId(), sequence, origin(channel),
                pc ? sequence : 100 + sequence, r.operatorName(), pc ? "PC_MANUAL" : r.inputSource(),
                pc ? Samples.line(101 + index).barcode() : r.barcode(), r.dispatchCycle(), r.productCode(), r.weightGrams(), r.rawWeight(),
                pc ? r.savedAt() : r.receivedAt(), r.savedAt(), pc ? "SERVER_CAPTURED" : r.timeQuality(),
                index == 0 ? "=HYPERLINK(\"https://invalid.example\",\"synthetic\")" : r.remark());
    }

    private static String origin(ConfirmedV1Input.Channel channel) {
        return channel == ConfirmedV1Input.Channel.PC ? "pc-work:work-PC" : "device-PDA";
    }

    private static ObjectNode receipt(ConfirmedV1Input.Channel channel, ConfirmedV1Input.Result result) {
        var r = JsonNodeFactory.instance.objectNode().put("orderId", result.orderId()).put("submissionId", result.submissionId())
                .put("status", "SUCCEEDED").put("resultVersion", "1").put("actualCount", "3")
                .put("payloadSha256", "0".repeat(64)).put("inventoryDeducted", false);
        if (channel == ConfirmedV1Input.Channel.PC)
            return r.put("channel", "PC").put("workId", "work-PC").put("weightGrams", "901503");
        r.put("actualWeightGrams", "901503").put("freezeDeviceId", "device-PDA").put("orderStatus", "COMPLETED")
                .put("orderNo", "TEST-R08-V1-PDA").put("firstOrderScanSeq", "1").put("lastOrderScanSeq", "5")
                .put("completedAt", Samples.CONFIRMED.toString()).put("receiptVersion", 1).put("captureHighWatermark", "6")
                .put("workRound", "1").put("issuedRecoveryEpoch", 1).put("signedReceipt", "SYNTHETIC_NOT_A_SIGNATURE");
        r.putObject("source").put("kind", "FORMAL").put("sourceOrderId", result.orderId());
        var items = r.putArray("items");
        for (var p : result.products()) items.addObject().put("deliveryCode", p.productCode())
                .put("actualCount", p.actualCount()).put("actualWeightGrams", p.weightGrams());
        return r;
    }

    static ConfirmedV1Input.Order withReceipt(ConfirmedV1Input.Order o, JsonNode receipt) {
        return new ConfirmedV1Input.Order(o.id(), o.number(), o.revision(), o.header(), o.confirmedAt(), o.channel(),
                o.sequenceReference(), o.products(), o.dispatches(), o.result(), receipt, o.exceptions());
    }
    static ConfirmedV1Input.Order withResult(ConfirmedV1Input.Order o, ConfirmedV1Input.Result result) {
        return new ConfirmedV1Input.Order(o.id(), o.number(), o.revision(), o.header(), o.confirmedAt(), o.channel(),
                o.sequenceReference(), o.products(), o.dispatches(), result, o.successReceipt(), o.exceptions());
    }
    static ConfirmedV1Input.Order withDispatches(ConfirmedV1Input.Order o, List<ConfirmedV1Input.Dispatch> dispatches) {
        return new ConfirmedV1Input.Order(o.id(), o.number(), o.revision(), o.header(), o.confirmedAt(), o.channel(),
                o.sequenceReference(), o.products(), dispatches, o.result(), o.successReceipt(), o.exceptions());
    }
    private ConfirmedV1Fixture() { }
}
