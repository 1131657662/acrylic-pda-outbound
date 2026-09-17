package com.acrylic.outbound.submissions;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.pda.AssignmentStore;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Component
public class SubmissionSnapshot {
    private final JdbcTemplate jdbc;
    public SubmissionSnapshot(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Validated validate(AssignmentStore.View a,JsonNode body) {
        var rows=jdbc.query("""
            SELECT BIN_TO_UUID(s.bale_id),b.barcode,s.delivery_code,s.weight_g,s.raw_weight,s.model_snapshot,s.name_snapshot
            FROM inventory_snapshot_bale s INNER JOIN bale b ON b.id=s.bale_id WHERE s.snapshot_id=?
            """,(rs,n)->new Bale(UUID.fromString(rs.getString(1)),rs.getString(2),rs.getString(3),rs.getLong(4),rs.getString(5),rs.getString(6),rs.getString(7)),Ids.bytes(UUID.fromString(a.inventorySnapshotId())));
        var inventory=new HashMap<String,Bale>();rows.forEach(r->inventory.put(r.barcode(),r));
        var plan=jdbc.query("SELECT delivery_code,planned_count FROM order_line l INNER JOIN order_revision r ON r.id=l.revision_id WHERE r.order_id=? AND r.revision=? ORDER BY delivery_code",
            (rs,n)->new Product(rs.getString(1),rs.getLong(2)),Ids.bytes(UUID.fromString(a.orderId())),a.revision());
        var products=new TreeMap<String,Total>();plan.forEach(p->products.put(p.code(),new Total(p.code(),p.count(),0,0)));
        var active=new ArrayList<Scan>();long weight=0;
        for(var scan:array(body,"scans")) {
            var bale=inventory.get(text(scan,"barcode"));require(bale!=null,"条码不在原固定快照中");
            equal(uuid(scan,"baleId"),bale.id(),"包ID与原快照不一致");equal(number(scan,"weightGrams"),bale.weight(),"重量与原快照不一致");
            equal(text(scan,"rawWeight"),bale.rawWeight(),"原始重量不一致");equal(text(scan,"deliveryCode"),bale.code(),"产品代码不一致");
            equal(text(scan,"model"),bale.model(),"型号不一致");equal(text(scan,"name"),bale.name(),"名称不一致");require(products.containsKey(bale.code()),"产品不属于本车计划");
            if(text(scan,"status").equals("ACTIVE")) {
                active.add(new Scan(uuid(scan,"captureId"),number(scan,"orderScanSeq"),number(scan,"expectedDispatchCycle"),bale));
                var total=products.get(bale.code());products.put(bale.code(),new Total(total.code(),total.planned(),total.count()+1,Math.addExact(total.weight(),bale.weight())));
                weight=Math.addExact(weight,bale.weight());
            }
        }
        require(products.values().stream().allMatch(p->p.count()<=p.planned()),"产品超计划，不能入账");
        equal(number(body,"expectedWeightGrams"),weight,"整单重量与原快照不一致");
        boolean shortShipment=products.values().stream().anyMatch(p->p.count()<p.planned());
        String reason=body.path("shortShipmentReason").isNull()?"":text(body,"shortShipmentReason").strip();
        require(reason.length()<=500 && (!shortShipment || !reason.isBlank()),"短装必须填写1至500字原因");
        return new Validated(active,new ArrayList<>(products.values()),weight,shortShipment);
    }
    public record Product(String code,long count) { }
    public record Bale(UUID id,String barcode,String code,long weight,String rawWeight,String model,String name) { }
    public record Scan(UUID capture,long sequence,long cycle,Bale bale) { }
    public record Total(String code,long planned,long count,long weight) { }
    public record Validated(List<Scan> scans,List<Total> products,long weight,boolean shortShipment) { }
}
