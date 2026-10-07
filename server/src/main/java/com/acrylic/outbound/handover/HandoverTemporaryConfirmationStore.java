package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.pda.PdaSessions;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
final class HandoverTemporaryConfirmationStore {
    private final JdbcTemplate jdbc;
    HandoverTemporaryConfirmationStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    Map<String, Object> get(UUID caseId) {
        var rows = jdbc.queryForList("""
            SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(case_id) case_id,BIN_TO_UUID(command_id) command_id,
              BIN_TO_UUID(authorization_id) authorization_id,BIN_TO_UUID(actor_id) actor_id,BIN_TO_UUID(device_id) device_id,
              LOWER(HEX(command_sha256)) command_sha256,command_bytes,
              LOWER(HEX(receipt_sha256)) receipt_sha256,receipt_bytes,recorded_at
            FROM temporary_handover_confirmation WHERE case_id=?
            """, Ids.bytes(caseId));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    void insert(HandoverTemporaryConfirmationCommand command, Receipt receipt, PdaSessions.Principal actor) {
        try { jdbc.update("""
            INSERT INTO temporary_handover_confirmation(case_id,id,command_id,authorization_id,actor_id,device_id,
                command_sha256,command_bytes,receipt_sha256,receipt_bytes,recorded_at)
            VALUES(?,?,?,?,?,?,UNHEX(?),?,UNHEX(?),?,?)
            """, Ids.bytes(command.caseId()), Ids.bytes(receipt.id()), Ids.bytes(command.key()), Ids.bytes(command.authorization()),
            Ids.bytes(actor.userId()), Ids.bytes(actor.deviceId()), command.digest(), command.bytes(),
            HandoverCommand.sha(receipt.bytes()), receipt.bytes(), Timestamp.from(receipt.recordedAt())); }
        catch (org.springframework.dao.DuplicateKeyException collision) { throw HandoverCommand.conflict(); }
    }
    record Receipt(UUID id, Instant recordedAt, byte[] bytes) { }
}
