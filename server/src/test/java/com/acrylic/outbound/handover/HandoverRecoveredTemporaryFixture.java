package com.acrylic.outbound.handover;

import com.acrylic.outbound.auth.*;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.e2averify.E2aMigrationSnapshot;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.WebApplicationContext;

/** Synthetic authenticated supervisors; source IDs, payload, STOP and verification are the actual restored originals. */
final class HandoverRecoveredTemporaryFixture {
    final JdbcTemplate jdbc;
    final HandoverCommandService commands;
    final HandoverStore cases;
    final HandoverRecoveredTemporaryBinding binding;
    final E2aMigrationSnapshot snapshots;
    final Path directory;
    final SessionUser requester,supervisor,unprivileged;

    HandoverRecoveredTemporaryFixture(WebApplicationContext app,Path attempt,HandoverRecoveredTemporaryBinding binding) throws Exception {
        this.binding=binding; jdbc=app.getBean(JdbcTemplate.class); commands=app.getBean(HandoverCommandService.class); cases=app.getBean(HandoverStore.class);
        directory=Files.createDirectory(attempt.resolve("recovered-fault")); snapshots=new E2aMigrationSnapshot(jdbc,attempt,attempt.resolve("storage"));
        requester=user(app,"SUPERVISOR"); supervisor=user(app,"SUPERVISOR"); unprivileged=user(app,"WAREHOUSE_ADMIN");
    }

    private SessionUser user(WebApplicationContext app,String role) {
        UUID id=Ids.next(); String username="recovered-"+id;
        String hash=app.getBean(PasswordEncoder.class).encode("Test-password-123");
        jdbc.update("INSERT INTO app_user(id,username,display_name,password_hash,created_at) VALUES(?,?,?,?,UTC_TIMESTAMP(3))",Ids.bytes(id),username,"恢复隔离主管",hash);
        jdbc.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code=?",Ids.bytes(id),role);
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)",Ids.bytes(id),Ids.bytes(binding.request().evidence().expected().warehouseId()));
        return app.getBean(IdentityStore.class).loadUserByUsername(username);
    }

    ObjectNode request() {
        var source=binding.request().evidence().expected();
        var command=base("REQUEST").put("scopeId",source.scopeId().toString());
        command.set("expected",HandoverCommand.Expected.forRequest(cases.scopeFacts(source.scopeId()),requester.authVersion())); return command;
    }

    ObjectNode fault(UUID caseId) {
        var command=base("FAULT_FENCE").put("caseId",caseId.toString()).put("sourceDeviceId",binding.request().evidence().expected().deviceId().toString());
        command.set("expected",HandoverCommand.Expected.forCase(cases.caseFacts(caseId),supervisor.authVersion()));
        command.putObject("physicalControl").put("deviceState","POWERED_OFF_IN_CUSTODY").put("controlledAt",Instant.now().minusMillis(1).toString())
            .put("deviceLocation","隔离开发设备柜").put("goodsLocation","隔离开发车辆").put("unresolvedInputCount","0");
        var facts=binding.nativeFacts().path("archive"); var reference=command.putObject("archivedEvidence");
        for(String field:List.of("sourceEvidenceSetId","verificationId","setSha256","captureHighWatermark","auditThroughSeq","orderScanHighWatermark"))
            reference.set(field,facts.path(field));
        return command;
    }

    private ObjectNode base(String kind) {
        return SubmissionJson.object().put("commandVersion",1).put("commandType",kind).put("commandId",UUID.randomUUID().toString())
            .put("warehouseId",binding.request().evidence().expected().warehouseId().toString())
            .put("transportRecoveryEpoch",jdbc.queryForObject("SELECT CAST(recovery_epoch AS CHAR) FROM server_state WHERE id=1",String.class));
    }

    byte[] execute(SessionUser actor,JsonNode command) {
        authenticate(actor);
        try { return commands.executeRecoveredTemporary(HandoverReceiptCodec.bytes(command),"received-fault",binding); }
        finally { SecurityContextHolder.clearContext(); }
    }
    static void authenticate(SessionUser actor) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,"",actor.getAuthorities()));
    }
    JsonNode snapshot(String name) throws Exception {
        var value=snapshots.capture(); Files.writeString(directory.resolve(name+".json"),value.toPrettyString()); return value;
    }
}
