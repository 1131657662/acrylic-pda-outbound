package com.acrylic.outbound.recovery;

import com.acrylic.outbound.devices.DeviceCrypto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Strict independently signed statement; possession covers the entire statement except the proof itself. */
final class RecoveryRecipientAdmissionFormat {
    static void verify(JsonNode body,RecoveryOperationsAuthority authority,RecoveryScopeReleaseStore store) {
        fields(body,"formatVersion purpose commandId authoritySha256 operatorId requestedAt expiresAt releaseCommitSha256 sourceManifestSha256 supervisorId supervisorAuthVersion registrarId registrarAuthVersion recipient control warehouseId scopeId caseId deviceProof");
        version(body); uuid(body,"commandId");
        require(text(body,"purpose").equals("RECOVERED_TEMP_RECIPIENT_ADMISSION"),"RECOVERY_RECIPIENT_PURPOSE");
        require(text(body,"authoritySha256").equals(authority.sha256())
            && authority.sha256().equals(text(store.approval().facts(),"authoritySha256"))
            && uuid(body,"operatorId").equals(uuid(authority.identity(),"operatorId")),"RECOVERY_RECIPIENT_AUTHORITY");
        bind(body,authority,store); recipient(body,authority); possession(body);
        uuid(body,"registrarId");
        require(revision(body,"registrarAuthVersion")>0 && revision(body,"supervisorAuthVersion")>0,"RECOVERY_RECIPIENT_ACTOR_VERSION");
        Instant requested=instant(body,"requestedAt"),expires=instant(body,"expiresAt");
        require(requested.getNano()%1_000_000==0 && expires.isAfter(requested)
            && !expires.isAfter(requested.plusSeconds(300)),"RECOVERY_RECIPIENT_WINDOW");
    }
    private static void bind(JsonNode body,RecoveryOperationsAuthority authority,RecoveryScopeReleaseStore store) {
        var commit=store.intent().commit(); var closed=authority.closed();
        require(body.path("control").equals(commit.path("control"))
            && closed.control().equals(store.approval().closed().control())
            && closed.authorityRoot().equals(store.approval().closed().authorityRoot())
            && closed.sourceIdentity().equals(store.approval().closed().sourceIdentity())
            && closed.sourcePinSha256().equals(store.approval().closed().sourcePinSha256()),"RECOVERY_RECIPIENT_CONTROL");
        require(text(body,"releaseCommitSha256").equals(store.intent().commitSha256())
            && text(body,"sourceManifestSha256").equals(closed.sourcePinSha256()),"RECOVERY_RECIPIENT_SOURCE");
        for(String field:List.of("warehouseId","scopeId","caseId"))
            require(uuid(body,field).equals(uuid(commit,field)),"RECOVERY_RECIPIENT_SCOPE");
        require(text(body,"supervisorId").equals(text(store.approval().facts(),"supervisorId")),"RECOVERY_RECIPIENT_SUPERVISOR");
    }
    private static void recipient(JsonNode body,RecoveryOperationsAuthority authority) {
        var value=body.path("recipient"); fields(value,"deviceId userId userAuthVersion deviceCode name publicKeyDer keySha256");
        require(!uuid(value,"deviceId").equals(authority.closed().sourceIdentity().deviceId()),"RECOVERY_RECIPIENT_ORIGINAL_DEVICE");
        uuid(value,"userId"); require(revision(value,"userAuthVersion")>0,"RECOVERY_RECIPIENT_USER_VERSION");
        require(text(value,"deviceCode").matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,39}"),"RECOVERY_RECIPIENT_DEVICE_CODE");
        String name=text(value,"name");
        require(!name.isBlank() && name.equals(name.strip()) && name.length()<=100
            && name.chars().noneMatch(Character::isISOControl),"RECOVERY_RECIPIENT_NAME");
        DeviceCrypto.publicKey(text(value,"publicKeyDer"));
        require(text(value,"keySha256").equals(DeviceCrypto.sha(text(value,"publicKeyDer"))),"RECOVERY_RECIPIENT_KEY_HASH");
    }
    private static void possession(JsonNode body) {
        var unsigned=(ObjectNode)body.deepCopy(); unsigned.remove("deviceProof");
        String signature=text(body,"deviceProof");
        try {
            byte[] decoded=Base64.getDecoder().decode(signature);
            require(decoded.length<=80 && Base64.getEncoder().encodeToString(decoded).equals(signature),"RECOVERY_RECIPIENT_PROOF_ENCODING");
        } catch(IllegalArgumentException error) { throw new RecoveryControlException("RECOVERY_RECIPIENT_PROOF_ENCODING",error); }
        DeviceCrypto.verify(text(body.path("recipient"),"publicKeyDer"),canonical(unsigned),signature);
    }
    private RecoveryRecipientAdmissionFormat() { }
}
