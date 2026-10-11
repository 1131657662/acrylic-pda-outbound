package com.acrylic.outbound.recovery;

import com.acrylic.outbound.devices.DeviceCrypto;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static com.acrylic.outbound.recovery.RecoveryReleaseRows.*;

/** Binds independent session evidence to the exact new qualification and actual LOGIN device proof. */
final class RecoveryRecipientSessionFormat {
    static void verify(JsonNode value,RecoveryTemporaryQualification qualification) {
        fields(value,"formatVersion purpose commandId qualificationSha256 qualificationId caseId scopeId warehouseId control recipient sourceManifestSha256 admissionSha256 preparedReceiptSha256 login effects requestedAt expiresAt capabilities");
        version(value); uuid(value,"commandId");
        require(text(value,"purpose").equals("RECOVERED_TEMP_RECIPIENT_SESSION"),"RECOVERY_SESSION_PURPOSE");
        var q=qualification.facts();
        require(text(value,"qualificationSha256").equals(qualification.sha256()),"RECOVERY_SESSION_QUALIFICATION_PIN");
        for(String field:List.of("qualificationId","caseId","scopeId","warehouseId","control","recipient","sourceManifestSha256","admissionSha256"))
            require(value.path(field).equals(q.path(field)),"RECOVERY_SESSION_QUALIFICATION_BINDING");
        hashField(value,"preparedReceiptSha256");
        Instant at=instant(value,"requestedAt"),expires=instant(value,"expiresAt");
        require(at.getNano()%1_000_000==0 && expires.getNano()%1_000_000==0 && expires.isAfter(at)
            && !at.isBefore(instant(q,"requestedAt")) && !expires.isAfter(instant(q,"expiresAt")),"RECOVERY_SESSION_WINDOW");
        require(value.path("capabilities").equals(com.acrylic.outbound.submissions.SubmissionJson.object().putArray("capabilities")
            .add("DOWNLOAD").add("CONFIRM")),"RECOVERY_SESSION_CAPABILITIES");
        login(value,qualification);
    }
    private static void login(JsonNode value,RecoveryTemporaryQualification qualification) {
        var login=value.path("login"); fields(login,"sessionId challengeId username requestId deviceProof");
        uuid(login,"sessionId"); uuid(login,"challengeId");
        require(text(login,"username").matches("[A-Za-z0-9._-]{1,64}")
            && text(login,"requestId").matches("[A-Za-z0-9._:-]{1,64}"),"RECOVERY_SESSION_LOGIN_FORMAT");
        var effects=value.path("effects"); fields(effects,"session challenge audit deviceLastSeenAt");
        var challenge=one(effects.path("challenge"));
        RecoveryReleaseRows.uuid(challenge,"id",text(login,"challengeId"));
        RecoveryReleaseRows.uuid(challenge,"device_id",text(value.path("recipient"),"deviceId")); eq(challenge,"purpose","LOGIN");
        String message="acrylic-pda-proof-v1\n"+text(login,"challengeId")+"\n"+text(value.path("recipient"),"deviceId")
            +"\nLOGIN\n"+text(challenge,"nonce")+"\n"+DeviceCrypto.sha(text(login,"username"));
        DeviceCrypto.verify(text(qualification.recipient().facts().path("recipient"),"publicKeyDer"),message,text(login,"deviceProof"));
    }
    static java.util.Map<String,String> one(JsonNode table) {
        fields(table,"columns rows"); var rows=decode(table); require(rows.size()==1,"RECOVERY_SESSION_EFFECT_COUNT"); return rows.getFirst();
    }
    private RecoveryRecipientSessionFormat() { }
}
