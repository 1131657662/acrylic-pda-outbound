package com.acrylic.outbound.temporary.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import static com.acrylic.outbound.temporary.protocol.ProtocolJson.require;

/** All supplied fields participate in the command hash; old commands are never filled in. */
final class TemporaryCommandShape {
    private static final Set<String> BODY = Set.of("commandVersion","commandType","commandId","warehouseId","source",
        "submissionId","workRound","authorizationId","freezeDeviceId","sourceRecoveryEpoch","transportRecoveryEpoch","expected","reason");
    private static final Set<String> EXPECTED = Set.of("canonicalOrderId","mappingVersion","currentSubmissionId","scopeRound",
        "slotVersion","decisionVersion","receivedPayloadSha256","declaredUploadSha256","planSha256","targetRevision",
        "candidateContextSha256","userAuthVersion","evidenceSetId","evidenceSetSha256","verificationId","verificationVersion",
        "verificationAlgorithmVersion","dispositionManifestSha256","dispositionManifestVersion","retainedManifestSha256","retainedManifestVersion");
    private static final Set<String> KINDS = Set.of("DUPLICATE_EXCLUDE","LINK","REJECT","SHORT_APPROVE","NORMAL_CONFIRM","PHYSICAL_DISPOSITION","CLOSE_DISPOSITION","REOPEN_WORK","ACTIVATE_CONTINUATION");
    private TemporaryCommandShape() { }

    static void validate(JsonNode body) {
        validate(body,false);
    }
    static void validate(JsonNode body,boolean historical) {
        require(body.get("commandVersion").isIntegralNumber() && body.get("commandVersion").longValue() == 1,"VERSION_INVALID");
        String kind = text(body,"commandType");
        require(KINDS.contains(kind),"COMMAND_KIND_INVALID");
        boolean legacy=historical && kind.equals("DUPLICATE_EXCLUDE") && !body.has("exclusion");
        bodyFields(body,kind,legacy);
        if(kind.equals("DUPLICATE_EXCLUDE") && !legacy)TemporaryExclusionShape.validate(body.path("exclusion"));
        for (String key : List.of("commandId","warehouseId","submissionId","authorizationId","freezeDeviceId")) uuid(body,key);
        for (String key : List.of("workRound","sourceRecoveryEpoch","transportRecoveryEpoch")) positive(body,key);
        String reason = text(body,"reason");
        require(reason.length() <= 1000,"REASON_TOO_LONG");
        require(kind.equals("NORMAL_CONFIRM") || !reason.isBlank(),"REASON_REQUIRED");
        if (Set.of("REOPEN_WORK","ACTIVATE_CONTINUATION").contains(kind)) {
            positive(body,"nextOrderScanSeq"); retainedManifest(body.get("retainedManifest"));
            if (kind.equals("ACTIVATE_CONTINUATION")) uuid(body,"continuationTicketId");
        }
        var source = body.get("source");
        fields(source,Set.of("kind","creatorDeviceId","clientOrderId"));
        require(text(source,"kind").equals("TEMP"),"SOURCE_KIND_INVALID");
        uuid(source,"creatorDeviceId"); uuid(source,"clientOrderId");
        expected(body.get("expected"),kind);
        var manifest = body.get("dispositionManifest");
        if (kind.equals("PHYSICAL_DISPOSITION") || kind.equals("CLOSE_DISPOSITION"))
            require(manifest != null && manifest.isObject(),"DISPOSITION_MANIFEST_REQUIRED");
        else require(manifest == null || manifest.isNull(),"DISPOSITION_MANIFEST_NOT_APPLICABLE");
    }

    private static void bodyFields(JsonNode body,String kind,boolean legacy) {
        var expected = new HashSet<>(BODY);
        boolean physical = kind.equals("PHYSICAL_DISPOSITION") || kind.equals("CLOSE_DISPOSITION");
        if (physical || body.has("dispositionManifest")) expected.add("dispositionManifest");
        if (Set.of("REOPEN_WORK","ACTIVATE_CONTINUATION").contains(kind)) { expected.add("retainedManifest"); expected.add("nextOrderScanSeq"); }
        if (kind.equals("ACTIVATE_CONTINUATION")) expected.add("continuationTicketId");
        if (kind.equals("DUPLICATE_EXCLUDE") && !legacy) expected.add("exclusion");
        fields(body,expected);
    }

