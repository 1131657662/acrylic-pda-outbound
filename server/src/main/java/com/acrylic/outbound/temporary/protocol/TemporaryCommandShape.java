package com.acrylic.outbound.temporary.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import static com.acrylic.outbound.temporary.protocol.ProtocolJson.require;

/** All fields participate in the command hash; no values are filled from headers. */
final class TemporaryCommandShape {
    private static final Set<String> BODY=Set.of("commandVersion","commandType","commandId","warehouseId","source",
        "submissionId","workRound","authorizationId","freezeDeviceId","sourceRecoveryEpoch","transportRecoveryEpoch","expected","dispositionManifest","reason");
    private static final Set<String> EXPECTED=Set.of("canonicalOrderId","mappingVersion","currentSubmissionId","scopeRound",
        "slotVersion","decisionVersion","receivedPayloadSha256","declaredUploadSha256","planSha256","targetRevision",
        "candidateContextSha256","userAuthVersion","evidenceSetId","evidenceSetSha256","verificationId","verificationVersion",
        "verificationAlgorithmVersion","dispositionManifestSha256","dispositionManifestVersion","retainedManifestSha256","retainedManifestVersion");
    private static final Set<String> KINDS=Set.of("DUPLICATE_EXCLUDE","LINK","REJECT","SHORT_APPROVE","NORMAL_CONFIRM","PHYSICAL_DISPOSITION","REOPEN_WORK");
    private TemporaryCommandShape() { }
    static void validate(JsonNode body) {
        fields(body,BODY);
        require(body.get("commandVersion").isIntegralNumber() && body.get("commandVersion").longValue()==1,"VERSION_INVALID");
        String kind=text(body,"commandType");require(KINDS.contains(kind),"COMMAND_KIND_INVALID");
        for(String key:List.of("commandId","warehouseId","submissionId","authorizationId","freezeDeviceId"))uuid(body,key);
        for(String key:List.of("workRound","sourceRecoveryEpoch","transportRecoveryEpoch"))positive(body,key);
        String reason=text(body,"reason");require(reason.length()<=1000,"REASON_TOO_LONG");
        require(kind.equals("NORMAL_CONFIRM") || !reason.isBlank(),"REASON_REQUIRED");
        var source=body.get("source");fields(source,Set.of("kind","creatorDeviceId","clientOrderId"));
        require(text(source,"kind").equals("TEMP"),"SOURCE_KIND_INVALID");uuid(source,"creatorDeviceId");uuid(source,"clientOrderId");
        expected(body.get("expected"),kind);
        if (kind.equals("PHYSICAL_DISPOSITION")) {
            require(body.get("dispositionManifest").isObject(),"DISPOSITION_MANIFEST_REQUIRED");
        } else require(body.get("dispositionManifest").isNull(),"DISPOSITION_MANIFEST_NOT_APPLICABLE");
    }
    private static void expected(JsonNode value,String kind) {
        fields(value,EXPECTED);uuid(value,"currentSubmissionId");
        decimal(value,"mappingVersion");decimal(value,"decisionVersion");positive(value,"scopeRound");positive(value,"userAuthVersion");
        for(String key:List.of("receivedPayloadSha256","declaredUploadSha256","planSha256","candidateContextSha256"))nullableHash(value,key);
        if(value.get("canonicalOrderId").isNull()) {
            require(text(value,"mappingVersion").equals("0") && value.get("slotVersion").isNull() && value.get("targetRevision").isNull(),"NULL_MAPPING_INVALID");
        } else { uuid(value,"canonicalOrderId");positive(value,"mappingVersion");positive(value,"slotVersion");positive(value,"targetRevision"); }
        evidence(value,kind);
    }
    private static void evidence(JsonNode value,String kind) {
        boolean physical=kind.equals("PHYSICAL_DISPOSITION"),reopen=kind.equals("REOPEN_WORK");
        var core=List.of("evidenceSetId","evidenceSetSha256","verificationId","verificationVersion","verificationAlgorithmVersion");
        if(physical || reopen) {
            uuid(value,"evidenceSetId");hash(value,"evidenceSetSha256");uuid(value,"verificationId");
            positive(value,"verificationVersion");positive(value,"verificationAlgorithmVersion");
        } else for(String key:core)require(value.get(key).isNull(),"EVIDENCE_NOT_APPLICABLE");
        manifest(value,"disposition",physical);manifest(value,"retained",reopen);
    }
    private static void manifest(JsonNode value,String prefix,boolean required) {
        if(required) { hash(value,prefix+"ManifestSha256");positive(value,prefix+"ManifestVersion"); }
        else require(value.get(prefix+"ManifestSha256").isNull() && value.get(prefix+"ManifestVersion").isNull(),"MANIFEST_NOT_APPLICABLE");
    }
    private static void fields(JsonNode value,Set<String> expected) {
        require(value!=null && value.isObject(),"FIELDS_INVALID");
        Set<String> actual=new HashSet<>();value.fieldNames().forEachRemaining(actual::add);
        require(actual.equals(expected),"FIELDS_INVALID");
    }
    private static String text(JsonNode value,String key) {
        require(value.get(key).isTextual(),"STRING_REQUIRED");return value.get(key).textValue();
    }
    private static void uuid(JsonNode value,String key) {
        String text=text(value,key);
        require(UUID.fromString(text).toString().equals(text),"UUID_INVALID");
    }
    private static void decimal(JsonNode value,String key) { require(text(value,key).matches("0|[1-9][0-9]{0,17}"),"DECIMAL_INVALID"); }
    private static void positive(JsonNode value,String key) { decimal(value,key);require(!text(value,key).equals("0"),"POSITIVE_REQUIRED"); }
    private static void nullableHash(JsonNode value,String key) { if(!value.get(key).isNull())hash(value,key); }
    private static void hash(JsonNode value,String key) { require(text(value,key).matches("[a-f0-9]{64}"),"HASH_INVALID"); }
}
