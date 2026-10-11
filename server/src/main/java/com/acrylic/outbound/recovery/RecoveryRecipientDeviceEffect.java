package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static com.acrylic.outbound.recovery.RecoveryReleaseRows.*;

/** All device columns are accounted for; no extra column, session or ordinary TEMP permission is tolerated. */
final class RecoveryRecipientDeviceEffect {
    static JsonNode before(JsonNode tables,JsonNode body) {
        ObjectNode copy=tables.deepCopy(); var table=(ObjectNode)copy.path("device");
        var rows=decode(table); var recipient=body.path("recipient");
        var added=rows.stream().filter(row->Objects.equals(row.get("id"),id(text(recipient,"deviceId")))).toList();
        require(added.size()==1,"RECOVERY_RECIPIENT_DEVICE_EFFECT_REQUIRED"); exact(added.getFirst(),body);
        rows.remove(added.getFirst()); table.set("rows",encode(table.path("columns"),rows));
        if(table.has("rowCount")) table.put("rowCount",Integer.toString(rows.size())); return copy;
    }
    private static void exact(Map<String,String> row,JsonNode body) {
        require(row.keySet().equals(Set.of("id","warehouse_id","device_code","name","public_key","key_sha256","status",
            "row_version","created_by","created_at","last_seen_at","temporary_order_enabled")),"RECOVERY_RECIPIENT_DEVICE_COLUMNS");
        var recipient=body.path("recipient");
        RecoveryReleaseRows.uuid(row,"id",text(recipient,"deviceId"));
        RecoveryReleaseRows.uuid(row,"warehouse_id",text(body,"warehouseId"));
        RecoveryReleaseRows.uuid(row,"created_by",text(body,"registrarId"));
        eq(row,"device_code",text(recipient,"deviceCode")); eq(row,"name",text(recipient,"name"));
        eq(row,"public_key",text(recipient,"publicKeyDer")); eq(row,"key_sha256",text(recipient,"keySha256"));
        eq(row,"status","ACTIVE"); eq(row,"row_version","1"); eq(row,"temporary_order_enabled","0"); eq(row,"last_seen_at",null);
        require(time(row,"created_at").equals(instant(body,"requestedAt")),"RECOVERY_RECIPIENT_DEVICE_TIME");
    }
    private RecoveryRecipientDeviceEffect() { }
}
