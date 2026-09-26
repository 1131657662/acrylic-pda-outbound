package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.common.JsonText;
import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeObjectFiles;
import com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeOpen;
import com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeReceiptCodec;
import com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeReceipts;
import com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeReplay;
import com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeStop;
import com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeStopObjects;
import com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeStopReceipts;
import com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeTransactions;
import com.acrylic.outbound.temporary.protocol.E2aProtocol;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

final class E2aV36StopWriterChecks {
    private E2aV36StopWriterChecks() { }

    static void verify(JdbcTemplate jdbc, Map<String, String> seed, Path storage) throws Exception {
        var transactions = new SourceEvidenceIntakeTransactions(new DataSourceTransactionManager(jdbc.getDataSource()));
        var opens = new SourceEvidenceIntakeReceipts(jdbc);
        var stops = new SourceEvidenceIntakeStopReceipts(jdbc);
        var objects = new SourceEvidenceIntakeStopObjects(jdbc);
        var files = new SourceEvidenceIntakeObjectFiles(storage.toString());
        var signer = signer(storage.getParent().resolve("private"));
        var codec = new SourceEvidenceIntakeReceiptCodec(signer);
        UUID session = session(jdbc, seed), openKey = UUID.randomUUID(), stopKey = UUID.randomUUID();
        var open = open(seed);
        transactions.write(status -> {
            opens.insertOpen(context(seed, session, openKey, open.canonicalBytes(), open.requestSha256()),
                codec.open(Ids.next(), openKey, open,
                    context(seed, session, openKey, open.canonicalBytes(), open.requestSha256()), Instant.now()));
            return null;
        });
        var boundOpen = bound(seed, openKey);
        var persistedOpen = transactions.read(status -> opens.openBound(boundOpen));
        require(Arrays.equals(persistedOpen.canonicalBytes(), open.canonicalBytes()), "OPEN did not survive JDBC");
        var deviceKeys = KeyPairGenerator.getInstance("EC");
        deviceKeys.initialize(new ECGenParameterSpec("secp256r1"));
        var pair = deviceKeys.generateKeyPair();
        var stop = stop(seed, openKey, open, pair, "stop-original");
        byte[] proof = stop.canonicalStopProofBytes();
        var stored = files.store(new ByteArrayInputStream(proof), proof.length, ImportFiles.sha256(proof));
        var binding = binding(seed, openKey);
        var saved = transactions.write(status -> {
            var object = objects.insert(binding, stop.stopEventId(), stored, Instant.now());
            var context = context(seed, session, stopKey, stop.canonicalBytes(), stop.requestSha256());
            var receipt = codec.stop(Ids.next(), stopKey, open, stop, context, object, Instant.now());
            stops.insert(context, object.id(), receipt);
            return receipt;
        });
        require(Arrays.equals(saved.originalResponseBytes(), stops.existing(bound(seed, stopKey),
            stop.canonicalBytes()).originalResponseBytes()), "Original STOP bytes changed");
        require(stops.existing(bound(seed, stopKey), stop.canonicalBytes()).status()
            == SourceEvidenceIntakeReplay.Status.REPLAY, "STOP replay failed");
        require(stops.existing(bound(seed, stopKey), "{}".getBytes(StandardCharsets.UTF_8)).status()
            == SourceEvidenceIntakeReplay.Status.CONFLICT, "Different STOP bytes replayed");
        var original = transactions.read(status -> objects.findAfterSourceLock(binding, stop.stopEventId()));
        require(original != null && original.stored().equals(stored), "STOP object was not persisted");
        files.verify(original.stored());
        rollback(transactions, jdbc, seed, openKey, open, pair, objects, files, binding);
    }

    private static void rollback(SourceEvidenceIntakeTransactions transactions, JdbcTemplate jdbc,
        Map<String, String> seed, UUID openKey, SourceEvidenceIntakeOpen.Parsed open,
        java.security.KeyPair pair, SourceEvidenceIntakeStopObjects objects,
        SourceEvidenceIntakeObjectFiles files, SourceEvidenceIntakeStopObjects.Binding binding) throws Exception {
        var failed = stop(seed, openKey, open, pair, "stop-rollback");
        byte[] proof = failed.canonicalStopProofBytes();
        var durable = files.store(new ByteArrayInputStream(proof), proof.length, ImportFiles.sha256(proof));
        int objectsBefore = count(jdbc, "source_evidence_object"), receiptsBefore = count(jdbc, "source_evidence_intake_receipt");
        try {
            transactions.write(status -> {
                objects.insert(binding, failed.stopEventId(), durable, Instant.now());
                throw new IllegalStateException("intentional post-object rollback");
            });
            throw new AssertionError("STOP write did not roll back");
        } catch (IllegalStateException expected) {
            require(expected.getMessage().equals("intentional post-object rollback"), "Wrong rollback failure");
        }
        require(count(jdbc, "source_evidence_object") == objectsBefore, "Rolled-back STOP object persisted");
        require(count(jdbc, "source_evidence_intake_receipt") == receiptsBefore, "Rolled-back STOP receipt persisted");
        require(transactions.read(status -> objects.findAfterSourceLock(binding, failed.stopEventId())) == null,
            "Rolled-back event is visible");
        files.verify(durable);
    }