    private static void retainedManifest(JsonNode manifest) {
        require(manifest != null && manifest.isObject() && manifest.size()==2,"RETAINED_MANIFEST_INVALID");
        require(manifest.path("version").isIntegralNumber() && manifest.path("version").longValue()>0,
            "RETAINED_MANIFEST_INVALID");
        require(manifest.path("items").isArray() && !manifest.path("items").isEmpty(),"RETAINED_MANIFEST_INVALID");
    }

    private static void expected(JsonNode value,String kind) {
        fields(value,EXPECTED); uuid(value,"currentSubmissionId");
        decimal(value,"mappingVersion"); decimal(value,"decisionVersion"); positive(value,"scopeRound"); positive(value,"userAuthVersion");
        for (String key : List.of("receivedPayloadSha256","declaredUploadSha256","planSha256","candidateContextSha256")) nullableHash(value,key);
        if (value.get("canonicalOrderId").isNull()) {
            require(text(value,"mappingVersion").equals("0") && value.get("slotVersion").isNull()
                && value.get("targetRevision").isNull(),"NULL_MAPPING_INVALID");
        } else {
            uuid(value,"canonicalOrderId"); positive(value,"mappingVersion");
            if (!kind.equals("PHYSICAL_DISPOSITION") || !value.get("slotVersion").isNull()) positive(value,"slotVersion");
            positive(value,"targetRevision");
        }
        evidence(value,kind);
    }

    private static void evidence(JsonNode value,String kind) {
        boolean physical = kind.equals("PHYSICAL_DISPOSITION") || kind.equals("CLOSE_DISPOSITION"), reopen = Set.of("REOPEN_WORK","ACTIVATE_CONTINUATION").contains(kind);
        var core = List.of("evidenceSetId","evidenceSetSha256","verificationId","verificationVersion","verificationAlgorithmVersion");
        if (physical || reopen) {
            uuid(value,"evidenceSetId"); hash(value,"evidenceSetSha256"); uuid(value,"verificationId");
            positive(value,"verificationVersion"); positive(value,"verificationAlgorithmVersion");
        } else for (String key : core) require(value.get(key).isNull(),"EVIDENCE_NOT_APPLICABLE");
        manifest(value,"disposition",physical); manifest(value,"retained",reopen);
    }

    private static void manifest(JsonNode value,String prefix,boolean required) {
        if (required) { hash(value,prefix + "ManifestSha256"); positive(value,prefix + "ManifestVersion"); }
        else require(value.get(prefix + "ManifestSha256").isNull()
            && value.get(prefix + "ManifestVersion").isNull(),"MANIFEST_NOT_APPLICABLE");
    }
    private static void fields(JsonNode value,Set<String> expected) {
        require(value != null && value.isObject(),"FIELDS_INVALID");
        Set<String> actual = new HashSet<>(); value.fieldNames().forEachRemaining(actual::add);
        require(actual.equals(expected),"FIELDS_INVALID");
    }
    private static String text(JsonNode value,String key) {
        require(value.get(key).isTextual(),"STRING_REQUIRED"); return value.get(key).textValue();
    }
    private static void uuid(JsonNode value,String key) {
        String text = text(value,key); require(UUID.fromString(text).toString().equals(text),"UUID_INVALID");
    }
    private static void decimal(JsonNode value,String key) { require(text(value,key).matches("0|[1-9][0-9]{0,17}"),"DECIMAL_INVALID"); }
    private static void positive(JsonNode value,String key) { decimal(value,key); require(!text(value,key).equals("0"),"POSITIVE_REQUIRED"); }
    private static void nullableHash(JsonNode value,String key) { if (!value.get(key).isNull()) hash(value,key); }
    private static void hash(JsonNode value,String key) { require(text(value,key).matches("[a-f0-9]{64}"),"HASH_INVALID"); }
}
