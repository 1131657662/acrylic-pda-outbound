package com.acrylic.outbound.orders;

import com.acrylic.outbound.common.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OrderQueries {
    private final JdbcTemplate jdbc;
    public OrderQueries(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    private static final String HEAD="""
        SELECT BIN_TO_UUID(o.id),o.order_no,o.source,o.status,o.current_revision,o.row_version,
          r.vehicle_no,r.container_no,r.planned_total,u.display_name,o.created_at,o.updated_at
        FROM outbound_order o INNER JOIN order_revision r ON r.order_id=o.id AND r.revision=o.current_revision
        INNER JOIN app_user u ON u.id=o.created_by WHERE o.warehouse_id=?
        """;
    private static final String REVISION="""
        SELECT BIN_TO_UUID(r.id),r.revision,BIN_TO_UUID(r.inventory_snapshot_id),r.mapping_revision,
          r.vehicle_no,r.container_no,r.notes,r.planned_total,r.reason,u.display_name,r.created_at
        FROM order_revision r INNER JOIN app_user u ON u.id=r.created_by WHERE r.order_id=?
        """;
    public OrderViews.Summary head(UUID warehouse,UUID order) {
        var rows=jdbc.query(HEAD+" AND o.id=?",this::summary,Ids.bytes(warehouse),Ids.bytes(order));
        if(rows.isEmpty()) throw new BusinessException(404,"ORDER_NOT_FOUND","订单不存在或无权访问");
        return rows.getFirst();
    }
    public Page<OrderViews.Summary> list(UUID warehouse,Filter filter) {
        int limit=Page.checkedLimit(filter.limit());
        if(filter.search().length()>40 || !Set.of("","DRAFT","READY","ASSIGNED","RECALL_PENDING","REVIEW_REQUIRED",
            "COMPLETED","CANCELLED","CONTINUING","LINKED").contains(filter.status()))
            throw new BusinessException(400,"INVALID_REQUEST","订单筛选条件无效");
        var sql=new StringBuilder(HEAD); var params=new ArrayList<Object>(); params.add(Ids.bytes(warehouse));
        if(!filter.status().isEmpty()) { sql.append(" AND o.status=?"); params.add(filter.status()); }
        if(!filter.search().isBlank()) {
            String search=filter.search().strip();
            sql.append(" AND (o.order_no=? OR r.vehicle_no=?)");
            // Order numbers are ASCII; never bind a Chinese plate against an ASCII column.
            params.add(search.chars().allMatch(c->c<128)?search:"");params.add(search);
        }
        if(!filter.cursor().isEmpty()) {
            var cursor=OrderCursor.parse(filter.cursor());
            sql.append(" AND (o.created_at<? OR (o.created_at=? AND o.id<?))");
            params.add(cursor.createdAt());params.add(cursor.createdAt());params.add(Ids.bytes(cursor.id()));
        }
        sql.append(" ORDER BY o.created_at DESC,o.id DESC LIMIT ?");params.add(limit+1);
        return Page.from(jdbc.query(sql.toString(),this::summary,params.toArray()),limit,OrderCursor::encode);
    }
    public OrderViews.Detail detail(UUID warehouse,UUID order,Integer revision) {
        var header=head(warehouse,order);
        var rows=jdbc.query(REVISION+" AND r.revision=?",this::revision,Ids.bytes(order),revision==null?header.revision():revision);
        if(rows.isEmpty()) throw new BusinessException(404,"ORDER_REVISION_NOT_FOUND","订单版本不存在");
        var plan=rows.getFirst();
        var lines=jdbc.query("""
            SELECT delivery_code,BIN_TO_UUID(mapping_id),BIN_TO_UUID(product_id),model_snapshot,name_snapshot,planned_count
            FROM order_line WHERE revision_id=? ORDER BY delivery_code
            """,(rs,n)->new OrderViews.Line(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getInt(6)),
            Ids.bytes(UUID.fromString(plan.id())));
        return new OrderViews.Detail(header,plan,lines);
    }
    public Page<OrderViews.Revision> history(UUID warehouse,UUID order,History filter) {
        head(warehouse,order);int limit=Page.checkedLimit(filter.limit());
        if(filter.before()<=0) throw new BusinessException(400,"INVALID_CURSOR","版本游标无效");
        return Page.from(jdbc.query(REVISION+" AND r.revision<? ORDER BY r.revision DESC LIMIT ?",this::revision,
            Ids.bytes(order),filter.before(),limit+1),limit,r->Integer.toString(r.revision()));
    }
    public Page<OrderViews.Event> events(UUID warehouse,UUID order,History filter) {
        head(warehouse,order);int limit=Page.checkedLimit(filter.limit());
        if(filter.before()<=0) throw new BusinessException(400,"INVALID_CURSOR","事件游标无效");
        var rows=jdbc.query("""
            SELECT e.id,e.event_type,u.display_name,e.details,e.server_time FROM operation_event e
            LEFT JOIN app_user u ON u.id=e.actor_id WHERE e.entity_type='ORDER' AND e.entity_id=? AND e.id<?
            ORDER BY e.id DESC LIMIT ?
            """,(rs,n)->new OrderViews.Event(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getTimestamp(5).toInstant().toString()),
            Ids.bytes(order),filter.before(),limit+1);
        return Page.from(rows,limit,OrderViews.Event::id);
    }
    private OrderViews.Summary summary(java.sql.ResultSet rs,int n) throws java.sql.SQLException {
        return new OrderViews.Summary(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getInt(5),rs.getString(6),
            rs.getString(7),rs.getString(8),rs.getString(9),rs.getString(10),rs.getTimestamp(11).toInstant().toString(),rs.getTimestamp(12).toInstant().toString());
    }
    private OrderViews.Revision revision(java.sql.ResultSet rs,int n) throws java.sql.SQLException {
        return new OrderViews.Revision(rs.getString(1),rs.getInt(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),
            rs.getString(7),rs.getString(8),rs.getString(9),rs.getString(10),rs.getTimestamp(11).toInstant().toString());
    }
    public record Filter(String status,String search,String cursor,int limit) { }
    public record History(long before,int limit) { }
}
