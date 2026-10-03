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

/** Atomically records disposition evidence and releases or quarantines every source reservation. */
@Repository
public class PhysicalDispositionStore {
    private static final Set<String> ACTIONS = Set.of("RELEASE", "QUARANTINE");
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
        validate(verified, manifestHash, manifestVersion, items);
        UUID disposition = Ids.next();
        insertHeader(disposition, verified, manifestHash, manifestVersion, recordedAt, close, reason);
        for (var item : items) applyBale(verified, disposition, item);
        if (close) closeSource(verified);
        return disposition;
    }

    private void validate(PhysicalDispositionVerification.Verified verified, String manifestHash, long manifestVersion,
        List<PhysicalDispositionManifest.Item> items) {
        if (verified == null || items == null || manifestHash == null || !manifestHash.matches("[a-f0-9]{64}")
            || manifestVersion <= 0 || items.stream().anyMatch(item -> item == null || !ACTIONS.contains(item.action())))
            throw conflict("DISPOSITION_MANIFEST_VERSION_CONFLICT");
        var actual = items.stream().map(PhysicalDispositionManifest.Item::baleId).toList();
        if (!verified.activeBales().equals(actual)) throw conflict("DISPOSITION_MANIFEST_SET_CONFLICT");
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
        int changed = item.action().equals("RELEASE") ? release(verified, bale, cycle) : quarantine(verified, disposition, bale, cycle);
        if (changed != 1) throw conflict("DISPOSITION_RESERVATION_CHANGED");
        BarcodeChanges.bale(jdbc, bale);
    }

    private int release(PhysicalDispositionVerification.Verified verified, UUID bale, long cycle) {
        return jdbc.update("""
            DELETE FROM bale_commitment
            WHERE bale_id=? AND state='SOURCE_RESERVED' AND source_scope_id=? AND source_evidence_set_id=?
              AND source_warehouse_id=? AND dispatch_cycle=? AND disposition_id IS NULL
            """, Ids.bytes(bale), Ids.bytes(verified.scope()), Ids.bytes(verified.set()), Ids.bytes(verified.warehouse()), cycle);
    }

    private int quarantine(PhysicalDispositionVerification.Verified verified, UUID disposition, UUID bale, long cycle) {
        return jdbc.update("""
            UPDATE bale_commitment SET state='QUARANTINED',disposition_id=?
            WHERE bale_id=? AND state='SOURCE_RESERVED' AND source_scope_id=? AND source_evidence_set_id=?
              AND source_warehouse_id=? AND dispatch_cycle=? AND disposition_id IS NULL
            """, Ids.bytes(disposition), Ids.bytes(bale), Ids.bytes(verified.scope()), Ids.bytes(verified.set()),
            Ids.bytes(verified.warehouse()), cycle);
    }

    private static BusinessException conflict(String code) { return new BusinessException(409, code, "disposition item cannot be committed"); }
}
