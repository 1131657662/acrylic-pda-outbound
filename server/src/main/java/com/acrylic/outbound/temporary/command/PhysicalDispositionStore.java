package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.sync.BarcodeChanges;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Records per-bale physical disposition without granting permission to resume scanning. */
@Repository
public class PhysicalDispositionStore {
    private static final Set<String> ACTIONS = Set.of("RELEASE", "RETAIN", "QUARANTINE");
    private final JdbcTemplate jdbc;
    public PhysicalDispositionStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID apply(PhysicalDispositionVerification.Verified verified, String manifestHash, long manifestVersion,
        List<PhysicalDispositionManifest.Item> items, Instant recordedAt) {
        return apply(verified, manifestHash, manifestVersion, items, recordedAt, false, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID apply(PhysicalDispositionVerification.Verified verified, String manifestHash, long manifestVersion,
        List<PhysicalDispositionManifest.Item> items, Instant recordedAt, boolean close, String reason) {
        if (close && (reason == null || reason.isBlank() || reason.length() > 1000))
            throw conflict("CLOSE_REASON_REQUIRED");
        validate(verified, manifestHash, manifestVersion, items, close);
        UUID disposition = Ids.next();
        insertHeader(disposition, verified, manifestHash, manifestVersion, recordedAt, close, reason);
        for (var item : items) applyBale(verified, disposition, item);
        if (close) closeSource(verified);
        return disposition;
    }

    private void validate(PhysicalDispositionVerification.Verified verified, String manifestHash, long manifestVersion,
        List<PhysicalDispositionManifest.Item> items, boolean close) {
        if (verified == null || items == null || manifestHash == null || !manifestHash.matches("[a-f0-9]{64}")
            || manifestVersion <= 0 || items.stream().anyMatch(item -> invalid(item, verified, close)))
            throw conflict("DISPOSITION_MANIFEST_VERSION_CONFLICT");
        var actual = items.stream().map(PhysicalDispositionManifest.Item::baleId).toList();
        if (!verified.activeBales().equals(actual)) throw conflict("DISPOSITION_MANIFEST_SET_CONFLICT");
    }

    private static boolean invalid(PhysicalDispositionManifest.Item item, PhysicalDispositionVerification.Verified verified,
        boolean close) {
        return item == null || !ACTIONS.contains(item.action())
            || (item.action().equals("RETAIN") && (!"ABORTED".equals(verified.status()) || close));
    }

    private void insertHeader(UUID disposition, PhysicalDispositionVerification.Verified verified,
        String manifestHash, long manifestVersion, Instant recordedAt, boolean close, String reason) {
        int header = jdbc.update("""
            INSERT INTO temporary_physical_disposition
            (id,warehouse_id,scope_id,submission_id,work_round,evidence_set_id,evidence_verification_id,manifest_sha256,manifest_version,close_mode,close_reason,recorded_at)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
            """, Ids.bytes(disposition), Ids.bytes(verified.warehouse()), Ids.bytes(verified.scope()), Ids.bytes(verified.submission()),
            verified.round(), Ids.bytes(verified.set()), Ids.bytes(verified.verification()), manifestHash, manifestVersion,
            close, reason, Timestamp.from(recordedAt));
        if (header != 1) throw new IllegalStateException("DISPOSITION_HEADER_INSERT");
    }

    private void closeSource(PhysicalDispositionVerification.Verified verified) {
        int scope = jdbc.update("UPDATE submission_scope SET current_submission_id=NULL WHERE id=? AND current_submission_id=?",
            Ids.bytes(verified.scope()), Ids.bytes(verified.submission()));
        if (scope != 1) throw conflict("CLOSE_SOURCE_NOT_CURRENT");
        var orders = jdbc.queryForList("SELECT canonical_order_id FROM submission_scope WHERE id=?", byte[].class, Ids.bytes(verified.scope()));
        if (orders.size() != 1 || orders.getFirst() == null) return;
        int slot = jdbc.update("UPDATE order_submission_slot SET current_submission_id=NULL,phase='CLOSED',slot_version=slot_version+1 "
            + "WHERE scope_id=? AND current_submission_id=? AND phase IN ('FROZEN','COMPLETED')",
            Ids.bytes(verified.scope()), Ids.bytes(verified.submission()));
        if (slot != 1) throw conflict("CLOSE_SLOT_NOT_CURRENT");
    }

    private void applyBale(PhysicalDispositionVerification.Verified verified, UUID disposition,
        PhysicalDispositionManifest.Item item) {
        UUID bale = item.baleId();
        long cycle = jdbc.queryForObject("SELECT dispatch_cycle FROM bale WHERE id=? FOR UPDATE", Long.class, Ids.bytes(bale));
        int inserted = jdbc.update("INSERT INTO temporary_physical_disposition_item(disposition_id,bale_id,dispatch_cycle,disposition,action) VALUES(?,?,?,'PHYSICAL_DISPOSED',?)",
            Ids.bytes(disposition), Ids.bytes(bale), cycle, item.action());
        if (inserted != 1) throw conflict("DISPOSITION_ITEM_INSERT");
        int changed = switch (item.action()) {
            case "RETAIN" -> retain(verified, bale, cycle);
            case "RELEASE" -> release(verified, bale, cycle);
            default -> quarantine(verified, disposition, bale, cycle);
        };
        if (changed != 1) throw conflict("DISPOSITION_RESERVATION_CHANGED");
        if (!item.action().equals("RETAIN")) BarcodeChanges.bale(jdbc, bale);
    }

    private int retain(PhysicalDispositionVerification.Verified verified, UUID bale, long cycle) {
        return jdbc.queryForList("""
            SELECT k.bale_id FROM bale_commitment k JOIN submission_scope c ON c.id=k.source_scope_id
            WHERE k.bale_id=? AND k.source_scope_id=? AND k.source_warehouse_id=? AND k.dispatch_cycle=?
              AND k.disposition_id IS NULL AND k.continuation_id IS NULL
              AND k.recovery_package_id IS NULL AND k.disposition_evidence_id IS NULL
              AND k.pc_work_id IS NULL AND k.pc_disposition_id IS NULL
              AND ((k.state='SOURCE_RESERVED' AND k.source_evidence_set_id=? AND k.submission_id IS NULL)
                OR (k.state='REVIEW_RESERVED' AND k.submission_id=? AND k.source_evidence_set_id IS NULL
                    AND k.order_id=c.canonical_order_id))
              AND (EXISTS (SELECT 1 FROM source_evidence_item i WHERE i.set_id=? AND i.scope_id=?
                AND i.submission_id=? AND i.work_round=? AND i.warehouse_id=? AND i.bale_id=k.bale_id
                AND i.original_cycle=k.dispatch_cycle AND i.item_kind='SCAN' AND i.record_status='ACTIVE')
                OR EXISTS (SELECT 1 FROM submission s
                  LEFT JOIN temporary_handover_submission hr ON hr.submission_id=s.id AND hr.scope_id=s.scope_id AND hr.to_work_round=s.work_round
                  LEFT JOIN temporary_handover_activation ha ON ha.ticket_id=hr.ticket_id AND ha.to_work_round=s.work_round
                    AND ha.new_authorization_id=s.temporary_authorization_id AND ha.target_device_id=s.freeze_device_id AND ha.target_user_id=s.operator_id
                  LEFT JOIN temporary_handover_activation_owner o ON o.ticket_id=ha.ticket_id AND o.bale_id=k.bale_id AND o.dispatch_cycle=k.dispatch_cycle
                  LEFT JOIN temporary_source_continuation_submission r ON r.submission_id=s.id AND r.scope_id=s.scope_id AND r.to_work_round=s.work_round
                  LEFT JOIN temporary_source_continuation_activation a ON a.ticket_id=r.ticket_id AND a.scope_id=r.scope_id AND a.from_work_round=r.from_work_round
                    AND a.new_authorization_id=s.temporary_authorization_id AND a.target_device_id=s.freeze_device_id AND a.target_operator_id=s.operator_id
                  LEFT JOIN temporary_source_continuation_bale b ON b.continuation_id=a.ticket_id AND b.scope_id=r.scope_id
                    AND b.from_work_round=r.from_work_round AND b.bale_id=k.bale_id AND b.expected_cycle=k.dispatch_cycle
                  WHERE s.id=? AND s.scope_id=k.source_scope_id AND s.work_round=?
                    AND (ha.ticket_id IS NULL OR a.ticket_id IS NULL) AND (o.bale_id IS NOT NULL OR b.bale_id IS NOT NULL)))
            FOR UPDATE
            """, Ids.bytes(bale), Ids.bytes(verified.scope()), Ids.bytes(verified.warehouse()), cycle,
            Ids.bytes(verified.set()), Ids.bytes(verified.submission()), Ids.bytes(verified.set()),
            Ids.bytes(verified.scope()), Ids.bytes(verified.submission()), verified.round(), Ids.bytes(verified.warehouse()),
            Ids.bytes(verified.submission()),verified.round()).size();
    }

    private int release(PhysicalDispositionVerification.Verified verified, UUID bale, long cycle) {
        return jdbc.update("""
            DELETE FROM bale_commitment
            WHERE bale_id=? AND source_scope_id=? AND source_warehouse_id=? AND dispatch_cycle=? AND disposition_id IS NULL
              AND ((state='SOURCE_RESERVED' AND source_evidence_set_id=? AND submission_id IS NULL)
                OR (state='REVIEW_RESERVED' AND submission_id=? AND source_evidence_set_id IS NULL))
              AND EXISTS (SELECT 1 FROM source_evidence_item i WHERE i.set_id=? AND i.scope_id=? AND i.submission_id=?
                AND i.bale_id=bale_commitment.bale_id AND i.original_cycle=bale_commitment.dispatch_cycle
                AND i.item_kind='SCAN' AND i.record_status='ACTIVE')
            """, Ids.bytes(bale), Ids.bytes(verified.scope()), Ids.bytes(verified.warehouse()), cycle,
            Ids.bytes(verified.set()), Ids.bytes(verified.submission()), Ids.bytes(verified.set()),
            Ids.bytes(verified.scope()), Ids.bytes(verified.submission()));
    }

    private int quarantine(PhysicalDispositionVerification.Verified verified, UUID disposition, UUID bale, long cycle) {
        return jdbc.update("""
            UPDATE bale_commitment SET state='QUARANTINED',disposition_id=?,source_evidence_set_id=?,submission_id=NULL,order_id=NULL
            WHERE bale_id=? AND source_scope_id=? AND source_warehouse_id=? AND dispatch_cycle=? AND disposition_id IS NULL
              AND ((state='SOURCE_RESERVED' AND source_evidence_set_id=? AND submission_id IS NULL)
                OR (state='REVIEW_RESERVED' AND submission_id=? AND source_evidence_set_id IS NULL))
              AND EXISTS (SELECT 1 FROM source_evidence_item i WHERE i.set_id=? AND i.scope_id=? AND i.submission_id=?
                AND i.bale_id=bale_commitment.bale_id AND i.original_cycle=bale_commitment.dispatch_cycle
                AND i.item_kind='SCAN' AND i.record_status='ACTIVE')
            """, Ids.bytes(disposition), Ids.bytes(verified.set()), Ids.bytes(bale), Ids.bytes(verified.scope()),
            Ids.bytes(verified.warehouse()), cycle, Ids.bytes(verified.set()), Ids.bytes(verified.submission()),
            Ids.bytes(verified.set()), Ids.bytes(verified.scope()), Ids.bytes(verified.submission()));
    }

    private static BusinessException conflict(String code) { return new BusinessException(409, code, "disposition item cannot be committed"); }
}
