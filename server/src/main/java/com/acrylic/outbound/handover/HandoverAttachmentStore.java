package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.Ids;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Immutable case-scoped bytes and references to the command's pre-existing idempotency reservation. */
@Repository
class HandoverAttachmentStore {
    private final JdbcTemplate jdbc;
    HandoverAttachmentStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    Stored byUpload(Owner owner, UUID key) {
        var rows = jdbc.query("SELECT * FROM handover_attachment WHERE case_id=? AND warehouse_id=? AND uploaded_by=? AND upload_key=?",
            this::row, Ids.bytes(owner.kase()), Ids.bytes(owner.warehouse()), Ids.bytes(owner.user()), Ids.bytes(key));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    Stored owned(Owner owner, UUID id) {
        var rows = jdbc.query("SELECT * FROM handover_attachment WHERE id=? AND case_id=? AND warehouse_id=? AND uploaded_by=?",
            this::row, Ids.bytes(id), Ids.bytes(owner.kase()), Ids.bytes(owner.warehouse()), Ids.bytes(owner.user()));
        return one(rows);
    }

    Stored forReview(UUID kase, UUID warehouse, UUID id) {
        var rows = jdbc.query("""
            SELECT a.* FROM handover_attachment a WHERE a.id=? AND a.case_id=? AND a.warehouse_id=? AND EXISTS(
              SELECT 1 FROM handover_attachment_use u JOIN api_idempotency_request q ON q.id=u.request_id
              JOIN handover_command_receipt r ON r.id=q.resource_id
              WHERE u.attachment_id=a.id AND u.case_id=a.case_id AND r.case_id=a.case_id AND r.command_type='FAULT_FENCE')
            """, this::row, Ids.bytes(id), Ids.bytes(kase), Ids.bytes(warehouse));
        return one(rows);
    }

    Stored insert(Owner owner, UUID key, byte[] bytes) {
        var id = Ids.next(); var sha = HandoverCommand.sha(bytes);
        jdbc.update("""
            INSERT INTO handover_attachment(id,case_id,warehouse_id,uploaded_by,upload_key,content_sha256,size_bytes,content_bytes,created_at)
            VALUES(?,?,?,?,?,?,?,?,?)
            """, Ids.bytes(id), Ids.bytes(owner.kase()), Ids.bytes(owner.warehouse()), Ids.bytes(owner.user()), Ids.bytes(key),
            sha, bytes.length, bytes, Timestamp.from(Instant.now()));
        return new Stored(id, owner, sha, bytes.length, bytes);
    }

    void bind(HandoverCommand.Write write, Owner owner, int ordinal, UUID attachment) {
        jdbc.update("""
            INSERT INTO handover_attachment_use(request_id,ordinal,attachment_id,case_id,warehouse_id,uploaded_by)
            VALUES(?,?,?,?,?,?)
            """, Ids.bytes(write.requestId()), ordinal, Ids.bytes(attachment), Ids.bytes(owner.kase()),
            Ids.bytes(owner.warehouse()), Ids.bytes(owner.user()));
    }

    List<UUID> uses(UUID receipt, Owner owner, String key, String digest) {
        return jdbc.query("""
            SELECT u.attachment_id FROM handover_attachment_use u JOIN api_idempotency_request q ON q.id=u.request_id
            WHERE q.resource_id=? AND q.principal_id=? AND q.route_scope=? AND q.idempotency_key=? AND q.request_sha256=?
              AND u.case_id=? AND u.warehouse_id=? AND u.uploaded_by=? ORDER BY u.ordinal
            """, (rs, n) -> uuid(rs.getBytes(1)), Ids.bytes(receipt), Ids.bytes(owner.user()), HandoverCommand.ROUTE_SCOPE,
            key, digest, Ids.bytes(owner.kase()), Ids.bytes(owner.warehouse()), Ids.bytes(owner.user()));
    }

    private Stored one(List<Stored> rows) {
        if (rows.size() != 1) throw HandoverAttachmentBytes.missing();
        return rows.getFirst();
    }

    private Stored row(java.sql.ResultSet rs, int index) throws java.sql.SQLException {
        var owner = new Owner(uuid(rs.getBytes("case_id")), uuid(rs.getBytes("warehouse_id")), uuid(rs.getBytes("uploaded_by")));
        return new Stored(uuid(rs.getBytes("id")), owner, rs.getString("content_sha256"), rs.getInt("size_bytes"), rs.getBytes("content_bytes"));
    }

    private static UUID uuid(byte[] bytes) { var buffer = ByteBuffer.wrap(bytes); return new UUID(buffer.getLong(), buffer.getLong()); }
    record Owner(UUID kase, UUID warehouse, UUID user) { }
    record Stored(UUID id, Owner owner, String sha, int size, byte[] bytes) {
        Stored { bytes = bytes.clone(); }
        @Override public byte[] bytes() { return bytes.clone(); }
        void verify() {
            try { HandoverAttachmentBytes.verify(bytes, sha); HandoverCommand.corruptUnless(size == bytes.length); }
            catch (com.acrylic.outbound.common.BusinessException damaged) { throw HandoverCommand.corrupt(); }
        }
    }
}
