package com.acrylic.outbound.relay;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Current server facts remain separate from signed bare-SKU claims. No reservation is written. */
@Repository
public class RelayIndependentInventory {
    private final NamedParameterJdbcTemplate jdbc;
    public RelayIndependentInventory(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }
    public List<ObjectNode> read(RelayArchive.Bundle bundle) {
        var active=bundle.evidence().active();var result=new ArrayList<ObjectNode>();
        RelayIndependentCandidateQuery.capacity(active.size(),10000);
        for(int offset=0;offset<active.size();offset+=500) {
            var chunk=active.subList(offset,Math.min(offset+500,active.size()));var originals=new HashMap<String,JsonNode>();
            chunk.forEach(scan->originals.put(text(scan,"barcode"),scan));
            var rows=jdbc.query(sql(),new MapSqlParameterSource("warehouse",Ids.bytes(bundle.original().warehouse()))
                .addValue("barcodes",List.copyOf(originals.keySet())),(rs,n)->object().put("barcode",rs.getString(1)).put("baleId",rs.getString(2))
                    .put("warehouseId",rs.getString(3)).put("dispatchCycle",rs.getString(4)).put("snapshotId",rs.getString(5))
                    .put("positionSnapshotId",rs.getString(6)).put("upstreamPresent",rs.getBoolean(7)).put("bookStatus",rs.getString(8))
                    .put("blockedReason",rs.getString(9)).put("positionVersion",rs.getString(10)).put("deliveryCode",rs.getString(11))
                    .put("model",rs.getString(12)).put("name",rs.getString(13)).put("weightGrams",rs.getString(14))
                    .put("commitmentState",rs.getString(15)).put("commitmentOrderId",rs.getString(16)).put("commitmentCycle",rs.getString(17)));
            var indexed=new HashMap<String,ObjectNode>();rows.forEach(row->indexed.put(text(row,"barcode"),row));
            for(var scan:chunk)result.add(compare(scan,indexed.get(text(scan,"barcode")),bundle.original().warehouse().toString()));
        }
        return List.copyOf(result);
    }
    private String sql() {
        return """
            SELECT b.barcode,BIN_TO_UUID(b.id),BIN_TO_UUID(b.warehouse_id),b.dispatch_cycle,BIN_TO_UUID(w.active_inventory_snapshot_id),
              BIN_TO_UUID(p.latest_snapshot_id),p.upstream_present,p.book_status,p.blocked_reason,p.row_version,
              s.delivery_code,s.model_snapshot,s.name_snapshot,s.weight_g,k.state,BIN_TO_UUID(k.order_id),k.dispatch_cycle
            FROM bale b CROSS JOIN warehouse w LEFT JOIN inventory_position p ON p.bale_id=b.id
            LEFT JOIN inventory_snapshot_bale s ON s.bale_id=b.id AND s.snapshot_id=w.active_inventory_snapshot_id
            LEFT JOIN bale_commitment k ON k.bale_id=b.id
            WHERE w.id=:warehouse AND b.warehouse_id=:warehouse AND b.barcode IN (:barcodes) ORDER BY b.barcode
            """;
    }
    static ObjectNode compare(JsonNode scan,ObjectNode server,String warehouse) {
        var result=object().put("captureId",text(scan,"captureId")).put("sequence",text(scan,"sequence")).put("barcode",text(scan,"barcode"));
        result.set("original",scan);result.set("server",server);var blockers=result.putArray("blockers");
        if(server==null) { blockers.add("BALE_UNKNOWN");return result; }
        if(!warehouse.equals(text(server,"warehouseId")))blockers.add("OTHER_WAREHOUSE");
        if(server.path("positionVersion").isNull())blockers.add("POSITION_MISSING");
        if(!server.path("upstreamPresent").asBoolean())blockers.add("UPSTREAM_ABSENT");
        if(!server.path("bookStatus").asText().equals("NOT_DEDUCTED"))blockers.add("BOOK_UNAVAILABLE");
        if(!server.path("blockedReason").isNull())blockers.add("INVENTORY_BLOCKED");
        if(!server.path("commitmentState").isNull())blockers.add("COMMITMENT_EXISTS");
        if(server.path("snapshotId").isNull() || !server.path("snapshotId").equals(server.path("positionSnapshotId")))blockers.add("SNAPSHOT_CHANGED");
        for(String field:List.of("deliveryCode","model","name","weightGrams"))if(!scan.path(field).equals(server.path(field)))blockers.add("SKU_"+field.toUpperCase(Locale.ROOT)+"_MISMATCH");
        return result;
    }
}
