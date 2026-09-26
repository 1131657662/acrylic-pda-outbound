package com.acrylic.outbound.orders;

import java.util.List;

public final class OrderViews {
    private OrderViews() { }
    public record Summary(String id, String orderNo, String source, String status, int revision,
        String rowVersion, String vehicleNo, String containerNo, String plannedTotal, String createdBy,
        String createdAt, String updatedAt) { }
    public record Line(String deliveryCode, String mappingId, String productId, String model, String name, int plannedCount) { }
    public record Revision(String id, int revision, String inventorySnapshotId, String mappingRevision,
        String vehicleNo, String containerNo, String notes, String plannedTotal, String reason, String createdBy, String createdAt) { }
    public record Detail(Summary order, Revision plan, List<Line> lines) { }
    public record NumberedPage(List<Summary> items,long total,int page,int pageSize) { }
    public record Receipt(String id, String orderId, int revision, String rowVersion, String status) {
        @com.fasterxml.jackson.annotation.JsonProperty
        public OrderTemporaryCandidateModels.Check temporaryCandidateCheck() {
            return OrderTemporaryCandidateModels.Check.forOrder(orderId);
        }
    }
    public record Event(String id, String type, String actor, String details, String serverTime) { }
}
