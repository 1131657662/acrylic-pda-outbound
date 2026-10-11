package com.acrylic.outbound.recovery;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.springframework.web.context.WebApplicationContext;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** New isolated target, original source, existing PREPARE dependencies and a real receiving login. */
final class RecoveryRecipientSessionFixture {
    final RecoveryQualificationFixture fixture;
    final RecoveryTemporaryQualification qualification;
    final RecoveryRecipientSessionHttp.Result login;
    final ObjectNode body;
    RecoveryRecipientSessionFixture(WebApplicationContext app,Path attempt,Path original) throws Exception {
        fixture=new RecoveryQualificationFixture(app,attempt,original); var signed=fixture.sign(fixture.body);
        qualification=RecoveryTemporaryQualification.verify(signed,fixture.admission); fixture.issue(qualification); fixture.save(attempt,signed);
        byte[] prepared=RecoveryReceivedPreparationChecks.prepare(fixture,qualification,RecoveryReceivedPreparationChecks.command(fixture));
        Files.write(attempt.resolve("preparation-receipt.json"),prepared);
        var snapshots=new com.acrylic.outbound.e2averify.E2aMigrationSnapshot(fixture.enrollment.jdbc,attempt,attempt.resolve("storage"));
        Files.writeString(attempt.resolve("session-login-before.json"),snapshots.capture().toPrettyString());
        login=new RecoveryRecipientSessionHttp().login(fixture,attempt);
        Files.writeString(attempt.resolve("session-login-after.json"),snapshots.capture().toPrettyString()); body=statement(prepared);
    }
    private ObjectNode statement(byte[] prepared) {
        var q=qualification.facts(); var result=SubmissionJson.object().put("formatVersion",1).put("purpose","RECOVERED_TEMP_RECIPIENT_SESSION")
            .put("commandId",Ids.next().toString()).put("qualificationSha256",qualification.sha256()).put("preparedReceiptSha256",sha256(prepared));
        for(String field:List.of("qualificationId","caseId","scopeId","warehouseId","control","recipient","sourceManifestSha256","admissionSha256")) result.set(field,q.path(field));
        result.putObject("login").put("sessionId",login.tokens().path("sessionId").asText()).put("challengeId",login.challenge().path("challengeId").asText())
            .put("username",fixture.enrollment.recipient.username()).put("requestId",login.requestId()).put("deviceProof",login.deviceProof());
        var effects=result.putObject("effects"); effects.set("session",row("pda_session","id",login.tokens().path("sessionId").asText()));
        effects.set("challenge",row("device_challenge","id",login.challenge().path("challengeId").asText()));
        effects.set("audit",row("operation_event","request_id",login.requestId()));
        byte[] seen=fixture.enrollment.jdbc.queryForObject("SELECT last_seen_at FROM device WHERE id=?",byte[].class,
            Ids.bytes(uuid(q.path("recipient"),"deviceId"))); effects.put("deviceLastSeenAt",Base64.getEncoder().encodeToString(seen));
        Instant now=fixture.enrollment.jdbc.queryForObject("SELECT UTC_TIMESTAMP(3)",java.sql.Timestamp.class).toInstant();
        result.put("requestedAt",now.toString()).put("expiresAt",now.plusSeconds(600).toString());
        result.putArray("capabilities").add("DOWNLOAD").add("CONFIRM"); return result;
    }
    private JsonNode row(String table,String field,String value) {
        var result=SubmissionJson.object(); var columns=result.putArray("columns"); var rows=result.putArray("rows");
        fixture.enrollment.jdbc.query("SELECT * FROM "+table+" WHERE "+field+"=?",rs->{
            for(int i=1;i<=rs.getMetaData().getColumnCount();i++) columns.add(rs.getMetaData().getColumnLabel(i));
            while(rs.next()) { var row=rows.addArray(); for(int i=1;i<=rs.getMetaData().getColumnCount();i++) {
                byte[] bytes=rs.getBytes(i); if(bytes==null) row.addNull(); else row.add(Base64.getEncoder().encodeToString(bytes));
            }} return null;
        },field.equals("id")?Ids.bytes(UUID.fromString(value)):value); return result;
    }
    RecoveryRecipientSession.Request sign(ObjectNode statement) throws Exception {
        byte[] bytes=fixture.enrollment.signer.sign(statement); Path file=fixture.enrollment.signer.directory.resolve("session-"+UUID.randomUUID()+".json");
        Files.write(file,bytes); return new RecoveryRecipientSession.Request(fixture.enrollment.signer.authority(),file,sha256(bytes));
    }
    void authenticate() {
        var principal=fixture.enrollment.app.getBean(com.acrylic.outbound.pda.PdaSessions.class).authenticate(login.tokens().path("accessToken").asText());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal,null,List.of()));
    }
    com.acrylic.outbound.handover.HandoverRecoveredRecipientSession.Result admit(RecoveryRecipientSession session) {
        authenticate();
        try { return fixture.enrollment.app.getBean(com.acrylic.outbound.handover.HandoverRecoveredRecipientSession.class)
            .admit(session,fixture.enrollment.setup.request(),"receiving-session"); }
        finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }
}
