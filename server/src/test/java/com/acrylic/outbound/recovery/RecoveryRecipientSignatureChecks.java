package com.acrylic.outbound.recovery;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.devices.DeviceCrypto;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.security.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Necessary cryptographic/identity/time counterexamples use valid outer signatures, not malformed DTO substitutes. */
final class RecoveryRecipientSignatureChecks {
    static void before(RecoveryRecipientFixture f) throws Exception {
        var cross=f.body.deepCopy().put("warehouseId",UUID.randomUUID().toString());
        refuses(f,f.sign(cross),RecoveryControlException.class);
        var generator=KeyPairGenerator.getInstance("EC"); generator.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
        String key=Base64.getEncoder().encodeToString(generator.generateKeyPair().getPublic().getEncoded());
        var changed=f.body.deepCopy(); ((ObjectNode)changed.path("recipient")).put("publicKeyDer",key).put("keySha256",DeviceCrypto.sha(key));
        refuses(f,f.sign(changed),BusinessException.class);
        var request=f.sign(f.body); var envelope=(ObjectNode)readCanonical(Files.readAllBytes(request.file()));
        ((ObjectNode)envelope.path("approval")).put("deviceProof",Base64.getEncoder().encodeToString(new byte[70]));
        byte[] signature=f.signer.sign(envelope.path("approval")); Path proof=f.signer.directory.resolve("bad-possession.json"); Files.write(proof,signature);
        refuses(f,new RecoveryRecipientAdmission.Request(request.authority(),proof,sha256(signature)),BusinessException.class);
        envelope.put("signature","AAAA"); byte[] invalid=RecoveryControlJson.encode(envelope);
        Path bad=f.signer.directory.resolve("bad-rsa-signature.json"); Files.write(bad,invalid);
        refuses(f,new RecoveryRecipientAdmission.Request(request.authority(),bad,sha256(invalid)),RecoveryControlException.class);
        expired(f); existingCrossWarehouse(f); revokedTargetVersion(f);
    }
    private static void expired(RecoveryRecipientFixture f) throws Exception {
        Instant before=Instant.now().minusSeconds(600).truncatedTo(ChronoUnit.MILLIS);
        var body=f.body.deepCopy().put("requestedAt",before.toString()).put("expiresAt",before.plusSeconds(300).toString());
        var admission=RecoveryRecipientAdmission.verify(f.sign(body),f.store);
        assertThatThrownBy(()->f.admit(admission)).hasMessage("RECOVERY_RECIPIENT_EXPIRED");
        Instant future=Instant.now().plusSeconds(60).truncatedTo(ChronoUnit.MILLIS);
        var next=f.body.deepCopy().put("requestedAt",future.toString()).put("expiresAt",future.plusSeconds(300).toString());
        var futureAdmission=RecoveryRecipientAdmission.verify(f.sign(next),f.store);
        assertThatThrownBy(()->f.admit(futureAdmission)).hasMessage("RECOVERY_RECIPIENT_EXPIRED");
    }
    private static void existingCrossWarehouse(RecoveryRecipientFixture f) throws Exception {
        var ids=f.jdbc.queryForList("SELECT BIN_TO_UUID(id) FROM device WHERE warehouse_id<>? AND status='ACTIVE'",String.class,
            com.acrylic.outbound.common.Ids.bytes(uuid(f.body,"warehouseId")));
        assertThat(ids).hasSize(1); var body=f.body.deepCopy(); ((ObjectNode)body.path("recipient")).put("deviceId",ids.getFirst());
        var admission=RecoveryRecipientAdmission.verify(f.sign(body),f.store);
        assertThatThrownBy(()->f.admit(admission)).hasMessage("RECOVERY_RECIPIENT_DEVICE_ALREADY_EXISTS");
    }
    private static void revokedTargetVersion(RecoveryRecipientFixture f) throws Exception {
        var body=f.body.deepCopy(); ((ObjectNode)body.path("recipient")).put("userId",f.setup.supervisor().id().toString())
            .put("userAuthVersion",Long.toString(f.setup.supervisor().authVersion()));
        var denied=RecoveryRecipientAdmission.verify(f.sign(body),f.store);
        assertThatThrownBy(()->f.admit(denied)).hasMessage("RECOVERY_RECIPIENT_PERMISSION");
    }
    static void after(RecoveryRecipientFixture f) throws Exception {
        var differentSignature=RecoveryRecipientAdmission.verify(f.sign(f.body),f.store);
        assertThatThrownBy(()->f.admit(differentSignature)).hasMessage("RECOVERY_RECIPIENT_IDEMPOTENCY_CONFLICT");
    }
    private static void refuses(RecoveryRecipientFixture f,RecoveryRecipientAdmission.Request request,Class<? extends Throwable> type) {
        assertThatThrownBy(()->RecoveryRecipientAdmission.verify(request,f.store)).isInstanceOf(type);
    }
    private RecoveryRecipientSignatureChecks() { }
}
