package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.temporary.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Generated signing keys with storage-boundary doubles; no original operator/device identity is claimed. */
final class HandoverTemporaryCredentialFixture {
    final UUID id = UUID.randomUUID(), device = UUID.randomUUID(), user = UUID.randomUUID(), warehouse = UUID.randomUUID();
    final UUID snapshot = UUID.randomUUID(), bundle = UUID.randomUUID(), policy = UUID.randomUUID();
    final Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    final Instant issued = now.minusSeconds(60), expires = issued.plusSeconds(172800);
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final TemporaryStore store = mock(TemporaryStore.class);
    final OfflineSigner signer;
    final ObjectNode manifest, auth, proof;
    TemporaryCommands.Acknowledgement ack;

    HandoverTemporaryCredentialFixture(Path directory) throws Exception {
        signer = signer(directory, "first", ""); manifest = manifest();
        auth = object().put("iss", "acrylic-outbound").put("aud", "acrylic-pda-temporary-work")
            .put("jti", id.toString()).put("purpose", "TEMP_WORK").put("manifestSha256", sha(manifest))
            .put("scope", "temporary_order:create pda:scan pda:submit").put("iat", issued.getEpochSecond()).put("exp", expires.getEpochSecond());
        auth.set("identity", manifest.path("identity"));
        proof = object().put("iss", "acrylic-outbound").put("aud", "acrylic-pda-temporary-preparation")
            .put("authorizationId", id.toString()).put("bundleId", bundle.toString()).put("manifestSha256", sha(manifest))
            .put("iat", issued.getEpochSecond()).put("exp", expires.getEpochSecond());
        when(jdbc.queryForObject(startsWith("SELECT key_id"), eq(String.class), any(Object[].class))).thenReturn(signer.publicKey().keyId());
        when(jdbc.queryForObject(startsWith("SELECT command_sha256"), eq(String.class), any(Object[].class))).thenReturn("a".repeat(64));
        when(jdbc.queryForObject(startsWith("SELECT CAST(version"), eq(String.class), any(Object[].class))).thenReturn("1");
        when(jdbc.queryForMap(anyString(), any(Object[].class))).thenReturn(Map.of("snapshot", snapshot.toString(), "mapping", "1", "policy", policy.toString()));
        when(store.ack(any())).thenAnswer(call -> ack);
        acknowledge(signer, now, now);
    }

    TemporaryStore.Stored row() {
        return new TemporaryStore.Stored(id, warehouse, device, user, "1", "2", "3", "4", issued, expires,
            new TemporaryCommands.Receipt(id.toString(), bundle.toString(), signer.sign(auth).token(), canonical(manifest),
                sha(manifest), signer.sign(proof).token()));
    }

    HandoverTemporaryCredential reader(OfflineSigner trust) { return new HandoverTemporaryCredential(trust, store, jdbc); }

    void acknowledge(OfflineSigner issuer, Instant activated, Instant received) {
        var claims = object().put("iss", "acrylic-outbound").put("aud", "acrylic-pda-temporary-activation")
            .put("authorizationId", id.toString()).put("bundleId", bundle.toString()).put("manifestSha256", sha(manifest))
            .put("serverRecoveryEpoch", "4").put("userId", user.toString()).put("deviceId", device.toString())
            .put("commandSha256", "a".repeat(64)).put("activatedAt", activated.toString()).put("acknowledgedAt", received.toString());
        ack = new TemporaryCommands.Acknowledgement(id.toString(), bundle.toString(), activated.toString(), received.toString(), issuer.sign(claims).token());
    }

    private ObjectNode manifest() {
        var value = object().put("schemaVersion", 1).put("purpose", "TEMP_WORK").put("barcodeRuleVersion", "1")
            .put("inventorySnapshotId", snapshot.toString()).put("inventoryVersion", "1").put("mappingRevision", "1")
            .put("policyVersion", "1").put("issuedAt", issued.toString()).put("expiresAt", expires.toString());
        value.putObject("identity").put("authorizationId", id.toString()).put("bundleId", bundle.toString())
            .put("deviceId", device.toString()).put("userId", user.toString()).put("warehouseId", warehouse.toString())
            .put("authorizationEpoch", "1").put("userAuthVersion", "2").put("deviceVersion", "3").put("serverRecoveryEpoch", "4");
        return value;
    }

    static OfflineSigner signer(Path directory, String label, String history) throws Exception {
        var generator = java.security.KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); var pair = generator.generateKeyPair();
        var privatePath = directory.resolve(label + "-private.der"); var publicPath = directory.resolve(label + "-public.der");
        Files.write(privatePath, pair.getPrivate().getEncoded()); Files.write(publicPath, pair.getPublic().getEncoded());
        return new OfflineSigner(privatePath.toString(), publicPath.toString(), history, new JsonText(new com.fasterxml.jackson.databind.ObjectMapper()));
    }
}
