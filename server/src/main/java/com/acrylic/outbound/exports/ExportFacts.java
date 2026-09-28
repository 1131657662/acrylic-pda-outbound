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
    /**
     * R08 子项 C：该提交是否源自 TEMP。TEMP 的 {@code submission_scope} 没有 {@code source_order_id}
     * （FORMAL 必有，见 {@code ck_submission_scope_source}），据此可区分"TEMP 受理正文"与"正式 PDA 正文"；
     * 两者正文形状不同（TEMP 没有 {@code source.sourceOrderId}），导出读取器必须据此选路。
     */
    public boolean tempOrigin(String submission) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM submission s JOIN submission_scope c ON c.id=s.scope_id "
            + "WHERE s.id=? AND c.source_order_id IS NULL",Integer.class,Ids.bytes(UUID.fromString(submission)))==1;
    }
    public Head head(UUID warehouse, UUID order) { return head(warehouse, order, 1L); }

    /**
     * D 片1：按**指定结果版本**取订单事实。默认 v1（原确认）路径的行为与以前逐字相同；
     * v&gt;1 的行按 `V46` 的 `ck_result_channel` 第三支**必然没有提交**（两列皆 NULL、只有
     * {@code source_correction_id}），所以这里对 v&gt;1 **不要求提交非空**，改由
     * {@link CorrectionExportSource} 从更正凭据组装快照。
     */
    public Head head(UUID warehouse, UUID order, long resultVersion) {
        var rows=jdbc.query("""
            SELECT o.order_no,o.source,o.status,BIN_TO_UUID(r.submission_id),BIN_TO_UUID(r.pc_submission_id),
              CAST(r.actual_count AS CHAR),CAST(r.weight_g AS CHAR),r.header_snapshot,r.created_at,
              CAST(r.result_version AS CHAR)
            FROM outbound_order o LEFT JOIN order_result_version r ON r.order_id=o.id AND r.result_version=?
            WHERE o.warehouse_id=? AND o.id=?
            """,(rs,n)->new Head(warehouse,order,rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),
                rs.getString(6),rs.getString(7),rs.getString(8)==null?null:parse(rs.getString(8)),
                rs.getTimestamp(9)==null?null:rs.getTimestamp(9).toInstant(),rs.getString(10)),
                resultVersion,Ids.bytes(warehouse),Ids.bytes(order));
        if(rows.isEmpty()) throw new BusinessException(404,"ORDER_NOT_FOUND","订单不存在或无权访问");
        var h=rows.getFirst();
        check(Set.of("WEB","PDA_TEMP").contains(h.source()) && h.status().equals("COMPLETED") && h.receipt()!=null,
                "只支持已完成订单的原确认文件");
        // v1＝原确认：必须有且仅有一个提交来源；v>1＝更正净结果：必然没有提交（见 ck_result_channel）。
        if(h.resultVersion().equals("1")) check((h.pdaSubmission()==null)!=(h.pcSubmission()==null),"原确认提交来源不唯一");
        else check(h.pdaSubmission()==null && h.pcSubmission()==null,"更正版本不应携带提交来源");
        check(Long.parseLong(h.count())<=5000,"单次原确认文件最多5000包");
        return h;
    }
    /**
     * 该结果版本是否由某一更正文档产出（v&gt;1 的判定来源：{@code correction_document.effective_result_version}）。
     * 返回 null 表示该版本没有对应的更正文档 ⇒ 不属于"更正净结果"。
     */
    public String correctionDocumentFor(UUID order, long resultVersion) {
        var rows=jdbc.queryForList("SELECT BIN_TO_UUID(id) FROM correction_document WHERE order_id=? "
            + "AND effective_result_version=? AND status IN ('EFFECTIVE','WITHDRAW_PENDING') LIMIT 2",
            String.class,Ids.bytes(order),resultVersion);
        if(rows.isEmpty()) return null;
        check(rows.size()==1,"同一结果版本对应多个更正文档");
        return rows.getFirst();
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
    public record Head(UUID warehouse,UUID order,String number,String source,String status,String pdaSubmission,String pcSubmission,
            String count,String weight,JsonNode receipt,Instant confirmedAt,String resultVersion) {
        /** 兼容既有构造点：resultVersion 缺省为 "1"（原确认），JSON 形状不变。 */
        public Head(UUID warehouse,UUID order,String number,String source,String status,String pdaSubmission,String pcSubmission,
                String count,String weight,JsonNode receipt,Instant confirmedAt) {
            this(warehouse,order,number,source,status,pdaSubmission,pcSubmission,count,weight,receipt,confirmedAt,"1");
        }
        String submission() { return pdaSubmission!=null?pdaSubmission:pcSubmission; }
    }
    public record Plan(UUID id,OrderSnapshot.Header header) { }
    public record Dispatch(String id,String order,String submission,String capture,String bale,long sequence,long cycle,
            String code,String weight,String rawWeight) { }
}
