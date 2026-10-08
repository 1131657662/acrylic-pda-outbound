package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Reads the trusted stop verification and exact rejected-source reservations under one transaction. */
@Repository
public class PhysicalDispositionVerification {
    private final JdbcTemplate jdbc;
    public PhysicalDispositionVerification(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Verified read(UUID warehouse, UUID scope, UUID submission, long round, UUID set, UUID verification,
        String setHash, long verificationVersion, long algorithmVersion) {
        return read(warehouse, scope, submission, round, set, verification, setHash, verificationVersion, algorithmVersion, Mode.REJECTED);
    }

    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Verified readForClose(UUID warehouse, UUID scope, UUID submission, long round, UUID set, UUID verification,
        String setHash, long verificationVersion, long algorithmVersion) {
        return read(warehouse, scope, submission, round, set, verification, setHash, verificationVersion, algorithmVersion, Mode.CLOSE);
    }

    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Verified readForContinuation(UUID warehouse, UUID scope, UUID submission, long round, UUID set, UUID verification,
        String setHash, long verificationVersion, long algorithmVersion) {
        return read(warehouse, scope, submission, round, set, verification, setHash, verificationVersion, algorithmVersion, Mode.ABORTED_CONTINUATION);
    }

    private Verified read(UUID warehouse, UUID scope, UUID submission, long round, UUID set, UUID verification,
        String setHash, long verificationVersion, long algorithmVersion, Mode mode) {
        var rows = jdbc.queryForList("""
            SELECT s.status AS submission_status,s.issues_json,result,set_sha256,verification_version,algorithm_version
            FROM source_evidence_verification v JOIN submission s ON s.id=v.submission_id
            WHERE v.id=? AND v.set_id=? AND v.scope_id=? AND v.submission_id=? AND v.work_round=? FOR UPDATE
        """, Ids.bytes(verification), Ids.bytes(set), Ids.bytes(scope), Ids.bytes(submission), round);
        if (rows.size() != 1) throw conflict("DISPOSITION_VERIFICATION_MISSING");
        var row = rows.getFirst();
        String status = Objects.toString(row.get("submission_status"), "");
        switch (mode) {
            case REJECTED -> requireRejected(row);
            case CLOSE -> requireCloseable(row);
            case ABORTED_CONTINUATION -> requireAborted(status);
        }
        if (!"VERIFIED_COMPLETE_AT_STOP".equals(row.get("result"))
            || !Objects.equals(setHash, row.get("set_sha256")) || number(row.get("verification_version")) != verificationVersion
            || number(row.get("algorithm_version")) != algorithmVersion)
            throw conflict("DISPOSITION_VERIFICATION_NOT_COMPLETE");
        var proof = new Verified(warehouse, scope, submission, round, set, verification, status, setHash,
            verificationVersion, algorithmVersion, List.of());
        return new Verified(warehouse, scope, submission, round, set, verification, status, setHash,
            verificationVersion, algorithmVersion, reservations(proof));
    }

    private List<UUID> reservations(Verified proof) {
        var expected = new ArrayList<>(jdbc.query("""
            SELECT bale_id,original_cycle FROM source_evidence_item
            WHERE set_id=? AND scope_id=? AND submission_id=? AND work_round=? AND warehouse_id=?
              AND item_kind='SCAN' AND record_status='ACTIVE' ORDER BY bale_id FOR UPDATE
            """, (rs, n) -> new Reservation(uuid(rs.getBytes(1)), rs.getLong(2)), Ids.bytes(proof.set()),
            Ids.bytes(proof.scope()), Ids.bytes(proof.submission()), proof.round(), Ids.bytes(proof.warehouse())));
        expected.addAll(inherited(proof));
        expected.addAll(continued(proof));
        var actual = jdbc.query("""
            SELECT k.bale_id,k.dispatch_cycle,b.dispatch_cycle AS current_cycle
            FROM bale_commitment k JOIN bale b ON b.id=k.bale_id JOIN submission_scope c ON c.id=k.source_scope_id
            WHERE k.source_scope_id=? AND k.source_warehouse_id=? AND b.warehouse_id=? AND k.disposition_id IS NULL
              AND ((k.state='SOURCE_RESERVED' AND k.source_evidence_set_id=? AND k.submission_id IS NULL)
                OR (k.state='REVIEW_RESERVED' AND k.submission_id=? AND k.source_evidence_set_id IS NULL
                    AND k.order_id=c.canonical_order_id)) ORDER BY k.bale_id FOR UPDATE
            """, (rs, n) -> {
                if (rs.getLong(2) != rs.getLong(3)) throw conflict("DISPOSITION_RESERVATION_CORRUPT");
                return new Reservation(uuid(rs.getBytes(1)), rs.getLong(2));
            }, Ids.bytes(proof.scope()), Ids.bytes(proof.warehouse()), Ids.bytes(proof.warehouse()),
            Ids.bytes(proof.set()), Ids.bytes(proof.submission()));
        if (actual.isEmpty()) throw conflict("DISPOSITION_RESERVATIONS_MISSING");
        var order = Comparator.comparing(Reservation::bale);
        if (!expected.stream().sorted(order).toList().equals(actual.stream().sorted(order).toList())
            || expected.stream().map(Reservation::bale).distinct().count() != expected.size())
            throw conflict("DISPOSITION_RESERVATION_CORRUPT");
        return expected.stream().map(Reservation::bale).sorted().toList();
    }

    private List<Reservation> inherited(Verified proof) {
        if(proof.round()==1)return List.of();
        // Receiving-device pages attest only its suffix. The immutable activation retains earlier owners.
        return jdbc.query("""
            SELECT r.bale_id,r.dispatch_cycle
            FROM temporary_handover_activation_owner r
            JOIN temporary_handover_submission s ON s.ticket_id=r.ticket_id
            JOIN temporary_handover_activation_ticket t ON t.id=s.ticket_id AND t.scope_id=s.scope_id
              AND t.from_work_round=s.from_work_round AND t.to_work_round=s.to_work_round
            JOIN handover_case h ON h.id=t.case_id AND h.scope_id=s.scope_id
            WHERE s.scope_id=? AND s.submission_id=? AND s.to_work_round=? AND h.warehouse_id=?
              AND h.status='ACTIVATED' AND h.source_kind='TEMP'
              AND NOT EXISTS(SELECT 1 FROM source_evidence_item i WHERE i.set_id=? AND i.scope_id=s.scope_id
                AND i.submission_id=s.submission_id AND i.work_round=s.to_work_round AND i.bale_id=r.bale_id
                AND i.item_kind='SCAN' AND i.record_status='ACTIVE' AND i.original_cycle=r.dispatch_cycle)
            ORDER BY r.bale_id FOR UPDATE
            """, (rs,n)->new Reservation(uuid(rs.getBytes(1)),rs.getLong(2)), Ids.bytes(proof.scope()),
            Ids.bytes(proof.submission()),proof.round(),Ids.bytes(proof.warehouse()),Ids.bytes(proof.set()));
    }
    private List<Reservation> continued(Verified proof) {
        if(proof.round()==1)return List.of();
        return jdbc.query("""
            SELECT b.bale_id,b.expected_cycle FROM temporary_source_continuation_submission r
            JOIN temporary_source_continuation_activation a ON a.ticket_id=r.ticket_id AND a.scope_id=r.scope_id AND a.from_work_round=r.from_work_round
            JOIN submission s ON s.id=r.submission_id AND s.scope_id=r.scope_id AND s.work_round=r.to_work_round
              AND s.temporary_authorization_id=a.new_authorization_id AND s.freeze_device_id=a.target_device_id AND s.operator_id=a.target_operator_id
            JOIN submission_scope c ON c.id=s.scope_id AND c.warehouse_id=?
            JOIN temporary_source_continuation_bale b ON b.continuation_id=a.ticket_id AND b.scope_id=r.scope_id AND b.from_work_round=r.from_work_round
            WHERE r.scope_id=? AND r.submission_id=? AND r.to_work_round=?
              AND NOT EXISTS(SELECT 1 FROM source_evidence_item i WHERE i.set_id=? AND i.scope_id=r.scope_id
                AND i.submission_id=r.submission_id AND i.work_round=r.to_work_round AND i.bale_id=b.bale_id
                AND i.item_kind='SCAN' AND i.record_status='ACTIVE' AND i.original_cycle=b.expected_cycle)
            ORDER BY b.bale_id FOR UPDATE
            """,(rs,n)->new Reservation(uuid(rs.getBytes(1)),rs.getLong(2)),Ids.bytes(proof.warehouse()),Ids.bytes(proof.scope()),
            Ids.bytes(proof.submission()),proof.round(),Ids.bytes(proof.set()));
    }

    private static void requireRejected(Map<String, Object> row) {
        if (!"REJECTED".equals(row.get("submission_status")) || !hasSupervisorRejection(row.get("issues_json")))
            throw conflict("DISPOSITION_REJECTION_REQUIRED");
    }

    private static void requireCloseable(Map<String, Object> row) {
        if ("ABORTED".equals(row.get("submission_status"))) return;
        requireRejected(row);
    }

    private static void requireAborted(String status) {
        if (!"ABORTED".equals(status)) throw conflict("CONTINUATION_ABORT_REQUIRED");
    }

    private static boolean hasSupervisorRejection(Object value) {
        if (!(value instanceof CharSequence text)) return false;
        try {
            var stored = SubmissionJson.parse(text.toString());
            var issues = stored.isArray() ? stored : stored.path("issues");
            if (!issues.isArray()) return false;
            for (var issue : issues) {
                if (issue.isObject() && "SUPERVISOR_REJECTED".equals(issue.path("code").asText())) return true;
            }
        } catch (RuntimeException invalidJson) {
            return false;
        }
        return false;
    }

    private static long number(Object value) { return ((Number) value).longValue(); }
    private static UUID uuid(byte[] value) {
        if (value == null || value.length != 16) throw conflict("DISPOSITION_RESERVATION_CORRUPT");
        var b = java.nio.ByteBuffer.wrap(value); return new UUID(b.getLong(), b.getLong());
    }
    private static BusinessException conflict(String code) { return new BusinessException(409, code, "disposition verification facts cannot be verified"); }
    private enum Mode { REJECTED, CLOSE, ABORTED_CONTINUATION }
    public record Verified(UUID warehouse, UUID scope, UUID submission, long round, UUID set, UUID verification,
        String status, String setHash, long verificationVersion, long algorithmVersion, List<UUID> activeBales) {
        public Verified(UUID warehouse, UUID scope, UUID submission, long round, UUID set, UUID verification,
            String setHash, long verificationVersion, long algorithmVersion, List<UUID> activeBales) {
            this(warehouse, scope, submission, round, set, verification, "REJECTED", setHash,
                verificationVersion, algorithmVersion, activeBales);
        }
    }
    record Reservation(UUID bale, long cycle) { }
}
