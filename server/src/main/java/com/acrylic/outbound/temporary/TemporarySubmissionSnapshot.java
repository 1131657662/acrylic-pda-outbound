package com.acrylic.outbound.temporary;

import com.acrylic.outbound.submissions.SubmissionSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Component
public class TemporarySubmissionSnapshot {
    private final JdbcTemplate jdbc;private final TemporarySubmissionPlan plans;
    public TemporarySubmissionSnapshot(JdbcTemplate jdbc,TemporarySubmissionPlan plans) { this.jdbc=jdbc;this.plans=plans; }
    public SubmissionSnapshot.Validated validate(TemporarySubmissionPlan.Plan plan,JsonNode body) {
        var inventory=new HashMap<String,SubmissionSnapshot.Bale>();
        jdbc.query("""
            SELECT BIN_TO_UUID(b.id),b.barcode,s.delivery_code,s.weight_g,s.raw_weight,s.model_snapshot,s.name_snapshot
            FROM inventory_snapshot_bale s INNER JOIN bale b ON b.id=s.bale_id WHERE s.snapshot_id=UUID_TO_BIN(?)
            """,rs->{var b=new SubmissionSnapshot.Bale(UUID.fromString(rs.getString(1)),rs.getString(2),rs.getString(3),rs.getLong(4),rs.getString(5),rs.getString(6),rs.getString(7));inventory.put(b.barcode(),b);},text(body,"inventorySnapshotId"));
        var availability=new HashMap<String,JsonNode>();plans.part(plan.authorization().id(),"availability").path("items").forEach(row->availability.put(text(row,"barcode"),row));
        var products=new TreeMap<String,SubmissionSnapshot.Total>();plan.lines().forEach(p->products.put(p.code(),new SubmissionSnapshot.Total(p.code(),p.count(),0,0)));
        var mapping=new HashMap<String,JsonNode>();plan.lines().forEach(p->mapping.put(p.code(),p.mapping()));
        var scans=new ArrayList<SubmissionSnapshot.Scan>();long weight=0;
        for(var scan:array(body,"scans")) {
            var b=inventory.get(text(scan,"barcode"));require(b!=null && products.containsKey(b.code()),"条码不属于原库存/计划");
            equal(b.model(),text(mapping.get(b.code()),"model"),"固定库存与产品计划型号不符");equal(b.name(),text(mapping.get(b.code()),"name"),"固定库存与产品计划名称不符");
            equal(uuid(scan,"baleId"),b.id(),"原包ID不符");equal(number(scan,"weightGrams"),b.weight(),"原重量不符");
            equal(text(scan,"rawWeight"),b.rawWeight(),"原始重量不符");equal(text(scan,"deliveryCode"),b.code(),"原代码不符");
            equal(text(scan,"model"),b.model(),"原型号不符");equal(text(scan,"name"),b.name(),"原名称不符");
            var available=availability.get(b.barcode());require(available!=null,"缺原可用基线");equal(number(scan,"expectedDispatchCycle"),number(available,"dispatchCycle"),"不得换用新周期");
            if(text(scan,"status").equals("ACTIVE")) {
                scans.add(new SubmissionSnapshot.Scan(uuid(scan,"captureId"),number(scan,"orderScanSeq"),number(scan,"expectedDispatchCycle"),b));
                var p=products.get(b.code());products.put(b.code(),new SubmissionSnapshot.Total(p.code(),p.planned(),p.count()+1,Math.addExact(p.weight(),b.weight())));weight=Math.addExact(weight,b.weight());
            }
        }
        require(products.values().stream().allMatch(p->p.count()<=p.planned()),"超出产品计划");equal(number(body,"expectedWeightGrams"),weight,"整单重量错误");
        boolean shortShipment=products.values().stream().anyMatch(p->p.count()<p.planned());String reason=body.path("shortShipmentReason").isNull()?"":text(body,"shortShipmentReason").strip();
        require(reason.length()<=500 && (!shortShipment || !reason.isBlank()),"短装须填写原因");
        return new SubmissionSnapshot.Validated(scans,new ArrayList<>(products.values()),weight,shortShipment);
    }
}
