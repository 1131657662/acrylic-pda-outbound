package com.acrylic.outbound.recall;

import com.acrylic.outbound.sync.BarcodeChanges;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionSnapshot;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Component
public class RecoveryStock {
    private final JdbcTemplate jdbc;
    public RecoveryStock(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public ArrayNode reserve(RecallStore.Head h,UUID packageId,SubmissionSnapshot.Validated value) {
        var issues=object().putArray("issues");
        for(var scan:value.scans().stream().sorted(Comparator.comparing(s->s.bale().id().toString())).toList()) {
            byte[] id=Ids.bytes(scan.bale().id());long cycle=jdbc.queryForObject("SELECT dispatch_cycle FROM bale WHERE id=? FOR UPDATE",Long.class,id);
            var owner=jdbc.queryForList("SELECT IF(state IN ('QUARANTINED','SOURCE_RESERVED'),'FOREIGN_OWNER',BIN_TO_UUID(order_id)) FROM bale_commitment WHERE bale_id=? FOR UPDATE",String.class,id);
            if(cycle!=scan.cycle()) issues.add(object().put("code","DISPATCH_CYCLE_CONFLICT").put("barcode",scan.bale().barcode()));
            if(!owner.isEmpty() && !h.order().toString().equals(owner.getFirst())) {
                issues.add(object().put("code","BALE_ALREADY_COMMITTED").put("barcode",scan.bale().barcode()));continue;
            }
            // Preserve physical evidence even when upstream/book/cycle is conflicted; recovery never permits dispatch.
            if(owner.isEmpty()) jdbc.update("""
                INSERT INTO bale_commitment(bale_id,order_id,dispatch_cycle,state,created_at,recovery_package_id)
                VALUES(?,?,?,'RECOVERY_RESERVED',UTC_TIMESTAMP(3),?)
                """,id,Ids.bytes(h.order()),scan.cycle(),Ids.bytes(packageId));
        }
        for(var scan:value.scans()) BarcodeChanges.bale(jdbc,scan.bale().id());
        return issues;
    }
}
