package com.acrylic.outbound.temporary.review;

import com.acrylic.outbound.auth.IdentityStore;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.temporary.TemporaryStore;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.object;

@Component
public class TemporaryReviewEligibility {
    private final JdbcTemplate jdbc; private final TemporaryStore store; private final IdentityStore identities;
    public TemporaryReviewEligibility(JdbcTemplate jdbc,TemporaryStore store,IdentityStore identities) {
        this.jdbc=jdbc; this.store=store; this.identities=identities;
    }
    public ArrayNode current(TemporaryStore.Stored original, ServerState.State state) {
        return current(original,state,Instant.now());
    }
    public ArrayNode current(TemporaryStore.Stored original, ServerState.State state,Instant evaluatedAt) {
        var issues=object().putArray("issues");
        add(issues,"TEMPORARY_ORDER_CONFIRMATION_REQUIRED",true);
        add(issues,"TEMPORARY_AUTH_EXPIRED",!original.expiresAt().isAfter(evaluatedAt));
        add(issues,"TEMPORARY_ACTIVATION_UNACKNOWLEDGED",store.ack(original)==null);
        var user=jdbc.queryForObject("SELECT status,auth_version FROM app_user WHERE id=?",
            (rs,n)->new Version(rs.getString(1),rs.getString(2)),Ids.bytes(original.userId()));
        var device=jdbc.queryForObject("SELECT status,row_version,temporary_order_enabled FROM device WHERE id=?",
            (rs,n)->new Device(rs.getString(1),rs.getString(2),rs.getBoolean(3)),Ids.bytes(original.deviceId()));
        add(issues,"TEMPORARY_AUTH_VERSION_CHANGED",!original.userVersion().equals(user.version()) || !original.deviceVersion().equals(device.version()));
        add(issues,"TEMPORARY_OPERATOR_DISABLED",!user.status().equals("ACTIVE"));
        add(issues,"TEMPORARY_DEVICE_DISABLED",!device.status().equals("ACTIVE"));
        add(issues,"TEMPORARY_ADMISSION_DISABLED",!device.enabled());
        add(issues,"TEMPORARY_OPERATOR_PERMISSION_CHANGED",!identities.permissions(original.userId()).containsAll(List.of("temporary_order:create","pda:scan","pda:submit")));
        add(issues,"TEMPORARY_OPERATOR_WAREHOUSE_CHANGED",identities.warehouses(original.userId()).stream().noneMatch(w->w.id().equals(original.warehouseId())));
        add(issues,"TEMPORARY_RECOVERY_REQUIRED",!state.epoch().equals(original.recoveryEpoch()));
        add(issues,"SERVER_RECOVERY_REQUIRED",!state.mode().equals("NORMAL"));
        return issues;
    }
    static void add(ArrayNode issues,String code,boolean condition) { if(condition) issues.add(object().put("code",code).put("count","1")); }
    private record Version(String status,String version) { }
    private record Device(String status,String version,boolean enabled) { }
}
