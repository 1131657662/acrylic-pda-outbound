package com.acrylic.outbound.exports;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.reports.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.acrylic.outbound.submissions.SubmissionJson.parse;

@Repository
public class ExportFacts {
    private final JdbcTemplate jdbc;
    public ExportFacts(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Head head(UUID warehouse, UUID order) {
        var rows=jdbc.query("""
            SELECT o.order_no,o.source,o.status,BIN_TO_UUID(r.submission_id),BIN_TO_UUID(r.pc_submission_id),
              r.actual_count,r.weight_g,r.header_snapshot,r.created_at
            FROM outbound_order o LEFT JOIN order_result_version r ON r.order_id=o.id AND r.result_version=1
            WHERE o.warehouse_id=? AND o.id=?
            """,(rs,n)->new Head(order,rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),
                rs.getString(6),rs.getString(7),rs.getString(8)==null?null:parse(rs.getString(8)),
                rs.getTimestamp(9)==null?null:rs.getTimestamp(9).toInstant()),Ids.bytes(warehouse),Ids.bytes(order));
        if(rows.isEmpty()) throw new BusinessException(404,"ORDER_NOT_FOUND","订单不存在或无权访问");
        var h=rows.getFirst();
        check(h.source().equals("WEB") && h.status().equals("COMPLETED") && h.receipt()!=null,
                "只支持已完成正式订单的原确认文件");
        check(Long.parseLong(h.count())<=5000,"单次原确认文件最多5000包");
        return h;
    }
    public Plan plan(UUID order, int revision) {
        var plans=jdbc.query("""
            SELECT BIN_TO_UUID(id),vehicle_no,container_no,notes FROM order_revision WHERE order_id=? AND revision=?
            """,(rs,n)->new Plan(UUID.fromString(rs.getString(1)),new OrderSnapshot.Header(rs.getString(2),rs.getString(3),rs.getString(4))),
                Ids.bytes(order),revision);
        check(plans.size()==1,"原计划版本缺失");return plans.getFirst();
    }
    public List<OrderSnapshot.Product> products(Plan plan) {
        return jdbc.query("SELECT delivery_code,name_snapshot,model_snapshot,planned_count FROM order_line WHERE revision_id=? ORDER BY delivery_code",
                (rs,n)->new OrderSnapshot.Product(rs.getString(1),rs.getString(2),rs.getString(3),rs.getLong(4)),Ids.bytes(plan.id()));
    }
    public ConfirmedV1Input.Result result(Head head) {
        var products=jdbc.query("""
            SELECT delivery_code,actual_count,weight_g FROM order_effective_line WHERE order_id=? AND result_version=1 ORDER BY delivery_code
            """,(rs,n)->new ConfirmedV1Input.ProductTotal(rs.getString(1),rs.getString(2),rs.getString(3)),Ids.bytes(head.order()));
        return new ConfirmedV1Input.Result(head.order().toString(),head.submission(),1,head.count(),head.weight(),products);
    }
    public List<Dispatch> dispatches(Head head) {
        return jdbc.query("""
            SELECT BIN_TO_UUID(id),BIN_TO_UUID(order_id),BIN_TO_UUID(COALESCE(submission_id,pc_submission_id)),
              BIN_TO_UUID(COALESCE(capture_id,pc_entry_id)),BIN_TO_UUID(bale_id),order_scan_seq,dispatch_cycle,delivery_code,weight_g,raw_weight
            FROM dispatch_record WHERE order_id=? AND COALESCE(submission_id,pc_submission_id)=? ORDER BY order_scan_seq LIMIT 5001
            """,(rs,n)->new Dispatch(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),
                rs.getLong(6),rs.getLong(7),rs.getString(8),rs.getString(9),rs.getString(10)),Ids.bytes(head.order()),Ids.bytes(UUID.fromString(head.submission())));
    }
    static void check(boolean valid,String message) {
        if(!valid) throw new BusinessException(409,"EXPORT_SOURCE_UNAVAILABLE",message);
    }
    public record Head(UUID order,String number,String source,String status,String pdaSubmission,String pcSubmission,
            String count,String weight,JsonNode receipt,Instant confirmedAt) {
        String submission() { return pdaSubmission!=null?pdaSubmission:pcSubmission; }
    }
    public record Plan(UUID id,OrderSnapshot.Header header) { }
    public record Dispatch(String id,String order,String submission,String capture,String bale,long sequence,long cycle,
            String code,String weight,String rawWeight) { }
}
