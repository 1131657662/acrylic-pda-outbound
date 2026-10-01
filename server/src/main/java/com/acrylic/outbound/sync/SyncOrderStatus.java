package com.acrylic.outbound.sync;

import com.fasterxml.jackson.databind.JsonNode;
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
            case "HANDOVER_ACTIVATED" -> handoverStatus(payload);
            default -> payloadStatus(payload).path("status").asText();
        };
        if (!STATUSES.contains(status)) throw new IllegalStateException("不支持的订单同步状态: " + status);
        return status;
    }

    private JsonNode payloadStatus(String payload) {
        try {
            var node = mapper.readTree(payload);
            if (node == null || !node.isObject()) throw new IllegalArgumentException("同步载荷必须为对象");
            return node;
        } catch (Exception error) {
            throw new IllegalStateException("订单同步事件损坏", error);
        }
    }

    private String handoverStatus(String payload) {
        var node = payloadStatus(payload);
        if (!node.has("status")) return "ASSIGNED";
        if (!node.path("status").isTextual()) throw new IllegalStateException("交接同步状态类型无效");
        return node.path("status").asText();
    }
}
