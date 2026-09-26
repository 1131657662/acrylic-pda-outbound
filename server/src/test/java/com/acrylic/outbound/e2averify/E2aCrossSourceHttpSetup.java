package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.devices.DeviceCrypto;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.protocol.E2aPageItem;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

final class E2aCrossSourceHttpSetup {
    final E2aCrossSourceFixture fixture;
    final KeyPair deviceKey;
    final String token = DeviceCrypto.secret();
    final ObjectNode audit;
    final List<ObjectNode> extraRecords, extraAudits;

    E2aCrossSourceHttpSetup(E2aCrossSourceFixture fixture) throws Exception {
        this(fixture, List.of(), List.of());
    }

    E2aCrossSourceHttpSetup(E2aCrossSourceFixture fixture, List<ObjectNode> records, List<ObjectNode> events) throws Exception {
        this.fixture = fixture;
        this.extraRecords = List.copyOf(records); this.extraAudits = List.copyOf(events);
        var generator = KeyPairGenerator.getInstance("EC"); generator.initialize(new ECGenParameterSpec("secp256r1"));
        deviceKey = generator.generateKeyPair(); audit = audit();
        String publicKey = Base64.getEncoder().encodeToString(deviceKey.getPublic().getEncoded());
        fixture.jdbc.update("UPDATE device SET public_key=?,key_sha256=? WHERE id=?", publicKey,
            DeviceCrypto.sha(publicKey), fixture.id("deviceId"));
        fixture.jdbc.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code='PDA_OPERATOR'", fixture.id("operatorId"));
        session();
        if (extraAudits.isEmpty()) storeAudit();
    }

    List<E2aPageItem> items() {
        var items = new ArrayList<>(fixture.items());
        extraRecords.forEach(record -> items.add(E2aCrossSourceFixture.item(record.has("state") ? "CAPTURE" : "SCAN", record)));
        extraAudits.forEach(event -> items.add(E2aCrossSourceFixture.item("AUDIT", event)));
        items.add(E2aCrossSourceFixture.item("AUDIT", audit));
        return List.copyOf(items);
    }

    List<byte[]> rawBytes() {
        var records = new ArrayList<>(List.of(fixture.earlier, fixture.priorScan, fixture.current, fixture.ownScan));
        records.addAll(extraRecords); records.addAll(extraAudits); records.add(audit);
        return records.stream().map(E2aCrossSourceFixture::bytes).toList();
    }

    static void signing(Path attempt) throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); var pair = generator.generateKeyPair();
        Files.write(attempt.resolve("private/server-private.der"), pair.getPrivate().getEncoded(), StandardOpenOption.CREATE_NEW);
        Files.write(attempt.resolve("private/server-public.der"), pair.getPublic().getEncoded(), StandardOpenOption.CREATE_NEW);
    }

    private void session() {
        fixture.jdbc.update("""
            INSERT INTO pda_session(id,device_id,user_id,user_auth_version,device_version,access_hash,refresh_hash,
              access_expires_at,expires_at,created_at)
            VALUES(?,?,?,1,1,?,?,DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 1 DAY),
              DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 2 DAY),UTC_TIMESTAMP(3))
            """, Ids.bytes(java.util.UUID.randomUUID()), fixture.id("deviceId"), fixture.id("operatorId"),
            DeviceCrypto.sha(token), DeviceCrypto.sha(DeviceCrypto.secret()));
    }

    private ObjectNode audit() {
        var event = SubmissionJson.object().put("id", java.util.UUID.randomUUID().toString()).putNull("captureId")
            .put("originDeviceId", fixture.seed.get("deviceId")).put("operatorId", fixture.seed.get("operatorId"))
            .put("localSeq", Integer.toString(extraAudits.size() + 1)).put("type", "STOP").put("detail", fixture.submission.toString())
            .put("wallMs", "1").put("elapsedMs", "1").put("boot", 1)
            .put("authorizationId", fixture.seed.get("authorizationId"));
        event.set("source", SubmissionJson.object().put("kind", "TEMP")
            .put("creatorDeviceId", fixture.seed.get("deviceId")).put("clientOrderId", fixture.client.toString()));
        return event;
    }

    private void storeAudit() {
        fixture.jdbc.update("""
            INSERT INTO pda_audit_event(id,origin_device_id,local_seq,warehouse_id,operator_id,source_recovery_epoch,
              record_sha256,payload,uploaded_by,received_at,temporary_authorization_id,client_order_id)
            VALUES(?,?,1,?,?,1,?,?,?,UTC_TIMESTAMP(3),?,?)
            """, Ids.bytes(SubmissionJson.uuid(audit, "id")), fixture.id("deviceId"), fixture.id("warehouseId"),
            fixture.id("operatorId"), SubmissionJson.sha(audit), SubmissionJson.canonical(audit), fixture.id("operatorId"),
            fixture.id("authorizationId"), Ids.bytes(fixture.client));
    }
}
