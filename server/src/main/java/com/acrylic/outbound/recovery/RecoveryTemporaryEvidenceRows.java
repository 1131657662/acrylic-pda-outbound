package com.acrylic.outbound.recovery;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.require;

/** Insert-only restoration of native IDs, timestamps and all owner columns. */
final class RecoveryTemporaryEvidenceRows {
    private final JdbcTemplate jdbc;
    RecoveryTemporaryEvidenceRows(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    ObjectNode read(RecoverySourceIdentity source) {
        return query(source," FOR UPDATE");
    }

    ObjectNode readOriginal(RecoverySourceIdentity source) {
        return query(source,"");
    }

    private ObjectNode query(RecoverySourceIdentity source,String lock) {
        var result=SubmissionJson.object();
        for(var table:RecoveryTemporaryEvidenceTable.values()) {
            var query=scope(table,source);
            var rows=jdbc.query(table.select()+query.filter()+" ORDER BY "+String.join(",",table.primary)+" LIMIT 65537"+lock,
                (rs,n) -> RecoveryTemporaryEvidenceColumns.read(rs,table),query.values());
            require(rows.size()<=65536,"RECOVERY_EVIDENCE_ROW_LIMIT");
            var array=result.putArray(table.table); rows.forEach(array::add);
        }
        return result;
    }

    int restore(JsonNode original,RecoverySourceIdentity source) {
        var before=read(source); int inserted=0;
        for(var table:RecoveryTemporaryEvidenceTable.values()) {
            var wanted=SubmissionJson.array(original,table.table);
            for(var row:SubmissionJson.array(before,table.table)) require(contains(wanted,row),"RECOVERY_EVIDENCE_ROW_CONFLICT");
            for(var row:wanted) inserted+=restoreRow(table,row);
        }
        require(read(source).equals(original),"RECOVERY_EVIDENCE_ROW_SET"); return inserted;
    }

    private int restoreRow(RecoveryTemporaryEvidenceTable table,JsonNode row) {
        var primary=RecoveryTemporaryEvidenceColumns.values(row,table,table.primary);
        var existing=jdbc.query(table.select()+" WHERE "+table.primaryFilter()+" FOR UPDATE",
            (rs,n) -> RecoveryTemporaryEvidenceColumns.read(rs,table),primary);
        if(!existing.isEmpty()) {
            require(existing.size()==1 && existing.getFirst().equals(row),"RECOVERY_EVIDENCE_ROW_CONFLICT"); return 0;
        }
        require(jdbc.update(table.insert(),RecoveryTemporaryEvidenceColumns.values(row,table,table.columns))==1,
            "RECOVERY_EVIDENCE_ROW_WRITE"); return 1;
    }

    private static boolean contains(JsonNode values,JsonNode expected) {
        for(var value:values) if(value.equals(expected)) return true;
        return false;
    }

    private static Query scope(RecoveryTemporaryEvidenceTable table,RecoverySourceIdentity id) {
        if(table==RecoveryTemporaryEvidenceTable.RESERVATION)
            return new Query(" WHERE source_scope_id=? OR submission_id=?",new Object[]{Ids.bytes(id.scopeId()),Ids.bytes(id.submissionId())});
        return new Query(" WHERE scope_id=? OR submission_id=?",new Object[]{Ids.bytes(id.scopeId()),Ids.bytes(id.submissionId())});
    }
    private record Query(String filter,Object[] values) { }
}
