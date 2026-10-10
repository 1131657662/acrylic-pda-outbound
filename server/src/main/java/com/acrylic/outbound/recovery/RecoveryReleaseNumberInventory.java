package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Digest-only global original number/target mapping; cross-warehouse business rows stay private. */
final class RecoveryReleaseNumberInventory {
    static ObjectNode capture(JdbcTemplate jdbc) {
        var rows=new ArrayList<JsonNode>();
        jdbc.query("SELECT id,order_no FROM outbound_order LIMIT 65537 FOR UPDATE",rs->{
            require(rows.size()<65536,"RECOVERY_NUMBER_INVENTORY_LIMIT");
            var row=SubmissionJson.object().putArray("row");
            row.add(Base64.getEncoder().encodeToString(rs.getBytes(1)));
            row.add(Base64.getEncoder().encodeToString(rs.getBytes(2))); rows.add(row);
        });
        return digest(rows);
    }

    static ObjectNode original(JsonNode snapshot) {
        var table=snapshot.path("tables").path("outbound_order"); var names=new ArrayList<String>();
        table.path("columns").forEach(column->names.add(column.asText()));
        require(names.containsAll(List.of("id","order_no")),"RECOVERY_NUMBER_ORDERS");
        var rows=new ArrayList<JsonNode>();
        for(var original:table.path("rows")) {
            var row=SubmissionJson.object().putArray("row");
            row.add(original.path(names.indexOf("id")).deepCopy()); row.add(original.path(names.indexOf("order_no")).deepCopy()); rows.add(row);
        }
        return digest(rows);
    }

    private static ObjectNode digest(List<JsonNode> rows) {
        rows.sort(Comparator.comparing(Object::toString)); var ordered=SubmissionJson.object().putArray("rows"); rows.forEach(ordered::add);
        return SubmissionJson.object().put("rowCount",Integer.toString(rows.size()))
            .put("rowsSha256",sha256(RecoveryTemporaryEvidenceMaterial.encode(ordered)));
    }
    private RecoveryReleaseNumberInventory() { }
}
