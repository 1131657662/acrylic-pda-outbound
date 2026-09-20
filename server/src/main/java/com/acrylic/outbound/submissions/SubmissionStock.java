package com.acrylic.outbound.submissions;

import com.acrylic.outbound.sync.BarcodeChanges;
import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Component
public class SubmissionStock {
    private final JdbcTemplate jdbc;
    public SubmissionStock(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public ArrayNode reserve(SubmissionStore.Head head,SubmissionSnapshot.Validated value) {
        var issues=object().putArray("issues");var sorted=value.scans().stream().sorted(Comparator.comparing(s->s.bale().id().toString())).toList();
        for(var scan:sorted) {
            UUID bale=scan.bale().id();long cycle=jdbc.queryForObject("SELECT dispatch_cycle FROM bale WHERE id=? FOR UPDATE",Long.class,Ids.bytes(bale));
            var positions=jdbc.query("""
                SELECT p.upstream_present,p.book_status,p.blocked_reason,s.delivery_code,s.weight_g,s.model_snapshot,s.name_snapshot
                FROM inventory_position p INNER JOIN inventory_snapshot_bale s ON s.snapshot_id=p.last_seen_snapshot_id AND s.bale_id=p.bale_id
                WHERE p.bale_id=? FOR UPDATE
                """,(rs,n)->new Position(rs.getBoolean(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getLong(5),rs.getString(6),rs.getString(7)),Ids.bytes(bale));
            var owners=jdbc.queryForList("SELECT BIN_TO_UUID(submission_id) FROM bale_commitment WHERE bale_id=? FOR UPDATE",String.class,Ids.bytes(bale));
            String conflict=cycle!=scan.cycle()?"DISPATCH_CYCLE_CONFLICT":positions.isEmpty()?"INVENTORY_UNAVAILABLE":
                !owners.isEmpty() && !head.control().id().toString().equals(owners.getFirst())?"BALE_ALREADY_COMMITTED":
                !positions.getFirst().present() || !positions.getFirst().book().equals("NOT_DEDUCTED") || positions.getFirst().blocked()!=null?"INVENTORY_UNAVAILABLE":
                !positions.getFirst().matches(scan.bale())?"INVENTORY_BASELINE_CHANGED":null;
            if(conflict!=null) { issues.add(object().put("code",conflict).put("barcode",scan.bale().barcode()));continue; }
            if(owners.isEmpty()) jdbc.update("INSERT INTO bale_commitment(bale_id,order_id,submission_id,dispatch_cycle,state,created_at) VALUES(?,?,?,?,'REVIEW_RESERVED',UTC_TIMESTAMP(3))",
                Ids.bytes(bale),Ids.bytes(head.control().order()),Ids.bytes(head.control().id()),scan.cycle());
        }
        for(var scan:sorted) BarcodeChanges.bale(jdbc,scan.bale().id());
        return issues;
    }
    private record Position(boolean present,String book,String blocked,String code,long weight,String model,String name) {
        boolean matches(SubmissionSnapshot.Bale bale) { return code.equals(bale.code()) && weight==bale.weight() && model.equals(bale.model()) && name.equals(bale.name()); }
    }
}
