package com.acrylic.outbound.reports;

import java.util.*;
import java.time.Instant;
import com.fasterxml.jackson.databind.JsonNode;
import static com.acrylic.outbound.reports.Values.*;

/** Checks the existing receipt fields; signature and snapshot authenticity remain upstream. */
final class ConfirmedV1Receipt {
    static void validate(ConfirmedV1Input.Order order, Map<String, ConfirmedV1Totals.Total> products, Instant cutoff) {
        var receipt = order.successReceipt();
        require(receipt.isObject(), "successReceipt must be an object");
        require(textAt(receipt, "orderId").equals(order.id()), "receipt belongs to another order");
        require(textAt(receipt, "submissionId").equals(order.result().submissionId()), "receipt belongs to another submission");
        require(textAt(receipt, "status").equals("SUCCEEDED"), "receipt is not successful");
        require(textAt(receipt, "resultVersion").equals("1"), "receipt is not version 1");
        var total = products.values().stream().reduce(ConfirmedV1Totals.Total.ZERO, ConfirmedV1Totals.Total::add);
        Objects.requireNonNull(order.channel(), "channel");
        String weightField = order.channel() == ConfirmedV1Input.Channel.PDA ? "actualWeightGrams" : "weightGrams";
        ConfirmedV1Totals.match(total, textAt(receipt, "actualCount"), textAt(receipt, weightField));
        require(!order.dispatches().isEmpty(), "confirmed order has no dispatches");
        if (order.channel() == ConfirmedV1Input.Channel.PDA) {
            pdaBoundary(order, receipt, cutoff);
            pda(order, receipt, products);
        } else pc(order, receipt);
    }

    private static void pda(ConfirmedV1Input.Order order, JsonNode receipt, Map<String, ConfirmedV1Totals.Total> products) {
        require(!receipt.has("channel"), "PDA receipt unexpectedly declares another channel");
        var source = receipt.path("source");
        require(textAt(source, "kind").equals("FORMAL") && textAt(source, "sourceOrderId").equals(order.id()),
                "PDA receipt source is not this formal order");
        require(textAt(receipt, "orderStatus").equals("COMPLETED"), "PDA order is not completed");
        require(textAt(receipt, "orderNo").equals(order.number()), "PDA receipt order number differs");
        var lines = order.dispatches();
        require(textAt(receipt, "firstOrderScanSeq").equals(Long.toString(lines.getFirst().line().orderScanSeq()))
                && textAt(receipt, "lastOrderScanSeq").equals(Long.toString(lines.getLast().line().orderScanSeq())),
                "PDA receipt sequence endpoints differ");
        var items = receipt.path("items");
        require(items.isArray(), "PDA receipt items missing");
        var receiptProducts = new ArrayList<ConfirmedV1Input.ProductTotal>();
        for (var item : items) receiptProducts.add(new ConfirmedV1Input.ProductTotal(textAt(item, "deliveryCode"),
                textAt(item, "actualCount"), textAt(item, "actualWeightGrams")));
        ConfirmedV1Totals.matchProducts(products, receiptProducts);
        String device = textAt(receipt, "freezeDeviceId");
        for (var dispatch : lines) {
            var line = dispatch.line();
            require(line.originDeviceId().equals(device), "PDA origin differs from freeze device; handover requires a separate adapter");
            require(Set.of("BROADCAST", "MANUAL").contains(line.inputSource()), "PDA input source invalid");
            require(Set.of("DEVICE_UNVERIFIED", "MISSING").contains(line.timeQuality()), "PDA clock trust must not be invented");
        }
    }

    private static void pdaBoundary(ConfirmedV1Input.Order order, JsonNode receipt, Instant cutoff) {
        var version = receipt.path("receiptVersion");
        require(version.isIntegralNumber() && version.canConvertToInt() && version.intValue() == 1, "unsupported PDA receipt version");
        var completed = Instant.parse(textAt(receipt, "completedAt"));
        require(!completed.isAfter(cutoff), "PDA success receipt is newer than snapshot cutoff");
        require(completed.atZone(ZONE).toLocalDate().equals(order.confirmedAt().atZone(ZONE).toLocalDate()),
                "PDA receipt and original confirmation disagree on shipment day; resolve the source snapshot first");
        String highWatermark = textAt(receipt, "captureHighWatermark");
        require(highWatermark.matches("[1-9][0-9]{0,18}"), "PDA captureHighWatermark must be a positive integer string");
        require(new java.math.BigInteger(highWatermark).compareTo(
                java.math.BigInteger.valueOf(order.dispatches().getLast().line().orderScanSeq())) >= 0,
                "PDA high watermark is below the last confirmed sequence");
    }

    private static void pc(ConfirmedV1Input.Order order, JsonNode receipt) {
        require(textAt(receipt, "channel").equals("PC"), "PC receipt channel differs");
        String origin = "pc-work:" + textAt(receipt, "workId");
        for (var dispatch : order.dispatches()) {
            var line = dispatch.line();
            require(line.originDeviceId().equals(origin), "PC origin must identify the original pc-work");
            require(line.deviceCaptureSeq() == line.orderScanSeq(), "PC source sequence must match entry sequence");
            require(line.inputSource().equals("PC_MANUAL") && line.timeQuality().equals("SERVER_CAPTURED"), "PC source metadata invalid");
            require(!line.savedAt().isBlank() && line.receivedAt().equals(line.savedAt()), "PC timestamps must retain entry created_at");
        }
    }

    private static String textAt(JsonNode node, String field) {
        var value = node.path(field);
        require(value.isTextual(), "receipt field must be a string: " + field);
        return required(value.textValue());
    }
    private ConfirmedV1Receipt() { }
}
