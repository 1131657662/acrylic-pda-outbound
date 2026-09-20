package com.acrylic.outbound.submissions;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Repository
public class SubmissionArchiveStore {
    private final JdbcTemplate jdbc;
    public SubmissionArchiveStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public JsonNode find(UUID id,String epoch) {
        var rows=jdbc.query("""
            SELECT confirmation_text,payload_sha256,receipt_sha256,archived_at,retain_until
            FROM submission_archive WHERE submission_id=? AND recovery_epoch=?
            """,(rs,n)->{
                try {
                    var proof=parse(rs.getString(1));
                    equal(text(proof,"payloadSha256"),rs.getString(2),"归档正文索引不一致");
                    equal(text(proof,"receiptSha256"),rs.getString(3),"归档回执索引不一致");
                    equal(Instant.parse(text(proof,"archivedAt")),rs.getTimestamp(4).toInstant(),"归档时间不一致");
                    equal(Instant.parse(text(proof,"retainUntil")),rs.getTimestamp(5).toInstant(),"留存期限不一致");
                    return proof;
                } catch(RuntimeException error) { throw SubmissionArchiveProofs.unavailable(); }
            },Ids.bytes(id),Long.parseLong(epoch));
        return rows.isEmpty()?null:rows.getFirst();
    }
    public void insert(UUID id,String epoch,JsonNode proof) {
        jdbc.update("""
            INSERT INTO submission_archive(submission_id,recovery_epoch,payload_sha256,receipt_sha256,confirmation_text,archived_at,retain_until)
            VALUES(?,?,?,?,?,?,?)
            """,Ids.bytes(id),Long.parseLong(epoch),text(proof,"payloadSha256"),text(proof,"receiptSha256"),canonical(proof),
            Timestamp.from(Instant.parse(text(proof,"archivedAt"))),Timestamp.from(Instant.parse(text(proof,"retainUntil"))));
    }
}
