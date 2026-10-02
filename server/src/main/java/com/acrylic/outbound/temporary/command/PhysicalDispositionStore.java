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

/** Atomically records disposition evidence and transfers every source reservation. */
@Repository
public class PhysicalDispositionStore {
    private final JdbcTemplate jdbc;
    public PhysicalDispositionStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID apply(PhysicalDispositionVerification.Verified verified, String manifestHash, long manifestVersion,
        List<UUID> orderedBales, Instant recordedAt) {
        if (!verified.setHash().equals(manifestHash) || manifestVersion <= 0) {
            throw conflict("DISPOSITION_MANIFEST_VERSION_CONFLICT");
        }
        if (!verified.activeBales().equals(orderedBales)) throw conflict("DISPOSITION_MANIFEST_SET_CONFLICT");
        UUID disposition = Ids.next();
        int header = jdbc.update("""
            INSERT INTO temporary_physical_disposition
            (id,warehouse_id,scope_id,submission_id,work_round,evidence_set_id,evidence_verification_id,manifest_sha256,manifest_version,recorded_at)
            VALUES(?,?,?,?,?,?,?,?,?,?)
            """, Ids.bytes(disposition), Ids.bytes(verified.warehouse()), Ids.bytes(verified.scope()), Ids.bytes(verified.submission()),
            verified.round(), Ids.bytes(verified.set()), Ids.bytes(verified.verification()), manifestHash, manifestVersion,
            Timestamp.from(recordedAt));
        if (header != 1) throw new IllegalStateException("DISPOSITION_HEADER_INSERT");
        for (UUID bale : orderedBales) {
            long cycle = jdbc.queryForObject("SELECT dispatch_cycle FROM bale WHERE id=? FOR UPDATE", Long.class, Ids.bytes(bale));
            int item = jdbc.update("INSERT INTO temporary_physical_disposition_item(disposition_id,bale_id,dispatch_cycle,disposition) VALUES(?,?,?,'PHYSICAL_DISPOSED')",
                Ids.bytes(disposition), Ids.bytes(bale), cycle);
            if (item != 1) throw conflict("DISPOSITION_ITEM_INSERT");
            int changed = jdbc.update("""
                UPDATE bale_commitment SET state='QUARANTINED',disposition_id=?
                WHERE bale_id=? AND state='SOURCE_RESERVED' AND source_scope_id=? AND source_evidence_set_id=?
                  AND source_warehouse_id=? AND dispatch_cycle=? AND disposition_id IS NULL
                """, Ids.bytes(disposition), Ids.bytes(bale), Ids.bytes(verified.scope()), Ids.bytes(verified.set()),
                Ids.bytes(verified.warehouse()), cycle);
            if (changed != 1) throw conflict("DISPOSITION_RESERVATION_CHANGED");
            BarcodeChanges.bale(jdbc, bale);
        }
        return disposition;
    }

    private static BusinessException conflict(String code) { return new BusinessException(409, code, "逐包处置未能整体提交"); }
}
