package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.audit.AuditWriter;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.pda.PdaSessions;
import com.acrylic.outbound.policies.PolicyStore;
import com.acrylic.outbound.submissions.*;
import com.acrylic.outbound.temporary.TemporaryIssuer;
import com.acrylic.outbound.temporary.TemporaryStore;
import com.acrylic.outbound.temporary.review.TemporaryDecisionCandidates;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.Mockito.mock;

/** Actual command/receipt components over synthetic database facts, without an HTTP identity claim. */
public final class ContinuationDatabaseCommand {
    final JdbcTemplate jdbc;
    final CommandContext context;
    final CommandIdentity.Actor actor;
    final CommandStore store;
    final CommandReceiptCodec codec;
    final CommandReceiptIntegrity integrity;
    final TemporaryReopenWorkOperation operation;
    final OfflineSigner offlineSigner;
    final Map<String, String> seed;
    final ObjectNode body;

    public ContinuationDatabaseCommand(JdbcTemplate jdbc, Map<String, String> seed, Path directory) throws Exception {
        this.jdbc = jdbc; this.seed = Map.copyOf(seed);
        jdbc.update("INSERT INTO order_submission_slot(order_id,scope_id,work_round,current_submission_id,phase) "
            + "VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),1,UUID_TO_BIN(?),'FROZEN')",
            seed.get("orderId"), seed.get("mappedScopeId"), seed.get("mappedSubmissionId"));
        context = new CommandFacts(jdbc, mock(SubmissionFiles.class)).read(UUID.fromString(seed.get("mappedSubmissionId")));
        actor = new CommandIdentity.Actor(UUID.fromString(seed.get("operatorId")), context.id("warehouseId"), "1", null,
            List.of("order:resume"), List.of(context.text("warehouseId")));
        store = new CommandStore(jdbc);
        offlineSigner = signer(directory); codec = new CommandReceiptCodec(offlineSigner);
        var business = new CommandBusinessIntegrity(jdbc, mock(SubmissionFiles.class), mock(SubmissionArchiveProofs.class),
            mock(TemporaryDecisionCandidates.class), new TemporaryContinuationIntegrity(jdbc));
        integrity = new CommandReceiptIntegrity(new CommandSignature(offlineSigner), store, new CommandHistoricalIdentity(jdbc), business);
        operation = new TemporaryReopenWorkOperation(new PhysicalDispositionVerification(jdbc),
            new TemporaryContinuationTicketStore(jdbc, new TemporaryTrustedScanHighWatermark(jdbc)));
        body = command();
    }

