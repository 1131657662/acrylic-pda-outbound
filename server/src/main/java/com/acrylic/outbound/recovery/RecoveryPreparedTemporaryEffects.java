package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static com.acrylic.outbound.recovery.RecoveryReleaseRows.*;

/** Only the exact signed PREPARE is inverted in a comparison copy; original tables remain fully compared. */
final class RecoveryPreparedTemporaryEffects {
    private final RecoveryTemporaryQualification qualification;
    private final RecoveryReceiptKeys keys;
    private final Instant observedAt;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    RecoveryPreparedTemporaryEffects(RecoveryTemporaryQualification qualification,RecoveryReceiptKeys keys,javax.sql.DataSource database) {
        this.qualification=qualification; this.keys=keys;
        jdbc=new org.springframework.jdbc.core.JdbcTemplate(database);
        observedAt=jdbc.queryForObject("SELECT UTC_TIMESTAMP(3)",java.sql.Timestamp.class).toInstant();
    }
    JsonNode before(JsonNode actual) {
        qualification.materials(); var q=qualification.facts();
        var found=decode(actual.path("handover_command_receipt")).stream()
            .filter(r->Objects.equals(r.get("case_id"),id(text(q,"caseId"))) && text(r,"command_type").equals("PREPARE")).toList();
        require(found.size()<=1,"RECOVERY_PREPARATION_MULTIPLE");
        if(found.isEmpty()) return actual.deepCopy();
        var row=found.getFirst(); var receipt=RecoveryReleaseFenceReceipts.receipt(row,keys);
        var command=SubmissionJson.parse(RecoveryReleaseRows.bytes(row.get("canonical_command_bytes")));
        var ticket=bind(command,receipt); ObjectNode result=actual.deepCopy();
        commandRows(result,row,receipt);
        var cases=decode(result.path("handover_case"));
        var selected=cases.stream().filter(r->Objects.equals(r.get("id"),id(text(q,"caseId")))).toList();
        require(selected.size()==1,"RECOVERY_PREPARATION_CASE");
        var current=selected.getFirst(); var before=RecoveryPreparedCaseEffect.before(current,receipt,ticket);
        var approved=decode(actual.path("handover_command_receipt")).stream()
            .filter(r->Objects.equals(r.get("case_id"),id(text(q,"caseId"))) && text(r,"command_type").equals("APPROVE")).toList();
        require(approved.size()==1,"RECOVERY_PREPARATION_APPROVAL");
        var original=RecoveryReleaseFenceReceipts.receipt(approved.getFirst(),keys);
        require(!Instant.parse(text(receipt,"recordedAt")).isBefore(Instant.parse(text(original,"recordedAt"))),"RECOVERY_PREPARATION_TIME");
        before.put("updated_at",approved.getFirst().get("recorded_at"));
        cases.set(cases.indexOf(current),before); set(result,"handover_case",cases); return result;
    }
    private JsonNode bind(JsonNode command,JsonNode receipt) {
        fields(command,"commandVersion commandType commandId warehouseId caseId transportRecoveryEpoch targetDeviceId targetUserId targetQualificationId expected");
        var q=qualification.facts(); var target=q.path("recipient"); var context=receipt.path("caseContext");
        require(command.path("commandVersion").isInt() && command.path("commandVersion").intValue()==1
            && text(command,"commandType").equals("PREPARE"),"RECOVERY_PREPARATION_COMMAND");
        for(String field:List.of("caseId","warehouseId")) require(command.path(field).equals(q.path(field)),"RECOVERY_PREPARATION_BINDING");
        require(text(command,"targetQualificationId").equals(text(q,"qualificationId"))
            && text(command,"targetDeviceId").equals(text(target,"deviceId")) && text(command,"targetUserId").equals(text(target,"userId"))
            && text(command,"transportRecoveryEpoch").equals(text(q.path("control"),"transportRecoveryEpoch")),"RECOVERY_PREPARATION_BINDING");
        bindContext(command,receipt);
        return ticket(receipt);
    }
    private void bindContext(JsonNode command,JsonNode receipt) {
        var q=qualification.facts(); var context=receipt.path("caseContext");
        var admission=qualification.recipient().facts(); var actor=receipt.path("actor");
        require(text(actor,"principalId").equals(text(admission,"supervisorId"))
            && text(actor,"userAuthVersion").equals(text(admission,"supervisorAuthVersion"))
            && text(actor,"warehouseId").equals(text(q,"warehouseId")),"RECOVERY_PREPARATION_ACTOR");
        require(text(context,"status").equals("APPROVED") && text(context,"caseVersion").equals("3"),"RECOVERY_PREPARATION_CONTEXT");
        for(String field:List.of("caseId","scopeId","warehouseId"))
            require(context.path(field).equals(q.path(field)),"RECOVERY_PREPARATION_CONTEXT");
        require(text(context,"submissionId").equals(text(q.path("source"),"submissionId"))
            && text(context,"fromWorkRound").equals(text(q.path("source"),"workRound"))
            && text(context,"inputHighWatermark").equals(text(q.path("source"),"inputHighWatermark")),"RECOVERY_PREPARATION_SOURCE");
        var expected=SubmissionJson.object().put("caseId",text(q,"caseId")).put("caseVersion","3").put("status","APPROVED")
            .put("userAuthVersion",text(admission,"supervisorAuthVersion"));
        require(command.path("expected").equals(expected),"RECOVERY_PREPARATION_EXPECTED");
    }
    private JsonNode ticket(JsonNode receipt) {
        var q=qualification.facts(); var target=q.path("recipient"); var context=receipt.path("caseContext");
        var ticket=SubmissionJson.object().put("caseId",text(q,"caseId")).put("targetDeviceId",text(target,"deviceId"))
            .put("targetUserId",text(target,"userId")).put("inputHighWatermark",text(context,"inputHighWatermark"))
            .put("canonicalOrderId",text(context,"canonicalOrderId")).put("issuedAt",text(receipt,"recordedAt"));
        ticket.set("temporaryQualification",qualification.preparationDescriptor());
        var decision=SubmissionJson.object().put("caseId",text(q,"caseId")).put("status","TARGET_PREPARED")
            .put("targetDeviceId",text(target,"deviceId")).put("targetUserId",text(target,"userId"))
            .put("preparedTicketSha256",SubmissionJson.sha(ticket));
        decision.set("temporaryQualification",qualification.preparationDescriptor());
        Instant at=Instant.parse(text(receipt,"recordedAt"));
        require(receipt.path("businessDecision").equals(decision) && !at.isBefore(instant(q,"requestedAt"))
            && at.isBefore(instant(q,"expiresAt")),"RECOVERY_PREPARATION_DECISION"); return ticket;
    }
    private void commandRows(ObjectNode result,Map<String,String> commandRow,JsonNode receipt) {
        var raw=result.path("operation_event").has("rows")?result:new RecoveryReleaseDatabaseRows(jdbc).read();
        for(String table:List.of("handover_command_receipt","api_idempotency_request","operation_event")) {
            var snapshot=result.path(table); var source=snapshot.has("rows")?snapshot:raw.path(table);
            if(!snapshot.has("rows")) require(snapshot.path("columns").equals(source.path("columns"))
                && text(snapshot,"rowCount").equals(Integer.toString(source.path("rows").size()))
                && text(snapshot,"rowsSha256").equals(sha256(RecoveryTemporaryEvidenceMaterial.encode(source.path("rows")))),"RECOVERY_PREPARATION_RAW_DIGEST");
            var rows=decode(source); String field=table.equals("handover_command_receipt")?"id":table.equals("operation_event")?"entity_id":"resource_id";
            var selected=rows.stream().filter(r->Objects.equals(r.get(field),id(text(receipt,"receiptId")))).toList();
            require(selected.size()==1,"RECOVERY_PREPARATION_EFFECT_COUNT"); var row=selected.getFirst();
            if(table.equals("handover_command_receipt")) require(row.equals(commandRow),"RECOVERY_PREPARATION_RECEIPT");
            if(table.equals("operation_event")) RecoveryReleaseCommandEffects.audit(row,receipt,commandRow,observedAt);
            if(table.equals("api_idempotency_request")) RecoveryReleaseCommandEffects.idempotency(row,receipt);
            rows.remove(row); set(result,table,rows);
        }
    }
    private static void set(ObjectNode tables,String name,List<Map<String,String>> rows) {
        var table=(ObjectNode)tables.path(name); var encoded=encode(table.path("columns"),rows);
        if(table.has("rows")) table.set("rows",encoded);
        else table.put("rowsSha256",sha256(RecoveryTemporaryEvidenceMaterial.encode(encoded)));
        if(table.has("rowCount")) table.put("rowCount",Integer.toString(rows.size()));
    }
}