    private static SourceEvidenceIntakeStop.Parsed stop(Map<String, String> seed, UUID openKey,
        SourceEvidenceIntakeOpen.Parsed open, java.security.KeyPair pair, String event) {
        var page = SubmissionJson.object().put("manifestId", "stop-it").put("manifestVersion", 1)
            .put("snapshotId", "snapshot").put("pageNo", 1).put("pageCount", 1)
            .put("isFinal", true).put("itemCount", 0).put("byteCount", 0).putNull("prevPageHash");
        page.putArray("items");
        var manifest = SubmissionJson.object().put("schemaVersion", 1).put("manifestId", "stop-it")
            .put("manifestVersion", 1).put("snapshotId", "snapshot").put("source", "TEMP")
            .put("sourceId", seed.get("authorizationId")).put("submissionId", seed.get("mappedSubmissionId"))
            .put("workRound", 1).put("pageCount", 1).put("entryCount", 0).put("byteCount", 0)
            .put("endPageNo", 1);
        manifest.putArray("pages").add(SubmissionJson.object().put("pageNo", 1)
            .put("pageHash", E2aProtocol.pageHash(page)));
        var hashes = E2aProtocol.manifestHashes(manifest);
        var core = SubmissionJson.object().put("warehouseId", seed.get("warehouseId"))
            .put("sourceId", seed.get("authorizationId")).put("submissionId", seed.get("mappedSubmissionId"))
            .put("workRound", 1).put("originalOperatorId", seed.get("operatorId"))
            .put("freezeDeviceId", seed.get("deviceId")).put("sourceRecoveryEpoch", 1)
            .put("stopEventId", event).put("captureHighWatermark", 0).put("auditThroughSeq", 1)
            .put("stopTransactionVersion", 1).put("manifestId", "stop-it").put("manifestVersion", 1)
            .put("manifestBytesHash", hashes.manifestBytesHash()).put("setHash", hashes.setHash());
        var request = SubmissionJson.object().put("operation", "STOP").put("openOriginalKey", openKey.toString())
            .put("manifestBytesHash", hashes.manifestBytesHash()).put("setHash", hashes.setHash());
        request.set("manifestCore", manifest);
        request.set("stopProof", E2aProtocol.signStop(core, pair.getPrivate(),
            ImportFiles.sha256(pair.getPublic().getEncoded())));
        return SourceEvidenceIntakeStop.parse(bytes(request), new SourceEvidenceIntakeStop.Expected(openKey,
            open, id(seed, "warehouseId"), id(seed, "operatorId"), id(seed, "deviceId"), 1, pair.getPublic()));
    }

    private static SourceEvidenceIntakeOpen.Parsed open(Map<String, String> seed) {
        var request = SubmissionJson.object().put("operation", "OPEN").put("manifestId", "stop-it")
            .put("manifestVersion", 1).put("snapshotId", "snapshot").put("source", "TEMP")
            .put("sourceId", seed.get("authorizationId")).put("submissionId", seed.get("mappedSubmissionId"))
            .put("workRound", 1);
        return SourceEvidenceIntakeOpen.parse(bytes(request), id(seed, "mappedSubmissionId"), 1);
    }

    private static UUID session(JdbcTemplate jdbc, Map<String, String> seed) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO pda_session(id,device_id,user_id,user_auth_version,device_version,access_hash,"
            + "refresh_hash,access_expires_at,expires_at,created_at) VALUES(?,?,?,1,1,?,? ,"
            + "DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 1 DAY),DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 1 DAY),UTC_TIMESTAMP(3))",
            Ids.bytes(id), Ids.bytes(id(seed, "deviceId")), Ids.bytes(id(seed, "operatorId")),
            ImportFiles.sha256(bytes(SubmissionJson.object().put("session", id.toString()))),
            ImportFiles.sha256(bytes(SubmissionJson.object().put("refresh", id.toString()))));
        return id;
    }

    private static OfflineSigner signer(Path privatePath) throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        var pair = generator.generateKeyPair();
        Path privateKey = privatePath.resolve("stop-it-private.pk8"), publicKey = privatePath.resolve("stop-it-public.der");
        Files.write(privateKey, pair.getPrivate().getEncoded(), java.nio.file.StandardOpenOption.CREATE_NEW);
        Files.write(publicKey, pair.getPublic().getEncoded(), java.nio.file.StandardOpenOption.CREATE_NEW);
        return new OfflineSigner(privateKey.toString(), publicKey.toString(), new JsonText(new ObjectMapper()));
    }

    private static SourceEvidenceIntakeReceipts.OpenInsert context(Map<String, String> seed,
        UUID session, UUID key, byte[] request, String hash) {
        return new SourceEvidenceIntakeReceipts.OpenInsert(id(seed, "operatorId"), key, id(seed, "warehouseId"),
            id(seed, "mappedScopeId"), id(seed, "mappedSubmissionId"), 1, id(seed, "deviceId"),
            id(seed, "mappedClientOrderId"), id(seed, "authorizationId"), id(seed, "operatorId"), 1,
            session, "1", "1", request, hash);
    }

    private static SourceEvidenceIntakeStopObjects.Binding binding(Map<String, String> seed, UUID open) {
        return new SourceEvidenceIntakeStopObjects.Binding(id(seed, "warehouseId"), id(seed, "mappedScopeId"),
            id(seed, "mappedSubmissionId"), 1, id(seed, "deviceId"), id(seed, "operatorId"), open);
    }

    private static SourceEvidenceIntakeReceipts.BoundKey bound(Map<String, String> seed, UUID key) {
        return new SourceEvidenceIntakeReceipts.BoundKey(id(seed, "operatorId"), key, id(seed, "warehouseId"),
            id(seed, "mappedSubmissionId"), 1, id(seed, "deviceId"), id(seed, "authorizationId"));
    }

    private static UUID id(Map<String, String> seed, String field) { return UUID.fromString(seed.get(field)); }
    private static byte[] bytes(ObjectNode value) { return value.toString().getBytes(StandardCharsets.UTF_8); }
    private static int count(JdbcTemplate jdbc, String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
