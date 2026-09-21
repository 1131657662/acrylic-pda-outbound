package com.acrylic.outbound.temporary.command;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Strict v1 wire types; Jackson coercions must never turn malformed signed values into trusted facts. */
public final class CommandReceiptShape {
    private static final String ROOT="receiptVersion receiptId commandId routeScope commandType commandSha256 status businessDecision recordedAt eventId resultingDecisionVersion originalContext actor expected capabilities";
    private static final String CONTEXT="scopeId warehouseId creatorDeviceId clientOrderId submissionId workRound status authorizationId authorizationEpoch authorizationUserVersion authorizationDeviceVersion freezeDeviceId originalOperatorId sourceRecoveryEpoch canonicalOrderId mappingId mappingVersion mappingOrderId currentSubmissionId scopeRound decisionVersion slotVersion slotScopeId slotSubmissionId slotRound targetRevision receivedPayloadSha256 declaredUploadSha256 planSha256";
    private CommandReceiptShape() { }
    public static void validate(JsonNode receipt) {
        fields(receipt,ROOT);
        var version=receipt.get("receiptVersion");
        require(version.isIntegralNumber() && version.canConvertToInt() && version.intValue()==1);
        for(String key:List.of("receiptId","commandId","eventId"))uuid(receipt.get(key),false);
        for(String key:List.of("routeScope","commandType","status"))text(receipt.get(key));
        hash(receipt.get("commandSha256"),false);decimal(receipt.get("resultingDecisionVersion"),false,true);
        require(receipt.get("businessDecision").isNull());instant(receipt.get("recordedAt"));
        actor(receipt.get("actor"));context(receipt.get("originalContext"));
        require(receipt.get("expected").isObject() && receipt.get("capabilities").equals(CommandReceiptCodec.capabilities()));
    }
    private static void actor(JsonNode actor) {
        fields(actor,"principalId userAuthVersion deviceId sessionId deviceVersion permissionSnapshot");
        uuid(actor.get("principalId"),false);decimal(actor.get("userAuthVersion"),false,true);
        boolean web=actor.get("sessionId").isNull();
        uuid(actor.get("deviceId"),web);uuid(actor.get("sessionId"),web);decimal(actor.get("deviceVersion"),web,true);
        if(web)require(actor.get("deviceId").isNull() && actor.get("deviceVersion").isNull());
        var permissions=actor.get("permissionSnapshot");
        fields(permissions,"requiredPermissions effectivePermissions warehouseIds roleIds");
        strings(permissions.get("requiredPermissions"),false);strings(permissions.get("effectivePermissions"),false);
        strings(permissions.get("warehouseIds"),true);strings(permissions.get("roleIds"),true);
    }
    private static void context(JsonNode context) {
        fields(context,CONTEXT);
        for(String key:List.of("scopeId","warehouseId","creatorDeviceId","clientOrderId","submissionId","authorizationId",
            "freezeDeviceId","originalOperatorId","mappingId"))uuid(context.get(key),false);
        for(String key:List.of("canonicalOrderId","mappingOrderId","currentSubmissionId","slotScopeId","slotSubmissionId"))uuid(context.get(key),true);
        for(String key:List.of("workRound","authorizationEpoch","authorizationUserVersion","authorizationDeviceVersion","sourceRecoveryEpoch",
            "scopeRound"))decimal(context.get(key),false,true);
        decimal(context.get("mappingVersion"),false,false);decimal(context.get("decisionVersion"),false,false);
        for(String key:List.of("slotVersion","slotRound","targetRevision"))decimal(context.get(key),true,true);
        for(String key:List.of("receivedPayloadSha256","declaredUploadSha256","planSha256"))hash(context.get(key),true);
        text(context.get("status"));
    }
    private static void fields(JsonNode object,String names) {
        require(object!=null && object.isObject());var expected=Set.of(names.split(" "));var actual=new HashSet<String>();
        object.fieldNames().forEachRemaining(actual::add);require(actual.equals(expected));
    }
    private static void strings(JsonNode list,boolean ids) {
        require(list.isArray() && !list.isEmpty());String prior=null;
        for(var item:list) {
            if(ids)uuid(item,false);else text(item);
            String value=item.textValue();require(!value.isBlank() && (prior==null || prior.compareTo(value)<0));prior=value;
        }
    }
    private static void uuid(JsonNode node,boolean nullable) {
        if(nullable && node.isNull())return;text(node);
        require(UUID.fromString(node.textValue()).toString().equals(node.textValue()));
    }
    private static void decimal(JsonNode node,boolean nullable,boolean positive) {
        if(nullable && node.isNull())return;text(node);
        require(node.textValue().matches(positive?"[1-9][0-9]*":"0|[1-9][0-9]*"));
        require(Long.parseLong(node.textValue())>=0);
    }
    private static void hash(JsonNode node,boolean nullable) {
        if(nullable && node.isNull())return;text(node);require(node.textValue().matches("[0-9a-f]{64}"));
    }
    private static void instant(JsonNode node) {
        text(node);var value=Instant.parse(node.textValue());
        require(value.toString().equals(node.textValue()) && value.truncatedTo(ChronoUnit.MILLIS).equals(value));
    }
    private static void text(JsonNode node) { require(node!=null && node.isTextual()); }
    private static void require(boolean value) { CommandErrors.integrity(value); }
}
