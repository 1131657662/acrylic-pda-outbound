package com.acrylic.outbound.relay;

import com.acrylic.outbound.common.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** A full bounded set of possible associations, without inferring product identity from bare SKU. */
@Repository
public class RelayIndependentCandidateQuery {
    private final NamedParameterJdbcTemplate jdbc;private final RelayIndependentCandidateWork work;
    public RelayIndependentCandidateQuery(NamedParameterJdbcTemplate jdbc,RelayIndependentCandidateWork work) { this.jdbc=jdbc;this.work=work; }
    public ObjectNode read(RelayArchive.Bundle bundle) {
        var task=bundle.independent().path("task");var zone=ZoneId.of("Asia/Shanghai");
        var start=Instant.parse(text(task,"createdTrustedAt")).atZone(zone).toLocalDate().minusDays(7);
        var end=Instant.parse(text(bundle.closure(),"closedAt")).atZone(zone).toLocalDate().plusDays(7);
        var params=new MapSqlParameterSource("warehouse",Ids.bytes(bundle.original().warehouse()))
            .addValue("vehicle",text(task,"vehicleNo")).addValue("start",Timestamp.from(start.atStartOfDay(zone).toInstant()))
            .addValue("end",Timestamp.from(end.plusDays(1).atStartOfDay(zone).toInstant()));
        var heads=jdbc.query(select()+predicate(bundle.evidence().lines(),params)+" ORDER BY o.created_at DESC,o.id DESC LIMIT 10001",params,
            (rs,n)->object().put("id",rs.getString(1)).put("orderNo",rs.getString(2)).put("source",rs.getString(3))
                .put("status",rs.getString(4)).put("revision",rs.getString(5)).put("rowVersion",rs.getString(6)).put("revisionId",rs.getString(7))
                .put("vehicleNo",rs.getString(8)).put("containerNo",rs.getString(9)).put("plannedTotal",rs.getString(10))
                .put("createdAt",rs.getTimestamp(11).toInstant().toString()).put("inventorySnapshotId",rs.getString(12))
                .put("mappingRevision",rs.getString(13)).put("hasAssignment",rs.getBoolean(14)).put("hasPcWork",rs.getBoolean(15))
                .put("hasSubmission",rs.getBoolean(16)).put("hasResult",rs.getBoolean(17)));
        capacity(heads.size(),2000);products(heads);work.attach(heads);heads.forEach(this::blockers);
        var result=object().put("fromDate",start.toString()).put("throughDate",end.toString()).put("total",Integer.toString(heads.size()));
        var items=result.putArray("items");heads.forEach(items::add);return result;
    }
    private String predicate(List<JsonNode> lines,MapSqlParameterSource params) {
        var alternatives=new ArrayList<String>();int index=0;
        params.addValue("codes",lines.stream().map(line->text(line,"deliveryCode")).toList());
        for(var line:lines) {
            alternatives.add("(BINARY l.model_snapshot=BINARY :model"+index+" AND BINARY l.name_snapshot=BINARY :name"+index+")");
            params.addValue("model"+index,text(line,"model")).addValue("name"+index,text(line,"name"));index++;
        }
        return " AND (BINARY r.vehicle_no=BINARY :vehicle OR EXISTS(SELECT 1 FROM order_line l WHERE l.revision_id=r.id AND (l.delivery_code IN (:codes) OR "
            +String.join(" OR ",alternatives)+")))";
    }
    private String select() {
        return """
            SELECT BIN_TO_UUID(o.id),o.order_no,o.source,o.status,o.current_revision,o.row_version,BIN_TO_UUID(r.id),
              r.vehicle_no,r.container_no,r.planned_total,o.created_at,BIN_TO_UUID(r.inventory_snapshot_id),r.mapping_revision,
              EXISTS(SELECT 1 FROM order_assignment a WHERE a.order_id=o.id),
              EXISTS(SELECT 1 FROM pc_work p WHERE p.order_id=o.id),
              EXISTS(SELECT 1 FROM submission_scope c INNER JOIN submission s ON s.scope_id=c.id
                WHERE c.source_order_id=o.id OR c.canonical_order_id=o.id),
              EXISTS(SELECT 1 FROM order_result_version v WHERE v.order_id=o.id)
            FROM outbound_order o INNER JOIN order_revision r ON r.order_id=o.id AND r.revision=o.current_revision
            WHERE o.warehouse_id=:warehouse AND o.created_at>=:start AND o.created_at<:end
            """;
    }
    private void products(List<ObjectNode> heads) {
        int[] total={0};
        for(int offset=0;offset<heads.size();offset+=500) {
            var chunk=heads.subList(offset,Math.min(offset+500,heads.size()));var byRevision=new HashMap<String,ObjectNode>();
            chunk.forEach(head->{head.putArray("products");byRevision.put(text(head,"revisionId"),head);});
            jdbc.query("""
                SELECT BIN_TO_UUID(revision_id),delivery_code,BIN_TO_UUID(product_id),model_snapshot,name_snapshot,planned_count
                FROM order_line WHERE revision_id IN (:ids) ORDER BY revision_id,delivery_code
                """,new MapSqlParameterSource("ids",chunk.stream().map(head->Ids.bytes(uuid(head,"revisionId"))).toList()),
                (org.springframework.jdbc.core.RowCallbackHandler)rs->{
                    capacity(++total[0],20000);byRevision.get(rs.getString(1)).withArray("products").add(object()
                        .put("deliveryCode",rs.getString(2)).put("productId",rs.getString(3)).put("model",rs.getString(4))
                        .put("name",rs.getString(5)).put("plannedCount",rs.getString(6)));
                });
        }
    }
    private void blockers(ObjectNode head) {
        var blockers=head.putArray("blockers");
        if(!Set.of("DRAFT","READY").contains(text(head,"status")))blockers.add("ORDER_STATE");
        for(String field:List.of("hasAssignment","hasPcWork","hasSubmission","hasResult"))if(head.path(field).asBoolean())blockers.add(field);
        head.put("associationAuthorized",false);
    }
    static void capacity(int actual,int maximum) {
        if(actual>maximum)throw new BusinessException(409,"INDEPENDENT_REVIEW_CAPACITY","核对依据超过单次资源上限，未生成完整核对结论；原件保留");
    }
}
