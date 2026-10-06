package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.JsonText;
import com.acrylic.outbound.inventory.master.MappingStore;
import com.acrylic.outbound.inventory.parser.ParsedBale;
import com.acrylic.outbound.pda.PreparationArchive;
import com.acrylic.outbound.policies.PolicyStore;
import com.acrylic.outbound.temporary.*;
import java.util.List;

/** Real preparation serializer over synthetic SQL inventory; all packaged parts and digests are coherent. */
final class TemporaryRoundIntakeParts {
    private final RetentionDatabaseFixture f;
    TemporaryRoundIntakeParts(RetentionDatabaseFixture f) { this.f=f; }
    List<PreparationArchive> build() {
        var json=new JsonText(new com.fasterxml.jackson.databind.ObjectMapper());
        var rows=f.jdbc.query("""
            SELECT s.model_snapshot,s.name_snapshot,b.barcode,s.delivery_code,s.raw_weight,s.weight_g
            FROM inventory_snapshot_bale s JOIN bale b ON b.id=s.bale_id WHERE s.snapshot_id=UUID_TO_BIN(?) ORDER BY b.barcode
            """,(rs,n)->new ParsedBale(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6)),f.seed.get("intakeSnapshot"));
        String digest=json.digest(rows);
        f.jdbc.update("UPDATE inventory_snapshot SET row_count=?,total_weight_g=?,manifest_sha256=? WHERE id=UUID_TO_BIN(?)",
            rows.size(),rows.size()*1000,digest,f.seed.get("intakeSnapshot"));
        var policy=new PolicyStore(f.jdbc).current(f.id("warehouseId"));
        var base=new TemporaryBaseline.View(f.seed.get("warehouseId"),f.seed.get("intakeSnapshot"),"1","1",rows.size(),digest,policy);
        return new TemporaryPayloads(f.jdbc,json,new MappingStore(f.jdbc),new TemporaryAvailability(f.jdbc)).build(base,"1");
    }
}
