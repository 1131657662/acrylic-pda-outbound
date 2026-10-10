package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.require;

/** Internal raw comparison only; no credentials are returned in a reconciliation result. */
final class RecoveryReleaseDatabaseRows {
    private final JdbcTemplate jdbc;
    private long bytesRead;
    RecoveryReleaseDatabaseRows(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    ObjectNode read() {
        var tables=SubmissionJson.object();
        for(String name:RecoveryReleaseInventoryRows.tableNames()) tables.set(name,table(name));
        return tables;
    }
    private ObjectNode table(String name) {
        var table=SubmissionJson.object(); var columns=table.putArray("columns"); var rows=table.putArray("rows");
        jdbc.query("SELECT * FROM "+name+" LIMIT 65537 FOR UPDATE",rs->{
            var metadata=rs.getMetaData();
            for(int i=1;i<=metadata.getColumnCount();i++) columns.add(metadata.getColumnLabel(i));
            while(rs.next()) {
                require(rows.size()<65536,"RECOVERY_SCOPE_ROWS_LIMIT"); var row=rows.addArray();
                for(int i=1;i<=metadata.getColumnCount();i++) {
                    byte[] bytes=rs.getBytes(i);
                    if(bytes==null) row.addNull();
                    else {
                        bytesRead+=bytes.length; require(bytesRead<=16777216,"RECOVERY_SCOPE_BYTES_LIMIT");
                        row.add(Base64.getEncoder().encodeToString(bytes));
                    }
                }
            }
            return null;
        });
        table.set("rows",RecoveryReleaseRows.encode(columns,RecoveryReleaseRows.decode(table))); return table;
    }
}
