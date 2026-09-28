package com.acrylic.outbound.exports;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.reports.Adjustment;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** B3 scalar changes are proved by frozen CREATE receipts and adjacent immutable headers, not numeric lines. */
@Component
public final class HeaderCorrectionSource {
    private final JdbcTemplate jdbc;
    public HeaderCorrectionSource(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    List<Adjustment> read(UUID order,long targetVersion) {
        var versions=jdbc.query("""
            SELECT BIN_TO_UUID(d.id),d.reason,d.document_sha256,BIN_TO_UUID(d.requested_by),
              BIN_TO_UUID(d.approved_by),d.approved_at,r.result_version,
              CAST(p.header_snapshot AS CHAR),CAST(r.header_snapshot AS CHAR),
              (SELECT COUNT(*) FROM correction_line c WHERE c.document_id=d.id),
              p.actual_count=r.actual_count AND p.weight_g=r.weight_g
            FROM order_result_version r JOIN correction_document d ON d.id=r.source_correction_id AND d.order_id=r.order_id
            LEFT JOIN order_result_version p ON p.order_id=r.order_id AND p.result_version=r.result_version-1
            WHERE r.order_id=? AND r.result_version BETWEEN 2 AND ? AND d.type='HEADER_CORRECTION'
              AND r.result_version=d.effective_result_version AND d.status IN ('EFFECTIVE','WITHDRAW_PENDING','WITHDRAWN')
            ORDER BY r.result_version
            """,(rs,n)->new Version(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),
                    rs.getTimestamp(6)==null?null:rs.getTimestamp(6).toInstant(),rs.getLong(7),rs.getString(8),rs.getString(9),
                    rs.getInt(10),rs.getBoolean(11)),Ids.bytes(order),targetVersion);
        var result=new ArrayList<Adjustment>();
        for(var version:versions)result.addAll(adjustments(order,version));
        return result;
    }

    private List<Adjustment> adjustments(UUID order,Version version) {
        ExportFacts.check(version.lines()==0 && version.unchangedTotals(),"抬头更正不应含数值明细或改变数重");
        ExportFacts.check(version.before()!=null && version.after()!=null && version.approver()!=null && version.at()!=null,
                "抬头更正缺少版本或审批事实");
        var receipt=receipt(order,version);var command=SubmissionJson.parse(receipt.command());
        var patch=command.path("headerPatch");var before=SubmissionJson.parse(version.before());var after=SubmissionJson.parse(version.after());
        ExportFacts.check(patch.isObject() && !patch.isEmpty() && before.isObject() && after.isObject(),"抬头补丁或前后快照缺失");
        ObjectNode expected=before.deepCopy();expected.setAll((ObjectNode)patch);
        ExportFacts.check(expected.equals(after),"抬头补丁与不可变结果版本不一致");
        var fields=new TreeMap<String,JsonNode>();patch.fields().forEachRemaining(e->fields.put(e.getKey(),e.getValue()));
        var result=new ArrayList<Adjustment>();
        for(var field:fields.entrySet()) {
            String key=field.getKey();JsonNode old=before.get(key),value=field.getValue();
            ExportFacts.check(old!=null && old.isValueNode() && value.isValueNode(),"抬头字段必须是已有标量");
            if(old.equals(value))continue;
            ExportFacts.check(Set.of("orderNo","vehicle","container","remark").contains(key),"报告未映射该抬头字段："+key);
            result.add(new Adjustment(version.id(),"",version.number(),Adjustment.Kind.HEADER_CORRECTION,"",0,0,"0","0",
                    key,scalar(old),scalar(value),version.at(),version.reason(),version.approver(),
                    "correction_document:"+version.id()+" / create_receipt:"+receipt.id()+" / headerPatch:/"+key
                    +" / result_versions:"+(version.number()-1)+"->"+version.number()));
        }
        ExportFacts.check(!result.isEmpty(),"抬头更正没有实际字段变化");
        return result;
    }

    private Receipt receipt(UUID order,Version version) {
        var rows=jdbc.query("""
            SELECT BIN_TO_UUID(id),canonical_command_bytes,response_bytes,BIN_TO_UUID(actor_id),HEX(command_sha256),HEX(response_sha256)
            FROM correction_command_receipt WHERE order_id=? AND document_id=UUID_TO_BIN(?)
              AND command_type='CREATE' AND status='RECORDED'
            """,(rs,n)->new Receipt(rs.getString(1),rs.getBytes(2),rs.getBytes(3),rs.getString(4),rs.getString(5),rs.getString(6)),
                Ids.bytes(order),version.id());
        ExportFacts.check(rows.size()==1,"抬头CREATE回执缺失或不唯一");var receipt=rows.getFirst();
        ExportFacts.check(sha(receipt.command()).equalsIgnoreCase(version.digest())
                && sha(receipt.command()).equalsIgnoreCase(receipt.commandHash())
                && sha(receipt.response()).equalsIgnoreCase(receipt.responseHash()),"抬头原始命令或回执摘要不符");
        var body=SubmissionJson.parse(receipt.command());var decision=SubmissionJson.parse(receipt.response()).path("businessDecision");
        ExportFacts.check(order.toString().equals(body.path("orderId").asText()) && "HEADER_CORRECTION".equals(body.path("type").asText())
                && version.reason().equals(body.path("reason").asText()) && version.requester().equals(receipt.actor())
                && version.id().equals(decision.path("documentId").asText())
                && version.digest().equals(decision.path("documentSha256").asText()),"抬头CREATE回执与文档不一致");
        return receipt;
    }
    private static String scalar(JsonNode value) { return value.isNull()?"null":value.asText(); }
    private static String sha(byte[] bytes) {
        try { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch(java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private record Receipt(String id,byte[] command,byte[] response,String actor,String commandHash,String responseHash) { }
    private record Version(String id,String reason,String digest,String requester,String approver,Instant at,long number,
            String before,String after,int lines,boolean unchangedTotals) { }
}