    private OfflineSigner signer(Path directory) throws Exception {
        Files.createDirectories(directory);
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); var pair = generator.generateKeyPair();
        Files.write(directory.resolve("private.pk8"), pair.getPrivate().getEncoded(), StandardOpenOption.CREATE_NEW);
        Files.write(directory.resolve("public.der"), pair.getPublic().getEncoded(), StandardOpenOption.CREATE_NEW);
        return new OfflineSigner(directory.resolve("private.pk8").toString(), directory.resolve("public.der").toString(),
            new JsonText(new ObjectMapper()));
    }

    private ObjectNode command() {
        var body = SubmissionJson.object().put("commandVersion", 1).put("commandType", "REOPEN_WORK")
            .put("commandId", UUID.randomUUID().toString()).put("transportRecoveryEpoch", "1")
            .put("reason", "Synthetic retained source check").put("nextOrderScanSeq", "5");
        for (String key : List.of("warehouseId", "submissionId", "workRound", "authorizationId", "freezeDeviceId", "sourceRecoveryEpoch"))
            body.set(key, context.json().get(key));
        body.putObject("source").put("kind", "TEMP").put("creatorDeviceId", context.text("creatorDeviceId"))
            .put("clientOrderId", context.text("clientOrderId"));
        var expected = CommandExpected.current(context, "1");
        for (String key : List.of("evidenceSetId", "evidenceSetSha256", "verificationId", "verificationVersion", "verificationAlgorithmVersion"))
            expected.set(key, context.json().get(key));
        var manifest = manifest(); body.set("retainedManifest", manifest);
        expected.put("retainedManifestSha256", SubmissionJson.sha(manifest)).put("retainedManifestVersion", "1");
        body.set("expected", expected); return body;
    }

    private ObjectNode manifest() {
        var result = SubmissionJson.object().put("version", 1); var items = result.putArray("items");
        jdbc.query("SELECT BIN_TO_UUID(bale_id),original_cycle,record_sha256 FROM source_evidence_item "
            + "WHERE set_id=? AND item_kind='SCAN' AND record_status='ACTIVE' ORDER BY bale_id", (rs, n) -> {
                items.addObject().put("baleId", rs.getString(1)).put("expectedDispatchCycle", rs.getString(2))
                    .put("recordSha256", rs.getString(3)); return rs.getString(1);
            }, Ids.bytes(context.id("evidenceSetId")));
        return result;
    }

    CommandInput input() { return CommandInput.parse(CommandReceiptCodec.bytes(body)); }

    CommandWrite write() {
        var input = input(); CommandExpected.validate(input, context, actor);
        CommandPermissions.requireWrite(actor.permissions(), input.kind());
        var snapshot = SubmissionJson.object().put("principalId", actor.id().toString()).put("userAuthVersion", "1")
            .putNull("deviceId").putNull("sessionId").putNull("deviceVersion");
        var permissions = snapshot.putObject("permissionSnapshot");
        permissions.putArray("requiredPermissions").add("order:resume");
        permissions.putArray("effectivePermissions").add("order:resume");
        permissions.putArray("warehouseIds").add(context.text("warehouseId"));
        permissions.putArray("roleIds").add(UUID.randomUUID().toString());
        return new CommandWrite(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            Instant.now().truncatedTo(ChronoUnit.MILLIS), input, context, snapshot);
    }

    public CommandReceiptCodec.Receipt execute() {
        var write = write(); store.reserve(write); store.advance(write);
        var receipt = codec.sign(write, operation.execute(write));
        store.receipt(write, receipt); store.event(write, receipt);
        var details = SubmissionJson.object().put("commandSha256", write.command().digest())
            .put("originalResponseSha256", receipt.originalSha256());
        new AuditWriter(jdbc).append(new AuditWriter.Event(actor.id(), "TEMPORARY_REOPEN_WORK", "TEMPORARY_COMMAND",
            write.receiptId(), details.toString()), UUID.randomUUID().toString());
        return receipt;
    }

    public CommandReceiptCodec.Receipt activate(UUID ticket, Path directory) throws Exception {
        var current = new CommandFacts(jdbc, mock(SubmissionFiles.class)).read(UUID.fromString(seed.get("mappedSubmissionId")));
        var input = CommandInput.parse(CommandReceiptCodec.bytes(activationBody(current, ticket)));
        var actor = new CommandIdentity.Actor(UUID.fromString(seed.get("operatorId")), current.id("warehouseId"),
            current.text("authorizationUserVersion"), null, List.of("order:resume"), List.of(current.text("warehouseId")));
        CommandExpected.validate(input, current, actor);
        UUID session = insertActivationSession(actor.id(), UUID.fromString(seed.get("deviceId")));
        var snapshot = SubmissionJson.object().put("principalId", actor.id().toString())
            .put("userAuthVersion", current.text("authorizationUserVersion"))
            .put("deviceId", seed.get("deviceId")).put("sessionId", session.toString())
            .put("deviceVersion", current.text("authorizationDeviceVersion"));
        var permissions = snapshot.putObject("permissionSnapshot");
        permissions.putArray("requiredPermissions").add("order:resume");
        permissions.putArray("effectivePermissions").add("order:resume");
        permissions.putArray("warehouseIds").add(current.text("warehouseId"));
        permissions.putArray("roleIds").add(UUID.randomUUID().toString());
        var write = new CommandWrite(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            Instant.now().truncatedTo(ChronoUnit.MILLIS), input, current, snapshot);
        store.reserve(write); store.advance(write);
        var issuer = new TemporaryContinuationAuthorizationIssuer(jdbc,
            new TemporaryIssuer(offlineSigner, new JsonText(new ObjectMapper())), new TemporaryStore(jdbc), new PolicyStore(jdbc));
        var activation = new TemporaryContinuationActivationOperation(jdbc, issuer);
        var receipt = codec.sign(write, activation.execute(write));
        store.receipt(write, receipt); store.event(write, receipt);
        new AuditWriter(jdbc).append(new AuditWriter.Event(actor.id(), "TEMPORARY_ACTIVATE_CONTINUATION",
            "TEMPORARY_COMMAND", write.receiptId(), SubmissionJson.object().put("commandSha256", write.command().digest()).toString()),
            UUID.randomUUID().toString());
        return receipt;
    }

    private UUID insertActivationSession(UUID user, UUID device) {
        UUID session = UUID.randomUUID();
        String suffix = session.toString().replace("-", "");
        jdbc.update("""
            INSERT INTO pda_session(id,device_id,user_id,user_auth_version,device_version,access_hash,refresh_hash,
              access_expires_at,expires_at,revoked_at,created_at)
            VALUES(?,?,?,?,?,?,?,DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 15 MINUTE),DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 1 DAY),NULL,UTC_TIMESTAMP(3))
            """, Ids.bytes(session), Ids.bytes(device), Ids.bytes(user), 1, 1,
            suffix + "a".repeat(32), suffix + "b".repeat(32));
        return session;
    }

    private ObjectNode activationBody(CommandContext current, UUID ticket) {
        var body = SubmissionJson.object().put("commandVersion", 1).put("commandType", "ACTIVATE_CONTINUATION")
            .put("commandId", UUID.randomUUID().toString()).put("transportRecoveryEpoch", "1")
            .put("reason", "Synthetic continuation activation").put("continuationTicketId", ticket.toString());
        for (String key : List.of("warehouseId", "submissionId", "workRound", "authorizationId", "freezeDeviceId", "sourceRecoveryEpoch"))
            body.set(key, current.json().get(key));
        body.put("nextOrderScanSeq", jdbc.queryForObject("SELECT CAST(next_order_scan_seq AS CHAR) FROM temporary_source_continuation WHERE id=?",
            String.class, Ids.bytes(ticket)));
        body.putObject("source").put("kind", "TEMP").put("creatorDeviceId", current.text("creatorDeviceId"))
            .put("clientOrderId", current.text("clientOrderId"));
        var expected = CommandExpected.current(current, current.text("authorizationUserVersion"));
        for (String key : List.of("evidenceSetId", "evidenceSetSha256", "verificationId", "verificationVersion", "verificationAlgorithmVersion"))
            expected.set(key, current.json().get(key));
        var manifest = manifest(current.id("evidenceSetId"));
        body.set("retainedManifest", manifest);
        expected.put("retainedManifestSha256", SubmissionJson.sha(manifest)).put("retainedManifestVersion", "1");
        body.set("expected", expected); return body;
    }

    private ObjectNode manifest(UUID set) {
        var result = SubmissionJson.object().put("version", 1); var items = result.putArray("items");
        jdbc.query("SELECT BIN_TO_UUID(bale_id),original_cycle,record_sha256 FROM source_evidence_item WHERE set_id=? AND item_kind='SCAN' AND record_status='ACTIVE' ORDER BY bale_id",
            (rs, n) -> { items.addObject().put("baleId", rs.getString(1)).put("expectedDispatchCycle", rs.getString(2)).put("recordSha256", rs.getString(3)); return rs.getString(1); },
            Ids.bytes(set));
        return result;
    }
}
