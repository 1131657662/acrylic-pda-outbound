package com.acrylic.outbound.temporary.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import static com.acrylic.outbound.temporary.protocol.ProtocolJson.require;

/** A single reviewed member; every field participates in the existing command signature domain. */
public final class TemporaryExclusionShape {
    private TemporaryExclusionShape() { }
    public static void validate(JsonNode value) {
        require(value!=null && value.isObject(),"EXCLUSION_REQUIRED");
        var names=new HashSet<String>();value.fieldNames().forEachRemaining(names::add);
        require(names.equals(Set.of("orderId","revision","rowVersion","basis","evidence","withdrawalRequestId")),"EXCLUSION_FIELDS_INVALID");
        uuid(value.path("orderId"));
        for(String field:List.of("revision","rowVersion"))require(value.path(field).isTextual()
            && value.path(field).asText().matches("[1-9][0-9]{0,17}"),"EXCLUSION_VERSION_INVALID");
        String basis=value.path("basis").asText();
        require(value.path("basis").isTextual() && Set.of("CANCELLED","WITHDRAWN","UNRELATED").contains(basis),"EXCLUSION_BASIS_INVALID");
        var evidence=value.path("evidence");
        require(evidence.isTextual() && !evidence.asText().isBlank() && evidence.asText().length()<=1000
            && evidence.asText().chars().noneMatch(Character::isISOControl),"EXCLUSION_EVIDENCE_REQUIRED");
        if(basis.equals("WITHDRAWN"))uuid(value.path("withdrawalRequestId"));
        else require(value.path("withdrawalRequestId").isNull(),"EXCLUSION_WITHDRAWAL_NOT_APPLICABLE");
    }
    private static void uuid(JsonNode node) {
        require(node.isTextual() && UUID.fromString(node.asText()).toString().equals(node.asText()),"EXCLUSION_UUID_INVALID");
    }
}
