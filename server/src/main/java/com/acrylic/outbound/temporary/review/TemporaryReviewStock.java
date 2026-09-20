package com.acrylic.outbound.temporary.review;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Reads current conflicts without reserving, releasing or publishing stock changes. */
@Component
public class TemporaryReviewStock {
    private final JdbcTemplate jdbc;
    public TemporaryReviewStock(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Map<String,Long> issues(JsonNode body, UUID submission) {
        var scans = new ArrayList<JsonNode>(); body.path("scans").forEach(row -> { if (row.path("status").asText().equals("ACTIVE")) scans.add(row); });
        var result = new TreeMap<String,Long>();
        for (int start = 0; start < scans.size(); start += 200) {
            var part = scans.subList(start, Math.min(start + 200, scans.size()));
            conflicts(part,submission).values().forEach(code->result.merge(code,1L,Long::sum));
        }
        return result;
    }
    public Map<String,String> conflicts(List<JsonNode> scans,UUID submission) {
        var active=scans.stream().filter(row->row.path("status").asText().equals("ACTIVE")).toList();
        var result=new HashMap<String,String>();if(active.isEmpty()) return result;
        var positions=positions(active);
        for(var scan:active) {
            String code=conflict(scan,positions.get(scan.path("baleId").asText()),submission);
            if(code!=null) result.put(scan.path("captureId").asText(),code);
        }
        return result;
    }
    private Map<String,Position> positions(List<JsonNode> scans) {
        String placeholders = String.join(",", Collections.nCopies(scans.size(), "?"));
        var rows = new HashMap<String,Position>();
        jdbc.query("""
            SELECT BIN_TO_UUID(b.id),b.dispatch_cycle,p.upstream_present,p.book_status,p.blocked_reason,
              s.delivery_code,s.weight_g,s.model_snapshot,s.name_snapshot,BIN_TO_UUID(c.submission_id),c.dispatch_cycle,c.state
            FROM bale b LEFT JOIN inventory_position p ON p.bale_id=b.id
            LEFT JOIN inventory_snapshot_bale s ON s.snapshot_id=p.last_seen_snapshot_id AND s.bale_id=b.id
            LEFT JOIN bale_commitment c ON c.bale_id=b.id WHERE b.id IN (
            """ + placeholders + ")", rs -> { rows.put(rs.getString(1), new Position(rs.getLong(2),rs.getBoolean(3),rs.getString(4),
                rs.getString(5),rs.getString(6),rs.getLong(7),rs.getString(8),rs.getString(9),rs.getString(10),rs.getLong(11),rs.getString(12))); },
            scans.stream().map(row -> Ids.bytes(UUID.fromString(row.path("baleId").asText()))).toArray());
        return rows;
    }
    private String conflict(JsonNode row, Position p, UUID submission) {
        if (p == null) return "INVENTORY_UNAVAILABLE";
        if (p.cycle() != row.path("expectedDispatchCycle").asLong()) return "DISPATCH_CYCLE_CONFLICT";
        if (p.owner() != null && !p.owner().equals(submission.toString())) return "BALE_ALREADY_COMMITTED";
        if (!p.present() || !"NOT_DEDUCTED".equals(p.book()) || p.blocked() != null) return "INVENTORY_UNAVAILABLE";
        if (!Objects.equals(p.code(), row.path("deliveryCode").asText()) || p.weight() != row.path("weightGrams").asLong()
            || !Objects.equals(p.model(),row.path("model").asText()) || !Objects.equals(p.name(),row.path("name").asText())) return "INVENTORY_BASELINE_CHANGED";
        if (p.owner() == null || p.reservedCycle() != p.cycle() || !"REVIEW_RESERVED".equals(p.state())) return "TEMPORARY_RESERVATION_RECHECK_REQUIRED";
        return null;
    }
    private record Position(long cycle,boolean present,String book,String blocked,String code,long weight,String model,String name,
                            String owner,long reservedCycle,String state) { }
}
