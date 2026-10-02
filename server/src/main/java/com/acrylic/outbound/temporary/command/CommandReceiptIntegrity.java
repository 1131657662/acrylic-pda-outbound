package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.protocol.TemporaryProtocol;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.stereotype.Component;

/** Validate immutable evidence against its stored references, never against current mutable permissions/slots. */
@Component
public class CommandReceiptIntegrity {
    private final CommandSignature signatures;private final CommandStore store;private final CommandHistoricalIdentity history;
    private final CommandBusinessIntegrity business;
    public CommandReceiptIntegrity(CommandSignature signatures,CommandStore store,CommandHistoricalIdentity history,CommandBusinessIntegrity business) {
        this.signatures=signatures;this.store=store;this.history=history;this.business=business;
    }
    public JsonNode verify(CommandRecord r) {
        try { return checked(r); }
        catch(RuntimeException damaged) { throw CommandErrors.corrupt(damaged); }
    }
    private JsonNode checked(CommandRecord r) {
        CommandErrors.integrity(r.id("id")!=null && r.id("id").equals(r.id("resource_id")) && r.id("api_request_id").equals(r.id("request_id")));
        var command=CommandInput.parse(r.bytes("canonical_command_bytes"));
        CommandErrors.integrity(Arrays.equals(command.bytes(),r.bytes("canonical_command_bytes")) && command.digest().equals(r.text("request_sha256"))
            && command.digest().equals(r.text("command_sha256")) && command.key().equals(r.text("original_key"))
            && command.kind().equals(r.text("command_type")) && TemporaryProtocol.ROUTE_SCOPE.equals(r.text("route_scope"))
            && "1".equals(r.text("command_version")) && "1".equals(r.text("receipt_version")));
        CommandErrors.integrity(TemporaryProtocol.originalResponseSha256(r.bytes("original_response_bytes")).equals(r.text("original_response_sha256")));
        var outer=(ObjectNode)SubmissionJson.parse(TemporaryProtocol.canonical(r.bytes("original_response_bytes")));
        CommandErrors.integrity(outer.path("signedReceipt").asText().equals(r.text("signed_receipt")));
        var unsigned=outer.deepCopy();unsigned.remove("signedReceipt");
        CommandReceiptShape.validate(unsigned);
        CommandErrors.integrity(TemporaryProtocol.unsignedReceiptSha256(CommandReceiptCodec.bytes(unsigned)).equals(r.text("unsigned_receipt_sha256")));
        signatures.verify(r.text("signed_receipt"),r.text("signature_key_id"),unsigned);
        header(r,command,unsigned);context(r,command,unsigned);history.verify(unsigned);closure(r,unsigned);business.verify(unsigned);
        approval(unsigned);return unsigned;
    }
    private void header(CommandRecord r,CommandInput command,JsonNode receipt) {
        CommandErrors.integrity(receipt.path("receiptId").asText().equals(r.id("id").toString())
            && receipt.path("commandId").asText().equals(command.key()) && receipt.path("commandType").asText().equals(command.kind())
            && receipt.path("routeScope").asText().equals(TemporaryProtocol.ROUTE_SCOPE) && receipt.path("commandSha256").asText().equals(command.digest())
            && receipt.path("recordedAt").asText().equals(r.recordedAt().toString()) && receipt.path("capabilities").equals(CommandReceiptCodec.capabilities()));
        var actor=receipt.path("actor");
        CommandErrors.integrity(actor.path("principalId").asText().equals(r.id("principal_id").toString())
            && actor.path("userAuthVersion").equals(command.expected().get("userAuthVersion")) && receipt.path("expected").equals(command.expected()));
        var permissions=actor.path("permissionSnapshot");String required=CommandPermissions.execution(command.kind());
        CommandErrors.integrity(required!=null && permissions.path("requiredPermissions").size()==1
            && permissions.path("requiredPermissions").get(0).asText().equals(required));
        CommandErrors.integrity(contains(permissions.path("effectivePermissions"),required)
            && contains(permissions.path("warehouseIds"),r.id("warehouse_id").toString()));
    }
    private boolean contains(JsonNode list,String value) {
        if(!list.isArray())return false;for(var item:list)if(item.isTextual() && item.asText().equals(value))return true;return false;
    }
    private void context(CommandRecord r,CommandInput command,JsonNode receipt) {
        var c=receipt.path("originalContext");var body=command.body();
        Map<String,String> fields=Map.of("scopeId","scope_id","warehouseId","warehouse_id","submissionId","submission_id",
            "creatorDeviceId","creator_device_id","clientOrderId","client_order_id","authorizationId","temporary_authorization_id",
            "originalOperatorId","original_operator_id","mappingId","original_mapping_id");
        fields.forEach((json,sql)->CommandErrors.integrity(c.path(json).asText().equals(r.id(sql).toString())));
        for(String key:List.of("warehouseId","submissionId","workRound","authorizationId","freezeDeviceId","sourceRecoveryEpoch"))
            CommandErrors.integrity(body.get(key).equals(c.get(key)));
        for(String key:List.of("creatorDeviceId","clientOrderId"))CommandErrors.integrity(body.get("source").get(key).equals(c.get(key)));
        CommandErrors.integrity(c.path("freezeDeviceId").equals(c.path("creatorDeviceId")) && c.path("workRound").asText().equals(r.text("work_round"))
            && c.path("sourceRecoveryEpoch").asText().equals(r.text("source_recovery_epoch")));
        if ("PHYSICAL_DISPOSITION".equals(command.kind())) {
            CommandErrors.integrity(r.id("evidence_set_id") != null && r.id("evidence_verification_id") != null
                && command.expected().path("evidenceSetId").asText().equals(r.id("evidence_set_id").toString())
                && command.expected().path("verificationId").asText().equals(r.id("evidence_verification_id").toString()));
            return;
        }
        var mapping=new CommandRecord(store.mapping(r.id("original_mapping_id")));
        CommandErrors.integrity(r.id("scope_id").equals(mapping.id("scope_id")) && r.id("warehouse_id").equals(mapping.id("warehouse_id"))
            && Objects.equals(r.id("original_canonical_order_id"),mapping.id("canonical_order_id"))
            && Objects.equals(c.path("canonicalOrderId").isNull()?null:UUID.fromString(c.path("canonicalOrderId").asText()),mapping.id("canonical_order_id"))
            && c.path("mappingVersion").asText().equals(mapping.text("mapping_version"))
            && c.get("mappingOrderId").equals(c.get("canonicalOrderId")));
        var expected=CommandExpected.current(new CommandContext(c),receipt.path("actor").path("userAuthVersion").asText());
        if(!receipt.path("businessDecision").isNull())expected.set("candidateContextSha256",receipt.path("businessDecision").path("candidates").get("contextSha256"));
        CommandErrors.integrity(expected.equals(command.expected()));
    }
    private void closure(CommandRecord r,JsonNode receipt) {
        var event=new CommandRecord(store.event(r.id("id")));byte[] unsigned=CommandReceiptCodec.bytes(receipt);
        CommandErrors.integrity(event.id("scope_id").equals(r.id("scope_id")) && event.id("id").toString().equals(receipt.path("eventId").asText())
            && event.text("decision_kind").equals(CommandDecision.kind(receipt)) && event.text("resulting_decision_version").equals(receipt.path("resultingDecisionVersion").asText())
            && Long.parseLong(event.text("resulting_decision_version"))==Math.addExact(receipt.path("originalContext").path("decisionVersion").asLong(),1)
            && Arrays.equals(unsigned,event.bytes("context_bytes")) && event.recordedAt().equals(r.recordedAt()));
        var audits=store.audits(r.id("id"));CommandErrors.integrity(audits.size()==1);
        var audit=audits.getFirst();var details=SubmissionJson.parse(audit.get("details").toString());
        CommandErrors.integrity(CommandDecision.eventType(receipt).equals(audit.get("event_type")) && Arrays.equals((byte[])audit.get("actor_id"),Ids.bytes(r.id("principal_id")))
            && details.path("originalResponseSha256").asText().equals(r.text("original_response_sha256"))
            && details.path("commandSha256").asText().equals(r.text("command_sha256")));
    }
    private void approval(JsonNode receipt) {
        var decision=receipt.path("businessDecision");if(decision.isNull() || decision.path("shortApprovalReceiptId").isNull())return;
        var linked=store.byReceipt(UUID.fromString(decision.path("shortApprovalReceiptId").asText()));
        CommandErrors.integrity("SHORT_APPROVE".equals(linked.text("command_type")));
        var approved=verify(linked);CommandErrors.integrity(CommandDecision.kind(approved).equals("SHORT_APPROVE")
            && approved.path("resultingDecisionVersion").equals(receipt.path("originalContext").path("decisionVersion"))
            && linked.text("original_response_sha256").equals(decision.path("shortApprovalSha256").asText())
            && approved.path("businessDecision").path("candidates").equals(decision.path("candidates"))
            && !java.time.Instant.parse(approved.path("recordedAt").asText()).isAfter(java.time.Instant.parse(receipt.path("recordedAt").asText())));
        for(String key:List.of("scopeId","submissionId","canonicalOrderId","mappingVersion","currentSubmissionId","scopeRound",
            "slotVersion","targetRevision","receivedPayloadSha256","declaredUploadSha256","planSha256"))
            CommandErrors.integrity(approved.path("originalContext").get(key).equals(receipt.path("originalContext").get(key)));
    }
}
