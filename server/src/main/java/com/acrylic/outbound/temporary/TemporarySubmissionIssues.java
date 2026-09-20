package com.acrylic.outbound.temporary;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Component
public class TemporarySubmissionIssues {
    private final JdbcTemplate jdbc;private final TemporaryStore store;
    public TemporarySubmissionIssues(JdbcTemplate jdbc,TemporaryStore store) { this.jdbc=jdbc;this.store=store; }
    public void append(TemporaryStore.Stored original,ArrayNode issues) {
        issues.add(object().put("code","TEMPORARY_ORDER_CONFIRMATION_REQUIRED"));
        if(!original.expiresAt().isAfter(Instant.now()))issues.add(object().put("code","TEMPORARY_AUTH_EXPIRED"));
        if(store.ack(original)==null)issues.add(object().put("code","TEMPORARY_ACTIVATION_UNACKNOWLEDGED"));
        String user=jdbc.queryForObject("SELECT auth_version FROM app_user WHERE id=?",String.class,Ids.bytes(original.userId()));
        String device=jdbc.queryForObject("SELECT row_version FROM device WHERE id=?",String.class,Ids.bytes(original.deviceId()));
        if(!original.userVersion().equals(user) || !original.deviceVersion().equals(device))issues.add(object().put("code","TEMPORARY_AUTH_VERSION_CHANGED"));
        if(Boolean.FALSE.equals(jdbc.queryForObject("SELECT temporary_order_enabled FROM device WHERE id=?",Boolean.class,Ids.bytes(original.deviceId()))))
            issues.add(object().put("code","TEMPORARY_ADMISSION_DISABLED"));
    }
}
