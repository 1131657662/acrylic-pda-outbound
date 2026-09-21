package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.Ids;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Conservative INTERNAL_TEST_ONLY eligibility, not a future confirmation/disposition business policy. */
@Component
public class CommandSyntheticEligibility {
    private final JdbcTemplate jdbc;
    public CommandSyntheticEligibility(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public void require(CommandContext source) {
        if(!reasons(source).isEmpty())throw CommandErrors.stale();
    }
    public List<String> reasons(CommandContext source) {
        var rows=jdbc.queryForList("""
            SELECT u.status user_status,u.auth_version,d.status device_status,d.row_version,d.temporary_order_enabled,
             a.expires_at,k.authorization_id acknowledgement_id,
             EXISTS(SELECT 1 FROM user_warehouse w WHERE w.user_id=u.id AND w.warehouse_id=a.warehouse_id) warehouse_access
            FROM temporary_authorization a JOIN app_user u ON u.id=a.user_id JOIN device d ON d.id=a.device_id
            LEFT JOIN temporary_preparation_ack k ON k.authorization_id=a.id WHERE a.id=?
            """,Ids.bytes(source.id("authorizationId")));
        if(rows.size()!=1)return List.of("SOURCE_ELIGIBILITY_UNAVAILABLE");
        var row=rows.getFirst();var reasons=new ArrayList<String>();
        if(!"ACTIVE".equals(row.get("user_status")))reasons.add("ORIGINAL_OPERATOR_INACTIVE");
        if(!source.text("authorizationUserVersion").equals(row.get("auth_version").toString()))reasons.add("ORIGINAL_OPERATOR_VERSION_CHANGED");
        if(!"ACTIVE".equals(row.get("device_status")))reasons.add("ORIGINAL_DEVICE_INACTIVE");
        if(!source.text("authorizationDeviceVersion").equals(row.get("row_version").toString()))reasons.add("ORIGINAL_DEVICE_VERSION_CHANGED");
        if(!truth(row.get("temporary_order_enabled")))reasons.add("ORIGINAL_TEMPORARY_ADMISSION_DISABLED");
        if(!truth(row.get("warehouse_access")))reasons.add("ORIGINAL_OPERATOR_WAREHOUSE_REMOVED");
        if(!CommandRecord.instant(row.get("expires_at")).isAfter(Instant.now()))reasons.add("ORIGINAL_AUTHORIZATION_EXPIRED");
        if(row.get("acknowledgement_id")==null)reasons.add("ORIGINAL_PREPARATION_UNACKNOWLEDGED");
        return List.copyOf(reasons);
    }
    private boolean truth(Object value) { return Boolean.TRUE.equals(value) || value instanceof Number n && n.intValue()==1; }
}
