package com.acrylic.outbound.orders;

import com.acrylic.outbound.common.Ids;
import java.sql.Timestamp;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import static com.acrylic.outbound.orders.OrderTemporaryCandidateModels.*;

/** Informational overlap only. Never locks, links, excludes, or changes either source. */
@Repository
public class OrderTemporaryCandidateQueries {
    private final NamedParameterJdbcTemplate jdbc;
    public OrderTemporaryCandidateQueries(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }
    private static final String OVERLAP="""
        EXISTS(SELECT 1 FROM order_line l WHERE l.revision_id=r.id AND l.product_id IN (:products))
        """;
    private static final String FROM="""
        FROM submission s INNER JOIN submission_scope c ON c.id=s.scope_id
        INNER JOIN submission_payload p ON p.submission_id=s.id
        INNER JOIN temporary_source_mapping m ON m.scope_id=c.id AND m.warehouse_id=c.warehouse_id AND m.mapping_version=1
        INNER JOIN outbound_order o ON o.id=m.canonical_order_id AND o.warehouse_id=c.warehouse_id AND o.source='PDA_TEMP'
        INNER JOIN order_revision r ON r.order_id=o.id AND r.revision=o.current_revision
        WHERE c.warehouse_id=:warehouse AND c.creator_device_id IS NOT NULL
          AND s.status IN ('SUCCEEDED','REVIEW_REQUIRED') AND p.received_at>=:start AND p.received_at<:end
          AND (BINARY r.vehicle_no=BINARY :vehicle OR
        """+OVERLAP+")";
    public Page read(OrderViews.Detail detail,Query query) {
        var params=new MapSqlParameterSource("warehouse",Ids.bytes(query.warehouse())).addValue("vehicle",detail.plan().vehicleNo())
            .addValue("start",Timestamp.from(query.range().start())).addValue("end",Timestamp.from(query.range().end()))
            .addValue("products",detail.lines().stream().map(l->Ids.bytes(UUID.fromString(l.productId()))).toList());
        long total=jdbc.queryForObject("SELECT COUNT(*) "+FROM,params,Long.class);
        int page=(int)Math.min(query.page(),Math.max(1,(total+19)/20));params.addValue("offset",(long)(page-1)*20);
        String select="SELECT BIN_TO_UUID(s.id),BIN_TO_UUID(o.id),o.order_no,s.status,r.vehicle_no,r.container_no,"
            +"r.planned_total,p.expected_count,p.received_at,(BINARY r.vehicle_no=BINARY :vehicle),"+OVERLAP;
        var items=jdbc.query(select+FROM+" ORDER BY (s.status='SUCCEEDED') DESC,p.received_at DESC,s.id DESC LIMIT 20 OFFSET :offset",params,
            (rs,n)->new Item(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),
                rs.getString(7),rs.getString(8),rs.getTimestamp(9).toInstant().toString(),rs.getBoolean(10),rs.getBoolean(11)));
        return new Page(items,total,page,20);
    }
    public boolean hasFormalWork(UUID order) {
        return jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM order_assignment WHERE order_id=:order)
              OR EXISTS(SELECT 1 FROM pc_work WHERE order_id=:order)
              OR EXISTS(SELECT 1 FROM submission_scope WHERE source_order_id=:order)
              OR EXISTS(SELECT 1 FROM order_result_version WHERE order_id=:order)
            """,new MapSqlParameterSource("order",Ids.bytes(order)),Boolean.class);
    }
}
