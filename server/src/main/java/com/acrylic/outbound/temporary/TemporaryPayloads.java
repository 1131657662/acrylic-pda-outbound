package com.acrylic.outbound.temporary;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.inventory.master.MappingStore;
import com.acrylic.outbound.inventory.parser.ParsedBale;
import com.acrylic.outbound.pda.PreparationArchive;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class TemporaryPayloads {
    private final JdbcTemplate jdbc;private final JsonText json;private final MappingStore mappings;private final TemporaryAvailability availability;
    public TemporaryPayloads(JdbcTemplate jdbc,JsonText json,MappingStore mappings,TemporaryAvailability availability) {
        this.jdbc=jdbc;this.json=json;this.mappings=mappings;this.availability=availability;
    }
    public List<PreparationArchive> build(TemporaryBaseline.View baseline,String epoch) {
        UUID warehouse=UUID.fromString(baseline.warehouseId());var inventory=inventory(baseline);
        var mapping=mappings.current(warehouse);var state=availability.read(warehouse,epoch);
        if(state.items().size()<baseline.inventoryRows()) throw new BusinessException(409,"PREPARATION_INCONSISTENT","库存可用状态基线不完整");
        return List.of(inventory,PreparationArchive.pack("mapping",json.encode(mapping),mapping.size()),
            PreparationArchive.pack("policy",json.encode(baseline.policy()),1),
            PreparationArchive.pack("rules",json.encode(new Rules(1,"[A-Z0-9]{11}",3,"INTEGER_GRAMS",3,"AUTO_SCAN_MANUAL_CONFIRM")),1),
            PreparationArchive.pack("availability",json.encode(state),state.items().size()));
    }
    private PreparationArchive inventory(TemporaryBaseline.View baseline) {
        var rows=jdbc.query("""
            SELECT s.model_snapshot,s.name_snapshot,b.barcode,s.delivery_code,s.raw_weight,s.weight_g
            FROM inventory_snapshot_bale s INNER JOIN bale b ON b.id=s.bale_id
            WHERE s.snapshot_id=? ORDER BY b.barcode
            """,(rs,n)->new ParsedBale(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6)),
            Ids.bytes(UUID.fromString(baseline.inventorySnapshotId())));
        var part=PreparationArchive.pack("inventory",json.encode(rows),rows.size());
        if(rows.size()!=baseline.inventoryRows() || !part.descriptor().sha256().equals(baseline.inventorySha256()))
            throw new BusinessException(409,"PREPARATION_INCONSISTENT","固定库存摘要或行数不一致，请后台核查");
        return part;
    }
    private record Rules(int version,String barcodePattern,int deliveryCodePrefixLength,String weightUnit,int maxWeightDecimals,String scanMode) { }
}
