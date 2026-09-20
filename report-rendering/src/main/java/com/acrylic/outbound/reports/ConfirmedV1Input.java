package com.acrylic.outbound.reports;

import java.time.Instant;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;

/** Module-owned projection of fixed v1 facts, not a server API or a database query. */
public record ConfirmedV1Input(ReportSnapshot.Metadata metadata, List<Order> orders) {
    public ConfirmedV1Input { orders = List.copyOf(orders); }
    public enum Channel { PDA, PC }

    public record Order(String id, String number, long revision, OrderSnapshot.Header header,
            Instant confirmedAt, Channel channel, String sequenceReference,
            List<OrderSnapshot.Product> products, List<Dispatch> dispatches,
            Result result, JsonNode successReceipt, List<OrderSnapshot.ExceptionRow> exceptions) {
        public Order {
            products = List.copyOf(products);
            dispatches = List.copyOf(dispatches);
            exceptions = List.copyOf(exceptions);
            successReceipt = successReceipt.deepCopy();
        }
        @Override public JsonNode successReceipt() { return successReceipt.deepCopy(); }
    }

    public record Dispatch(String orderId, String submissionId, ReportLine line) { }
    public record Result(String orderId, String submissionId, long version,
            String actualCount, String weightGrams, List<ProductTotal> products) {
        public Result { products = List.copyOf(products); }
    }
    public record ProductTotal(String productCode, String actualCount, String weightGrams) { }
}
