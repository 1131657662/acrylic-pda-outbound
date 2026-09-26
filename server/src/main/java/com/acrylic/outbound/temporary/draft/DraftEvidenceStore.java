package com.acrylic.outbound.temporary.draft;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeObjectFiles;
import com.acrylic.outbound.temporary.protocol.LocalDraftStopProtocol;
import com.acrylic.outbound.temporary.protocol.LocalDraftStopSnapshotProtocol;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DraftEvidenceStore {
    private final JdbcTemplate jdbc;
    public DraftEvidenceStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public record Header(UUID evidence,UUID client,UUID warehouse,UUID device,UUID operator,UUID authorization,
        long epoch,UUID openKey,byte[] openBytes,String openSha,String root,int count,long bytes) { }
    public record Item(long ordinal,String kind,UUID record,long sequence,String status,SourceEvidenceIntakeObjectFiles.Stored stored) {
        public byte[] descriptor(UUID evidence) {
            return DraftEvidenceWire.bytes(com.acrylic.outbound.submissions.SubmissionJson.object().put("evidenceId",evidence.toString())
                .put("ordinal",ordinal).put("kind",kind).put("recordId",record.toString()).put("deviceSeq",sequence)
                .put("recordStatus",status).put("byteCount",stored.bytes()).put("bytesSha256",stored.sha256()));
        }
        public LocalDraftStopSnapshotProtocol.Item wire(byte[] bytes) {
            return new LocalDraftStopSnapshotProtocol.Item(ordinal,kind,record,sequence,status,stored.sha256(),bytes);
        }
    }

    public Header find(UUID evidence) {
        var rows=jdbc.query("SELECT * FROM local_draft_evidence WHERE evidence_id=?",this::header,Ids.bytes(evidence));
        if(rows.size()>1)throw DraftEvidenceWire.invalid(); return rows.isEmpty()?null:rows.getFirst();
    }
    public Header required(UUID evidence) {
        var row=find(evidence);if(row==null)throw DraftEvidenceWire.missing();return row;
    }
    public void lock(UUID evidence) {
        jdbc.queryForList("SELECT evidence_id FROM local_draft_evidence WHERE evidence_id=? FOR UPDATE",byte[].class,Ids.bytes(evidence));
    }
    public boolean sourceExists(UUID device,UUID client) {
        return !jdbc.queryForList("SELECT evidence_id FROM local_draft_evidence WHERE creator_device_id=? AND client_order_id=?",
            byte[].class,Ids.bytes(device),Ids.bytes(client)).isEmpty();
    }
    public void insert(LocalDraftStopProtocol.Expected expected,LocalDraftStopProtocol.VerifiedSignature signed,UUID key,byte[] open) {
        jdbc.update("""
            INSERT INTO local_draft_evidence(evidence_id,client_order_id,warehouse_id,creator_device_id,original_operator_id,
              authorization_id,source_recovery_epoch,open_key,open_bytes,open_sha256,root_sha256,record_count,byte_count,received_at)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(3))
            """,Ids.bytes(expected.evidenceId()),Ids.bytes(expected.clientOrderId()),Ids.bytes(expected.warehouseId()),Ids.bytes(expected.creatorDeviceId()),
            Ids.bytes(expected.originalOperatorId()),Ids.bytes(expected.authorizationId()),expected.sourceRecoveryEpoch(),Ids.bytes(key),open,DraftEvidenceWire.sha(open),
            signed.rootSha256(),signed.recordCount(),signed.byteCount());
    }
    public Item item(UUID evidence,long ordinal) {
        var rows=jdbc.query("SELECT * FROM local_draft_evidence_item WHERE evidence_id=? AND ordinal=?",this::item,Ids.bytes(evidence),ordinal);
        return rows.isEmpty()?null:rows.getFirst();
    }
    public List<Item> page(UUID evidence,long after) {
        return jdbc.query("SELECT * FROM local_draft_evidence_item WHERE evidence_id=? AND ordinal>? ORDER BY ordinal LIMIT 128",
            this::item,Ids.bytes(evidence),after);
    }
    public Item audit(UUID evidence,long sequence) {
        var rows=jdbc.query("SELECT * FROM local_draft_evidence_item WHERE evidence_id=? AND item_kind='AUDIT' AND device_seq=?",
            this::item,Ids.bytes(evidence),sequence);
        DraftEvidenceWire.require(rows.size()<=1);return rows.isEmpty()?null:rows.getFirst();
    }
    public long[] totals(UUID evidence) {
        return jdbc.queryForObject("SELECT COUNT(*),COALESCE(SUM(byte_count),0),COALESCE(MAX(ordinal),0) FROM local_draft_evidence_item WHERE evidence_id=?",
            (rs,n)->new long[]{rs.getLong(1),rs.getLong(2),rs.getLong(3)},Ids.bytes(evidence));
    }
    public void insertItem(UUID evidence,Item item) {
        jdbc.update("""
            INSERT INTO local_draft_evidence_item(evidence_id,ordinal,item_kind,record_id,device_seq,record_status,byte_count,bytes_sha256,file_key)
            VALUES(?,?,?,?,?,?,?,?,?)
            """,Ids.bytes(evidence),item.ordinal(),item.kind(),Ids.bytes(item.record()),item.sequence(),item.status(),
            item.stored().bytes(),item.stored().sha256(),item.stored().key());
    }
    public boolean complete(UUID evidence) {
        return !jdbc.queryForList("SELECT evidence_id FROM local_draft_evidence_complete WHERE evidence_id=?",byte[].class,Ids.bytes(evidence)).isEmpty();
    }
    public void complete(UUID evidence,UUID principal,UUID key) {
        jdbc.update("INSERT INTO local_draft_evidence_complete(evidence_id,principal_id,original_key,completed_at) VALUES(?,?,?,UTC_TIMESTAMP(3))",
            Ids.bytes(evidence),Ids.bytes(principal),Ids.bytes(key));
    }
    public boolean completedBy(UUID evidence,UUID principal,UUID key) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM local_draft_evidence_complete WHERE evidence_id=? AND principal_id=? AND original_key=? AND operation_kind='VERIFY'",
            Integer.class,Ids.bytes(evidence),Ids.bytes(principal),Ids.bytes(key))==1;
    }
    private Header header(ResultSet rs,int index)throws SQLException {
        return new Header(uuid(rs,"evidence_id"),uuid(rs,"client_order_id"),uuid(rs,"warehouse_id"),uuid(rs,"creator_device_id"),
            uuid(rs,"original_operator_id"),uuid(rs,"authorization_id"),rs.getLong("source_recovery_epoch"),uuid(rs,"open_key"),
            rs.getBytes("open_bytes"),rs.getString("open_sha256"),rs.getString("root_sha256"),rs.getInt("record_count"),rs.getLong("byte_count"));
    }
    private Item item(ResultSet rs,int index)throws SQLException {
        return new Item(rs.getLong("ordinal"),rs.getString("item_kind"),uuid(rs,"record_id"),rs.getLong("device_seq"),rs.getString("record_status"),
            new SourceEvidenceIntakeObjectFiles.Stored(rs.getString("file_key"),rs.getString("bytes_sha256"),rs.getInt("byte_count")));
    }
    private UUID uuid(ResultSet rs,String name)throws SQLException {
        var bytes=java.nio.ByteBuffer.wrap(rs.getBytes(name));return new UUID(bytes.getLong(),bytes.getLong());
    }
}
