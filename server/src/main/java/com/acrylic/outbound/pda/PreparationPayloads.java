package com.acrylic.outbound.pda;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.inventory.parser.ParsedBale;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PreparationPayloads {
    private final JdbcTemplate jdbc;private final JsonText json;
    public PreparationPayloads(JdbcTemplate jdbc,JsonText json) { this.jdbc=jdbc;this.json=json; }
    public Data read(AssignmentStore.View assignment) {
        byte[] snapshot=Ids.bytes(UUID.fromString(assignment.inventorySnapshotId()));
        String plan=jdbc.queryForObject("SELECT snapshot_json FROM order_publication WHERE order_id=? AND revision=?",
            String.class,Ids.bytes(UUID.fromString(assignment.orderId())),assignment.revision());
        var rows=jdbc.query("""
            SELECT s.model_snapshot,s.name_snapshot,b.barcode,s.delivery_code,s.raw_weight,s.weight_g,
              BIN_TO_UUID(b.id),b.dispatch_cycle,p.upstream_present,p.book_status,p.blocked_reason,c.bale_id
            FROM inventory_snapshot_bale s INNER JOIN bale b ON b.id=s.bale_id
            INNER JOIN inventory_position p ON p.bale_id=b.id LEFT JOIN bale_commitment c ON c.bale_id=b.id
            WHERE s.snapshot_id=? ORDER BY b.barcode
            """,(rs,n)->new Row(new ParsedBale(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6)),
                new Availability(rs.getString(7),rs.getString(3),rs.getString(8),rs.getBoolean(9) && rs.getString(10).equals("NOT_DEDUCTED")
                    && rs.getString(11)==null && rs.getBytes(12)==null)),snapshot);
        int expected=jdbc.queryForObject("SELECT row_count FROM inventory_snapshot WHERE id=?",Integer.class,snapshot);
        if(rows.size()!=expected) throw new BusinessException(409,"PREPARATION_INCONSISTENT","库存固定版本不完整，需后台核查");
        return new Data(plan,json.encode(rows.stream().map(Row::bale).toList()),json.encode(rows.stream().map(Row::availability).toList()),expected);
    }
    private record Row(ParsedBale bale,Availability availability) { }
    private record Availability(String baleId,String barcode,String dispatchCycle,boolean available) { }
    public record Data(String plan,String inventory,String availability,int rows) { }
}
