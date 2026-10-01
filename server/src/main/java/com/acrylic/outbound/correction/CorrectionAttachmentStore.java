package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.Ids;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Stores immutable bytes and the exact correction lines that consume them. */
@Repository
class CorrectionAttachmentStore {
    private final JdbcTemplate jdbc;
    CorrectionAttachmentStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    Stored byUpload(Owner owner, UUID uploadKey) {
        var rows = jdbc.query("SELECT * FROM correction_attachment WHERE uploaded_by=? AND warehouse_id=? AND upload_key=?",
            this::row, Ids.bytes(owner.user()), Ids.bytes(owner.warehouse()), Ids.bytes(uploadKey));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    Stored owned(Owner owner, UUID id) {
        var rows = jdbc.query("SELECT * FROM correction_attachment WHERE id=? AND warehouse_id=? AND uploaded_by=?",
            this::row, Ids.bytes(id), Ids.bytes(owner.warehouse()), Ids.bytes(owner.user()));
        if (rows.size() != 1) throw CorrectionAttachmentBytes.missing();
        return rows.getFirst();
    }

    Stored forReview(UUID warehouse, UUID id) {
        var rows = jdbc.query("""
            SELECT a.* FROM correction_attachment a WHERE a.id=? AND a.warehouse_id=? AND EXISTS (
              SELECT 1 FROM correction_attachment_use u JOIN correction_document d ON d.id=u.document_id
              WHERE u.attachment_id=a.id AND d.warehouse_id=a.warehouse_id AND d.requested_by=a.uploaded_by)
            """, this::row, Ids.bytes(id), Ids.bytes(warehouse));
        if (rows.size() != 1) throw CorrectionAttachmentBytes.missing();
        return rows.getFirst();
    }

    Stored insert(Owner owner, UUID key, byte[] bytes) {
        UUID id = Ids.next(); String sha = CorrectionCommand.sha(bytes);
        jdbc.update("""
            INSERT INTO correction_attachment(id,warehouse_id,uploaded_by,upload_key,content_sha256,size_bytes,content_bytes,created_at)
            VALUES(?,?,?,?,?,?,?,?)
            """, Ids.bytes(id), Ids.bytes(owner.warehouse()), Ids.bytes(owner.user()), Ids.bytes(key), sha,
            bytes.length, bytes, Timestamp.from(Instant.now()));
        return new Stored(id, owner, sha, bytes.length, bytes);
    }

    void bind(UUID document, Use use) {
        jdbc.update("INSERT INTO correction_attachment_use(document_id,line_no,attachment_id) VALUES(?,?,?)",
            Ids.bytes(document), use.line(), Ids.bytes(use.attachment()));
    }

    private Stored row(java.sql.ResultSet rs, int index) throws java.sql.SQLException {
        var owner = new Owner(uuid(rs.getBytes("warehouse_id")), uuid(rs.getBytes("uploaded_by")));
        return new Stored(uuid(rs.getBytes("id")), owner, rs.getString("content_sha256"),
            rs.getInt("size_bytes"), rs.getBytes("content_bytes"));
    }

    private static UUID uuid(byte[] value) {
        var buffer = ByteBuffer.wrap(value);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    record Owner(UUID warehouse, UUID user) { }
    record Use(int line, UUID attachment) { }
    record Stored(UUID id, Owner owner, String sha, int size, byte[] bytes) {
        Stored { bytes = bytes.clone(); }
        @Override public byte[] bytes() { return bytes.clone(); }
        void verify() {
            CorrectionAttachmentBytes.verify(bytes, sha);
            if (size != bytes.length) throw new IllegalStateException("Stored attachment size mismatch");
        }
    }
}
