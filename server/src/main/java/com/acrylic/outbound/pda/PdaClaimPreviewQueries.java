package com.acrylic.outbound.pda;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.orders.OrderQueries;
import com.acrylic.outbound.orders.OrderViews;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Read-only published revision detail; flattened header remains compatible with existing clients. */
@Repository
public class PdaClaimPreviewQueries {
    private final OrderQueries orders;
    private final JdbcTemplate jdbc;
    public PdaClaimPreviewQueries(OrderQueries orders, JdbcTemplate jdbc) { this.orders = orders; this.jdbc = jdbc; }

    public Preview read(UUID warehouse, UUID order) {
        var detail = orders.detail(warehouse, order, null);
        String name = jdbc.queryForObject("SELECT name FROM warehouse WHERE id=?", String.class, Ids.bytes(warehouse));
        return new Preview(detail.order(), warehouse.toString(), name, detail.lines());
    }

    public record Preview(@JsonUnwrapped OrderViews.Summary order, String warehouseId,
                          String warehouseName, List<OrderViews.Line> lines) { }
}
