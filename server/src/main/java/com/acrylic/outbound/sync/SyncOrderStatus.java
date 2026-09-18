package com.acrylic.outbound.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class SyncOrderStatus {
    private final ObjectMapper mapper;
    private static final Set<String> STATUSES = Set.of("READY", "ASSIGNED", "REVIEW_REQUIRED",
        "COMPLETED", "CANCELLED", "RECALL_PENDING", "CONTINUING", "LINKED");
    public SyncOrderStatus(ObjectMapper mapper) { this.mapper = mapper; }
    public String from(String type, String payload) {
        String status = switch (type) {
            case "ORDER_PUBLISHED" -> "READY";
            case "ORDER_ASSIGNED" -> "ASSIGNED";
            case "ORDER_CANCELLED" -> "CANCELLED";
            default -> payloadStatus(payload);
        };
        if (!STATUSES.contains(status)) throw new IllegalStateException("不支持的订单同步状态: " + status);
        return status;
    }
    private String payloadStatus(String payload) {
        try { return mapper.readTree(payload).path("status").asText(); }
        catch (Exception error) { throw new IllegalStateException("订单同步事件损坏", error); }
    }
}
