package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.pda.PdaSessions;
import com.acrylic.outbound.pda.PreparationArchive;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.TemporaryBaseline;
import com.acrylic.outbound.temporary.TemporaryIssuer;
import com.acrylic.outbound.temporary.TemporaryStore;
import com.acrylic.outbound.temporary.TemporaryCommands;
import com.acrylic.outbound.policies.PolicyStore;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Issues an independent TEMP credential from locked, immutable source facts. */
@Component
public final class TemporaryContinuationAuthorizationIssuer {
    private final JdbcTemplate jdbc;
    private final TemporaryIssuer issuer;
    private final TemporaryStore store;
    private final PolicyStore policies;

    public TemporaryContinuationAuthorizationIssuer(JdbcTemplate jdbc, TemporaryIssuer issuer,
        TemporaryStore store, PolicyStore policies) {
        this.jdbc = jdbc; this.issuer = issuer; this.store = store; this.policies = policies;
    }

    public Issued issue(CommandWrite write, UUID ticket, UUID scope, UUID submission) {
        var actor = write.actor();
        UUID user = id(actor, "principalId"), device = id(actor, "deviceId");
        if (device == null) throw conflict("CONTINUATION_TARGET_PDA_REQUIRED");
        if (!device.equals(write.context().id("freezeDeviceId"))
            || !Objects.equals(user, write.context().id("originalOperatorId")))
            throw conflict("CONTINUATION_TARGET_IDENTITY_MISMATCH");
        var source = source(ticket, scope, submission);
        if (!Arrays.equals(source.previousAuthorization(), Ids.bytes(write.context().id("authorizationId"))))
            throw conflict("CONTINUATION_SOURCE_CHANGED");
        long epoch = nextEpoch(device, user);
        UUID authorization = Ids.next(), bundle = Ids.next(), client = Ids.next();
        var principal = new PdaSessions.Principal(UUID.randomUUID(), user, "continuation",
            device, toUuid(source.warehouse()), actor.path("userAuthVersion").asText(), actor.path("deviceVersion").asText());
        var baseline = baseline(source);
        var work = issuer.issue(new TemporaryIssuer.Binding(authorization, bundle, epoch,
            Long.toString(source.recoveryEpoch()), principal), baseline, parts(source.previousAuthorization()));
        store.insert(work);
        if (jdbc.update("INSERT INTO temporary_work_origin(creator_device_id,client_order_id,authorization_id) VALUES(?,?,?)",
            Ids.bytes(device), Ids.bytes(client), Ids.bytes(authorization)) != 1)
            throw new IllegalStateException("CONTINUATION_ORIGIN_INSERT");
        return new Issued(source, authorization, bundle, client, work.receipt());
    }

    private Source source(UUID ticket, UUID scope, UUID submission) {
        var rows = jdbc.query("""
            SELECT t.warehouse_id,t.previous_authorization_id,t.source_recovery_epoch,t.evidence_set_id,
              t.from_work_round,t.next_order_scan_seq,t.retained_manifest_sha256,t.retained_manifest_version,
              t.status,a.inventory_snapshot_id,a.mapping_revision,a.policy_id
            FROM temporary_source_continuation t JOIN temporary_authorization a ON a.id=t.previous_authorization_id
            WHERE t.id=? AND t.scope_id=? AND t.previous_submission_id=? FOR UPDATE
            """, (rs, n) -> new Source(rs.getBytes(1), rs.getBytes(2), rs.getLong(3), rs.getBytes(4),
                rs.getLong(5), rs.getLong(6), rs.getString(7), rs.getLong(8), rs.getString(9),
                rs.getBytes(10), rs.getLong(11), rs.getBytes(12)), Ids.bytes(ticket), Ids.bytes(scope), Ids.bytes(submission));
        if (rows.size() != 1 || !"TICKET_ISSUED".equals(rows.getFirst().status()))
            throw conflict("CONTINUATION_TICKET_NOT_ISSUED");
        return rows.getFirst();
    }

