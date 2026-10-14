package com.acrylic.outbound.relay;

import com.acrylic.outbound.common.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Immutable original bytes, check documents and explicit recovery references. */
@Repository
public class RelayPhysicalStore {
    private final JdbcTemplate jdbc;
    public RelayPhysicalStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Attachment attachment(UUID warehouse,UUID id) {
        var rows=jdbc.query("""
            SELECT BIN_TO_UUID(id),BIN_TO_UUID(relay_id),BIN_TO_UUID(uploaded_by),content_sha256,content_bytes
            FROM independent_physical_attachment WHERE id=? AND warehouse_id=?
            """,(rs,n)->new Attachment(UUID.fromString(rs.getString(1)),UUID.fromString(rs.getString(2)),
                UUID.fromString(rs.getString(3)),rs.getString(4),rs.getBytes(5)),Ids.bytes(id),Ids.bytes(warehouse));
        if(rows.size()!=1)throw missing();var result=rows.getFirst();
        try { RelayPhysicalPolicy.bytes(result.bytes(),result.sha()); }
        catch(BusinessException error) { throw corrupt(); }
        return result;
    }
    public void attachment(Scope scope,Attachment row) {
        jdbc.update("INSERT INTO independent_physical_attachment VALUES(?,?,?,?,?,?,?,UTC_TIMESTAMP(3))",
            Ids.bytes(row.id()),Ids.bytes(row.relay()),Ids.bytes(scope.warehouse()),Ids.bytes(row.actor()),row.sha(),row.bytes().length,row.bytes());
    }
    public List<Attachment> attachments(Scope scope,JsonNode references) {
        var result=new ArrayList<Attachment>();
        for(var ref:references) {
            var row=attachment(scope.warehouse(),RelayPhysicalPolicy.identifier(ref,"attachmentId"));
            if(!row.relay().equals(scope.relay()) || !row.actor().equals(scope.actor()) || !row.sha().equals(text(ref,"evidenceSha256"))
                || !Integer.toString(row.bytes().length).equals(text(ref,"sizeBytes")))throw missing();
            result.add(row);
        }
        return List.copyOf(result);
    }
    public void insert(Check row,List<Attachment> attachments) {
        var s=row.scope();jdbc.update("INSERT INTO independent_physical_check VALUES(?,?,?,?,?,?,?,?,?,?)",Ids.bytes(row.id()),Ids.bytes(s.relay()),
            Ids.bytes(s.warehouse()),Ids.bytes(s.actor()),sha(row.request()),sha(row.context()),canonical(row.request()),canonical(row.context()),
            canonical(row.receipt()),Timestamp.from(row.at()));
        attachments.forEach(a->jdbc.update("INSERT INTO independent_physical_attachment_use VALUES(?,?)",Ids.bytes(row.id()),Ids.bytes(a.id())));
    }
    public Check check(UUID warehouse,UUID id) {
        var rows=jdbc.query("""
            SELECT BIN_TO_UUID(relay_id),BIN_TO_UUID(supervisor_id),request_text,context_text,receipt_text,recorded_at,request_sha256,context_sha256
            FROM independent_physical_check WHERE id=? AND warehouse_id=?
            """,(rs,n)->{
                var request=parse(rs.getString(3));var context=parse(rs.getString(4));
                if(!sha(request).equals(rs.getString(7)) || !sha(context).equals(rs.getString(8)))throw corrupt();
                return new Check(id,new Scope(warehouse,UUID.fromString(rs.getString(1)),UUID.fromString(rs.getString(2))),
                    request,context,parse(rs.getString(5)),rs.getTimestamp(6).toInstant());
            },Ids.bytes(id),Ids.bytes(warehouse));
        if(rows.size()!=1)throw missing();return rows.getFirst();
    }
    public void references(Check row) {
        var expected=new HashSet<UUID>();row.request().path("attachments").forEach(ref->expected.add(RelayPhysicalPolicy.identifier(ref,"attachmentId")));
        var actual=new HashSet<>(jdbc.queryForList("SELECT BIN_TO_UUID(attachment_id) FROM independent_physical_attachment_use WHERE check_id=?",
            String.class,Ids.bytes(row.id())).stream().map(UUID::fromString).toList());
        if(!actual.equals(expected))throw corrupt();attachments(row.scope(),row.request().path("attachments"));
    }
    static BusinessException missing() { return new BusinessException(404,"INDEPENDENT_PHYSICAL_NOT_FOUND","实物依据不存在或无权使用"); }
    static BusinessException corrupt() { return new BusinessException(409,"INDEPENDENT_PHYSICAL_INTEGRITY","原实物依据不可核验，请保全核对"); }
    public record Scope(UUID warehouse,UUID relay,UUID actor) { }
    public record Attachment(UUID id,UUID relay,UUID actor,String sha,byte[] bytes) {
        public Attachment { bytes=bytes.clone(); }
        @Override public byte[] bytes() { return bytes.clone(); }
        JsonNode metadata() { return object().put("attachmentId",id.toString()).put("evidenceSha256",sha).put("sizeBytes",Integer.toString(bytes.length)); }
    }
    public record Check(UUID id,Scope scope,JsonNode request,JsonNode context,JsonNode receipt,Instant at) { }
}
