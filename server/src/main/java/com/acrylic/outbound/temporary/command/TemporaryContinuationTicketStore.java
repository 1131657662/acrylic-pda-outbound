package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

/** Persists an ABORTED TEMP continuation ticket without moving the shared bale owner. */
@Repository
public class TemporaryContinuationTicketStore {
    private final JdbcTemplate jdbc;
    private final TemporaryTrustedScanHighWatermark highWatermarks;
    public TemporaryContinuationTicketStore(JdbcTemplate jdbc, TemporaryTrustedScanHighWatermark highWatermarks) {
        this.jdbc = jdbc; this.highWatermarks = highWatermarks;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Ticket issue(CommandWrite write, PhysicalDispositionVerification.Verified verified,
        List<TemporaryRetainedManifest.Item> items, String manifestHash, long manifestVersion,
        long nextOrderScanSeq, Instant recordedAt) {
        if (!"ABORTED".equals(verified.status())) throw conflict("CONTINUATION_ABORT_REQUIRED");
        var history = history(verified);
        var expected = history.stream().map(row -> new TemporaryRetainedManifest.Expected(row.bale(), row.cycle(), row.recordSha256())).toList();
        TemporaryRetainedManifest.require(manifestNode(items, manifestHash, manifestVersion), expected, manifestHash, manifestVersion);
        highWatermarks.requireNext(verified.set(), verified.scope(), verified.submission(), verified.round(), nextOrderScanSeq);
        UUID existing = existing(verified.scope(), verified.round());
        if (existing != null) throw conflict("CONTINUATION_ALREADY_ISSUED");
        UUID ticket = Ids.next();
        try {
            int inserted = jdbc.update("""
                INSERT INTO temporary_source_continuation
                (id,warehouse_id,scope_id,previous_submission_id,previous_authorization_id,previous_operator_id,
                 freeze_device_id,source_recovery_epoch,from_work_round,to_work_round,evidence_set_id,evidence_verification_id,
                 retained_manifest_sha256,retained_manifest_version,next_order_scan_seq,command_receipt_id,command_sha256,
                 status,new_authorization_id,command_text,receipt_text,created_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'TICKET_ISSUED',NULL,?,NULL,?)
                """, Ids.bytes(ticket), Ids.bytes(verified.warehouse()), Ids.bytes(verified.scope()), Ids.bytes(verified.submission()),
                Ids.bytes(write.context().id("authorizationId")), Ids.bytes(write.context().id("originalOperatorId")),
                Ids.bytes(write.context().id("freezeDeviceId")), verifiedRoundEpoch(write), verified.round(), Math.addExact(verified.round(), 1),
                Ids.bytes(verified.set()), Ids.bytes(verified.verification()), manifestHash, manifestVersion, nextOrderScanSeq,
                Ids.bytes(write.receiptId()), write.command().digest(), new String(write.command().bytes(), StandardCharsets.UTF_8),
                Timestamp.from(recordedAt));
            if (inserted != 1) throw new IllegalStateException("CONTINUATION_TICKET_INSERT");
            for (var row : history) insertBale(ticket, verified.scope(), verified.round(), row);
            return new Ticket(ticket, verified.round(), Math.addExact(verified.round(), 1), items);
        } catch (DuplicateKeyException conflict) { throw conflict("CONTINUATION_ALREADY_ISSUED"); }
    }

    private long verifiedRoundEpoch(CommandWrite write) {
        return write.context().version("sourceRecoveryEpoch");
    }

    private UUID existing(UUID scope, long round) {
        var rows = jdbc.query("SELECT id FROM temporary_source_continuation WHERE scope_id=? AND from_work_round=? FOR UPDATE",
            (rs, n) -> toUuid(rs.getBytes(1)), Ids.bytes(scope), round);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private List<History> history(PhysicalDispositionVerification.Verified verified) {
        var rows = jdbc.query("""
            SELECT h.disposition_id,h.bale_id,h.dispatch_cycle,h.action,i.record_sha256
            FROM temporary_physical_disposition_history h
            JOIN temporary_physical_disposition d ON d.id=h.disposition_id
            JOIN source_evidence_item i ON i.set_id=d.evidence_set_id AND i.scope_id=h.scope_id
              AND i.submission_id=h.submission_id AND i.work_round=h.work_round AND i.bale_id=h.bale_id
              AND i.item_kind='SCAN' AND i.record_status='ACTIVE' AND i.original_cycle=h.dispatch_cycle
            WHERE h.scope_id=? AND h.submission_id=? AND h.work_round=? AND d.evidence_set_id=?
              AND d.evidence_verification_id=? AND d.close_mode=FALSE
            ORDER BY h.bale_id FOR UPDATE
            """, (rs, n) -> new History(toUuid(rs.getBytes(1)), toUuid(rs.getBytes(2)), rs.getLong(3),
                rs.getString(4), rs.getString(5)), Ids.bytes(verified.scope()), Ids.bytes(verified.submission()), verified.round(),
                Ids.bytes(verified.set()), Ids.bytes(verified.verification()));
        if (rows.isEmpty() || rows.stream().anyMatch(row -> !"RETAIN".equals(row.action())))
            throw conflict("CONTINUATION_RETAINED_SET_INVALID");
        var ids = rows.stream().map(History::bale).toList();
        if (new HashSet<>(ids).size() != ids.size()
            || !new HashSet<>(verified.activeBales()).equals(new HashSet<>(ids)))
            throw conflict("CONTINUATION_RETAINED_SET_INVALID");
        return List.copyOf(rows);
    }

    private void insertBale(UUID ticket, UUID scope, long round, History row) {
        jdbc.update("""
            INSERT INTO temporary_source_continuation_bale
            (continuation_id,scope_id,from_work_round,disposition_id,bale_id,expected_cycle,record_sha256)
            VALUES(?,?,?,?,?,?,?)
            """, Ids.bytes(ticket), Ids.bytes(scope), round, Ids.bytes(row.disposition()), Ids.bytes(row.bale()), row.cycle(), row.recordSha256());
    }

    private static com.fasterxml.jackson.databind.JsonNode manifestNode(List<TemporaryRetainedManifest.Item> items,
        String hash, long version) {
        var manifest = SubmissionJson.object().put("version", version); var values = manifest.putArray("items");
        for (var item : items) values.add(SubmissionJson.object().put("baleId", item.baleId().toString())
            .put("expectedDispatchCycle", Long.toString(item.cycle())).put("recordSha256", item.recordSha256()));
        if (!SubmissionJson.sha(manifest).equals(hash)) throw conflict("RETAINED_MANIFEST_HASH_CONFLICT");
        return manifest;
    }

    private static UUID toUuid(byte[] bytes) {
        if (bytes == null || bytes.length != 16) throw conflict("CONTINUATION_RETAINED_SET_INVALID");
        var buffer = java.nio.ByteBuffer.wrap(bytes); return new UUID(buffer.getLong(), buffer.getLong());
    }
    private static BusinessException conflict(String code) { return new BusinessException(409, code, "TEMP continuation ticket cannot be issued"); }
    private record History(UUID disposition, UUID bale, long cycle, String action, String recordSha256) { }
    public record Ticket(UUID id, long fromRound, long toRound, List<TemporaryRetainedManifest.Item> items) { }
}
