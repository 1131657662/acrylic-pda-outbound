package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Lossless native column codec shared by the four fixed evidence tables. */
final class RecoveryTemporaryEvidenceColumns {
    private RecoveryTemporaryEvidenceColumns() { }

    static ObjectNode read(ResultSet rs, RecoveryTemporaryEvidenceTable table) throws SQLException {
        var row=SubmissionJson.object();
        for(String column:table.columns) {
            if(rs.getObject(column)==null) row.putNull(column);
            else if(table.binary.contains(column)) row.put(column,Base64.getEncoder().encodeToString(rs.getBytes(column)));
            else if(table.times.contains(column)) row.put(column,rs.getTimestamp(column).toInstant().toString());
            else row.put(column,rs.getString(column));
        }
        return row;
    }

    static Object[] values(JsonNode row, RecoveryTemporaryEvidenceTable table, List<String> columns) {
        fields(row,String.join(" ",table.columns));
        return columns.stream().map(column -> value(row,column,table)).toArray();
    }

    private static Object value(JsonNode row, String column, RecoveryTemporaryEvidenceTable table) {
        if(row.path(column).isNull()) return null;
        String value=text(row,column);
        if(table.binary.contains(column)) {
            byte[] bytes=Base64.getDecoder().decode(value);
            require(value.equals(Base64.getEncoder().encodeToString(bytes)) && bytes.length>0
                && bytes.length <= (column.equals("manifest_bytes")?262144:16), "RECOVERY_EVIDENCE_ROW_BINARY");
            require(column.equals("manifest_bytes") || (bytes.length==16 && !Arrays.equals(bytes,new byte[16])), "RECOVERY_EVIDENCE_ROW_BINARY"); return bytes;
        }
        if(table.numbers.contains(column)) return revision(row,column);
        if(table.times.contains(column)) {
            Instant instant=Instant.parse(value);
            require(instant.toString().equals(value) && instant.getNano()%1_000_000==0,"RECOVERY_EVIDENCE_ROW_TIME");
            return Timestamp.from(instant);
        }
        require(value.length()<=262144,"RECOVERY_EVIDENCE_ROW_TEXT"); return value;
    }

    static String uuidBytes(UUID id) { return Base64.getEncoder().encodeToString(com.acrylic.outbound.common.Ids.bytes(id)); }
    static void equalsId(JsonNode row,String field,UUID expected) {
        require(expected==null?row.path(field).isNull():uuidBytes(expected).equals(text(row,field)),"RECOVERY_EVIDENCE_ROW_SOURCE");
    }
}
