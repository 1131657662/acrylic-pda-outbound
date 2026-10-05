package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.Ids;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Applies the controlled-link pointer switch inside the caller's transaction: append the new mapping
 * version, move the scope canonical, move the slot and mark the superseded PDA_TEMP order unworkable.
 * Every conditional statement must touch exactly one row; anything else keeps the whole link unapplied.
 */
@Repository
public class TemporaryLinkMapping {
    private final JdbcTemplate jdbc;
    public TemporaryLinkMapping(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Installed install(CommandContext context,String targetOrder) {
        long version=Math.addExact(context.version("mappingVersion"),1);UUID mapping=Ids.next();
        jdbc.update("INSERT INTO temporary_source_mapping(id,warehouse_id,scope_id,mapping_version,canonical_order_id,origin,observed_at) "
            +"VALUES(?,?,?,?,?,'CONTROLLED_LINK',UTC_TIMESTAMP(3))",Ids.bytes(mapping),Ids.bytes(context.id("warehouseId")),
            Ids.bytes(context.id("scopeId")),version,Ids.bytes(UUID.fromString(targetOrder)));
        require(jdbc.update("UPDATE submission_scope SET canonical_order_id=?,mapping_version=? WHERE id=? AND mapping_version=?",
            Ids.bytes(UUID.fromString(targetOrder)),version,Ids.bytes(context.id("scopeId")),context.version("mappingVersion")));
        require(jdbc.update("UPDATE order_submission_slot SET order_id=?,slot_version=slot_version+1 WHERE order_id=? AND scope_id=? AND slot_version=?",
            Ids.bytes(UUID.fromString(targetOrder)),Ids.bytes(context.id("canonicalOrderId")),Ids.bytes(context.id("scopeId")),context.version("slotVersion")));
        if(context.version("mappingVersion")==1)
            require(jdbc.update("UPDATE outbound_order SET status='LINKED',row_version=row_version+1,updated_at=UTC_TIMESTAMP(3) "
                +"WHERE id=? AND warehouse_id=? AND source='PDA_TEMP' AND status='REVIEW_REQUIRED'",
                Ids.bytes(context.id("canonicalOrderId")),Ids.bytes(context.id("warehouseId"))));
        // Reservations carry the order id captured when they were first written, so the switch must move this
        // submission's still-reserved rows onto the linked order inside the same transaction; otherwise the
        // later dispatch cannot match them and confirmation fails with BALE_COMMITMENT_CONFLICT. Only
        // REVIEW_RESERVED rows move: dispatched or settled commitments are never rewritten. This is part of the
        // link action already audited by the command ledger and its signed receipt (CommandReceiptIntegrity
        // requires exactly one audit event per receipt, so no second event is written here).
        jdbc.update("UPDATE bale_commitment SET order_id=? WHERE submission_id=? AND order_id=? AND state='REVIEW_RESERVED'",
            Ids.bytes(UUID.fromString(targetOrder)),Ids.bytes(context.id("submissionId")),Ids.bytes(context.id("canonicalOrderId")));
        return new Installed(mapping.toString(),version);
    }
    private void require(int rows) { if(rows!=1)throw CommandErrors.stale(); }
    public record Installed(String mappingId,long mappingVersion) { }
}