    private TemporaryBaseline.View baseline(Source source) {
        var snapshot = jdbc.query("SELECT version,row_count,manifest_sha256,status FROM inventory_snapshot WHERE id=? AND warehouse_id=?",
            (rs, n) -> new Snapshot(rs.getString(1), rs.getInt(2), rs.getString(3), rs.getString(4)),
            source.inventorySnapshot(), source.warehouse());
        if (snapshot.size() != 1 || !"ACTIVE".equals(snapshot.getFirst().status()))
            throw conflict("CONTINUATION_BASELINE_UNAVAILABLE");
        var policy = policies.byId(toUuid(source.warehouse()), toUuid(source.policy()));
        return new TemporaryBaseline.View(toUuid(source.warehouse()).toString(), toUuid(source.inventorySnapshot()).toString(),
            snapshot.getFirst().version(), Long.toString(source.mappingRevision()), snapshot.getFirst().rows(),
            snapshot.getFirst().sha(), policy);
    }

    private List<PreparationArchive> parts(byte[] authorization) {
        var manifest = jdbc.queryForObject("SELECT manifest_text FROM temporary_authorization WHERE id=?",
            String.class, authorization);
        JsonNode root = SubmissionJson.parse(manifest);
        var result = new ArrayList<PreparationArchive>();
        for (JsonNode descriptor : root.path("parts")) {
            String name = descriptor.path("name").asText();
            byte[] compressed = jdbc.queryForObject("SELECT compressed FROM temporary_preparation_part WHERE authorization_id=? AND part_name=?",
                byte[].class, authorization, name);
            if (!PreparationArchive.sha(compressed).equals(descriptor.path("compressedSha256").asText()))
                throw conflict("CONTINUATION_BASELINE_PART_CHANGED");
            result.add(new PreparationArchive(compressed, new PreparationArchive.Part(name,
                descriptor.path("compressedBytes").asInt(), descriptor.path("bytes").asInt(),
                descriptor.path("compressedSha256").asText(), descriptor.path("sha256").asText(), descriptor.path("rows").asInt())));
        }
        if (result.size() != 5) throw conflict("CONTINUATION_BASELINE_PARTS_INCOMPLETE");
        return List.copyOf(result);
    }

    private long nextEpoch(UUID device, UUID user) {
        jdbc.queryForObject("SELECT id FROM device WHERE id=? FOR UPDATE", byte[].class, Ids.bytes(device));
        Long current = jdbc.queryForObject("SELECT COALESCE(MAX(authorization_epoch),0) FROM temporary_authorization WHERE device_id=? AND user_id=?",
            Long.class, Ids.bytes(device), Ids.bytes(user));
        try { return Math.addExact(current == null ? 0 : current, 1); }
        catch (ArithmeticException overflow) { throw conflict("CONTINUATION_AUTHORIZATION_EPOCH_INVALID"); }
    }

    private static UUID id(JsonNode actor, String key) {
        if (actor.path(key).isMissingNode() || actor.path(key).isNull()) return null;
        try { return UUID.fromString(actor.path(key).asText()); } catch (IllegalArgumentException error) { throw conflict("CONTINUATION_TARGET_INVALID"); }
    }
    private static UUID toUuid(byte[] bytes) { return new UUID(java.nio.ByteBuffer.wrap(bytes).getLong(), java.nio.ByteBuffer.wrap(bytes).getLong(8)); }
    private static BusinessException conflict(String code) { return new BusinessException(409, code, "TEMP continuation authorization cannot be issued"); }

    public record Issued(Source source, UUID authorization, UUID bundle, UUID clientOrder, TemporaryCommands.Receipt receipt) { }
    public record Source(byte[] warehouse, byte[] previousAuthorization, long recoveryEpoch, byte[] evidenceSet,
        long fromRound, long nextOrderScanSeq, String manifestSha, long manifestVersion, String status,
        byte[] inventorySnapshot, long mappingRevision, byte[] policy) {
        public Source { warehouse = warehouse.clone(); previousAuthorization = previousAuthorization.clone(); evidenceSet = evidenceSet.clone(); inventorySnapshot = inventorySnapshot.clone(); policy = policy.clone(); }
        @Override public byte[] warehouse() { return warehouse.clone(); }
        @Override public byte[] previousAuthorization() { return previousAuthorization.clone(); }
        @Override public byte[] evidenceSet() { return evidenceSet.clone(); }
        @Override public byte[] inventorySnapshot() { return inventorySnapshot.clone(); }
        @Override public byte[] policy() { return policy.clone(); }
    }
    private record Snapshot(String version, int rows, String sha, String status) { }
}
