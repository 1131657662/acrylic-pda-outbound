package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.pda.*;
import com.acrylic.outbound.pda.PreparationArchive;
import com.acrylic.outbound.policies.PolicyStore;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Issues the receiving TEMP credential from the prepared recipient baseline. */
@Component
final class HandoverTemporaryActivationIssuer {
    private final JdbcTemplate jdbc;
    private final TemporaryIssuer issuer;
    private final TemporaryStore store;
    private final PolicyStore policies;

    HandoverTemporaryActivationIssuer(JdbcTemplate jdbc, TemporaryIssuer issuer, TemporaryStore store, PolicyStore policies) {
        this.jdbc = jdbc; this.issuer = issuer; this.store = store; this.policies = policies;
    }

    Issued issue(HandoverTemporaryActivationTicketStore.Ticket ticket, UUID sessionId) {
        Target target = target(ticket.targetAuthorizationId());
        if (!target.device().equals(ticket.targetDeviceId()) || !target.user().equals(ticket.targetUserId()))
            throw conflict("HANDOVER_TEMP_TARGET_CHANGED");
        jdbc.queryForObject("SELECT id FROM device WHERE id=? FOR UPDATE", byte[].class, Ids.bytes(target.device()));
        long sequence = store.nextSequence(new PdaSessions.Principal(sessionId, target.user(), target.username(), target.device(),
            target.warehouse(), Long.toString(target.userVersion()), Long.toString(target.deviceVersion())));
        UUID authorization = Ids.next();
        var principal = new PdaSessions.Principal(sessionId, target.user(), target.username(), target.device(), target.warehouse(),
            Long.toString(target.userVersion()), Long.toString(target.deviceVersion()));
        var work = issuer.issue(new TemporaryIssuer.Binding(authorization, Ids.next(), sequence,
            Long.toString(ticket.sourceRecoveryEpoch()), principal, target.expires()), baseline(target), parts(ticket.targetAuthorizationId()));
        store.insert(work);
        UUID clientOrder = Ids.next();
        if (jdbc.update("INSERT INTO temporary_work_origin(creator_device_id,client_order_id,authorization_id) VALUES(?,?,?)",
            Ids.bytes(target.device()), Ids.bytes(clientOrder), Ids.bytes(authorization)) != 1)
            throw new IllegalStateException("HANDOVER_TEMP_ORIGIN_INSERT");
        return new Issued(authorization, clientOrder, work.receipt(), sequence, target);
    }

    private Target target(UUID authorization) {
        var rows = jdbc.query("""
            SELECT BIN_TO_UUID(a.warehouse_id),BIN_TO_UUID(a.device_id),BIN_TO_UUID(a.user_id),u.username,
              a.user_auth_version,a.device_version,BIN_TO_UUID(a.inventory_snapshot_id),a.mapping_revision,
              BIN_TO_UUID(a.policy_id),s.version,s.row_count,s.manifest_sha256,a.expires_at
            FROM temporary_authorization a JOIN app_user u ON u.id=a.user_id
              JOIN inventory_snapshot s ON s.id=a.inventory_snapshot_id AND s.warehouse_id=a.warehouse_id
            WHERE a.id=? AND s.status='ACTIVE' FOR UPDATE
            """, (rs, n) -> new Target(UUID.fromString(rs.getString(1)), UUID.fromString(rs.getString(2)),
                UUID.fromString(rs.getString(3)), rs.getString(4), rs.getLong(5), rs.getLong(6),
                UUID.fromString(rs.getString(7)), rs.getLong(8), UUID.fromString(rs.getString(9)), rs.getString(10),
                rs.getInt(11), rs.getString(12), rs.getTimestamp(13).toInstant()), Ids.bytes(authorization));
        if (rows.size() != 1) throw conflict("HANDOVER_TEMP_TARGET_AUTHORIZATION_INVALID");
        return rows.getFirst();
    }

    private TemporaryBaseline.View baseline(Target target) {
        var policy = policies.byId(target.warehouse(), target.policy());
        if (policy == null) throw conflict("HANDOVER_TEMP_TARGET_POLICY_INVALID");
        return new TemporaryBaseline.View(target.warehouse().toString(), target.snapshot().toString(), target.version(),
            Long.toString(target.mapping()), target.rows(), target.snapshotSha(), policy);
    }

    private List<PreparationArchive> parts(UUID authorization) {
        JsonNode root = SubmissionJson.parse(jdbc.queryForObject("SELECT manifest_text FROM temporary_authorization WHERE id=?",
            String.class, Ids.bytes(authorization)));
        var result = new ArrayList<PreparationArchive>();
        for (JsonNode descriptor : root.path("parts")) {
            String name = descriptor.path("name").asText();
            byte[] compressed = jdbc.queryForObject("SELECT compressed FROM temporary_preparation_part WHERE authorization_id=? AND part_name=?",
                byte[].class, Ids.bytes(authorization), name);
            if (!PreparationArchive.sha(compressed).equals(descriptor.path("compressedSha256").asText()))
                throw conflict("HANDOVER_TEMP_TARGET_PART_CHANGED");
            result.add(new PreparationArchive(compressed, new PreparationArchive.Part(name,
                descriptor.path("compressedBytes").asInt(), descriptor.path("bytes").asInt(),
                descriptor.path("compressedSha256").asText(), descriptor.path("sha256").asText(), descriptor.path("rows").asInt())));
        }
        if (result.size() != 5) throw conflict("HANDOVER_TEMP_TARGET_PARTS_INCOMPLETE");
        return List.copyOf(result);
    }

    private static BusinessException conflict(String code) { return new BusinessException(409, code, "临时交接接收资格不可核验"); }
    record Target(UUID warehouse, UUID device, UUID user, String username, long userVersion, long deviceVersion,
        UUID snapshot, long mapping, UUID policy, String version, int rows, String snapshotSha, Instant expires) { }
    record Issued(UUID authorization, UUID clientOrder, TemporaryCommands.Receipt receipt, long sequence, Target target) { }
}
