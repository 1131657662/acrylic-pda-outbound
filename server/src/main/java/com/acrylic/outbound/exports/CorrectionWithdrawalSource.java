package com.acrylic.outbound.exports;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.reports.Adjustment;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Read-only withdrawal proof: inverse ledger entries and the recorded requester confirmation. */
@Component
public final class CorrectionWithdrawalSource {
    private final JdbcTemplate jdbc;
    public CorrectionWithdrawalSource(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    Adjustment read(UUID order,Map<String,Object> row) {
        String document=String.valueOf(row.get("document_id")),line=String.valueOf(row.get("line_id"));
        long version=Long.parseLong(String.valueOf(row.get("applied_version")));
        var receipt=receipt(order,document,version,String.valueOf(row.get("withdrawal_confirmed_by")));
        String references=ledgerProof(row,line);
        var decision=SubmissionJson.parse(receipt.response()).path("businessDecision");
        ExportFacts.check("WITHDRAWN".equals(decision.path("status").asText()),"撤销确认回执状态不符");
        Instant at=confirmedAt(row.get("withdrawal_confirmed_at"));
        return new Adjustment(document,line,version,Adjustment.Kind.CORRECTION_WITHDRAWAL,
                String.valueOf(row.get("dispatch_id")),number(row,"target_count").intValue(),number(row,"before_count").intValue(),
                number(row,"target_weight_g").toString(),number(row,"before_weight_g").toString(),"","","",at,
                SubmissionJson.parse(receipt.command()).path("reason").asText(),String.valueOf(row.get("approved_by")),
                "correction_document:"+document+" / correction_line:"+line+" / withdrawal_receipt:"+receipt.id()
                +" / confirmed_by:"+receipt.actor()+" / "+references);
    }

    private Receipt receipt(UUID order,String document,long version,String confirmer) {
        var rows=jdbc.query("""
            SELECT BIN_TO_UUID(id),canonical_command_bytes,response_bytes,BIN_TO_UUID(actor_id)
            FROM correction_command_receipt WHERE order_id=? AND document_id=UUID_TO_BIN(?)
              AND command_type='WITHDRAW_CONFIRM' AND status='RECORDED'
            """,(rs,n)->new Receipt(rs.getString(1),rs.getBytes(2),rs.getBytes(3),rs.getString(4)),Ids.bytes(order),document);
        ExportFacts.check(rows.size()==1,"撤销确认回执缺失或不唯一");
        var receipt=rows.getFirst();var decision=SubmissionJson.parse(receipt.response()).path("businessDecision");
        ExportFacts.check(receipt.actor().equals(confirmer) && decision.path("resultVersion").asLong()==version
                && document.equals(decision.path("documentId").asText()),"撤销确认回执与版本不一致");
        return receipt;
    }

    private String ledgerProof(Map<String,Object> row,String line) {
        var entries=jdbc.queryForList("""
            SELECT BIN_TO_UUID(r.id) id,BIN_TO_UUID(f.id) reverses_id,r.quantity_delta,r.weight_delta_g,
              f.quantity_delta forward_count,f.weight_delta_g forward_weight
            FROM correction_ledger r JOIN correction_ledger f ON f.id=r.reverses_entry_id
            WHERE r.correction_line_id=UUID_TO_BIN(?) AND f.correction_line_id=r.correction_line_id
              AND f.reverses_entry_id IS NULL ORDER BY r.id
            """,line);
        ExportFacts.check(!entries.isEmpty() && entries.size()==number(row,"ledger_rows").longValue(),"撤销反向账不完整");
        long count=0,weight=0;var refs=new ArrayList<String>();
        for(var entry:entries) {
            long c=number(entry,"quantity_delta").longValue(),w=number(entry,"weight_delta_g").longValue();
            ExportFacts.check(c==-number(entry,"forward_count").longValue() && w==-number(entry,"forward_weight").longValue(),
                    "撤销账未逐笔反向");
            count+=c;weight+=w;refs.add("withdrawal_ledger:"+entry.get("id")+" reverses:"+entry.get("reverses_id"));
        }
        ExportFacts.check(count==number(row,"before_count").longValue()-number(row,"target_count").longValue()
                && weight==number(row,"before_weight_g").longValue()-number(row,"target_weight_g").longValue(),
                "撤销账与原更正前后值不一致");
        return String.join(" / ",refs);
    }

    private static Number number(Map<String,Object> row,String key) { return (Number)row.get(key); }
    private static Instant confirmedAt(Object value) {
        if(value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
        if(value instanceof java.time.LocalDateTime local) return local.toInstant(java.time.ZoneOffset.UTC);
        if(value instanceof Instant instant) return instant;
        throw new com.acrylic.outbound.common.BusinessException(409,"EXPORT_SOURCE_UNAVAILABLE","撤销确认时刻不可解析");
    }
    private record Receipt(String id,byte[] command,byte[] response,String actor) { }
}
