package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Repository
public class SourceEvidenceQueries {
    private final JdbcTemplate jdbc;
    public SourceEvidenceQueries(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    JsonNode metadata(SubmissionStore.Head head) {
        var values=jdbc.queryForList("""
            SELECT JSON_OBJECT('scopeId',BIN_TO_UUID(c.id),'scopeRound',CAST(c.work_round AS CHAR),
              'currentSubmissionId',BIN_TO_UUID(c.current_submission_id),'registeredAt',DATE_FORMAT(s.created_at,'%Y-%m-%dT%H:%i:%s.%fZ'),
              'authorizationId',BIN_TO_UUID(a.id),'authorizationEpoch',CAST(a.authorization_epoch AS CHAR),
              'issuedAt',DATE_FORMAT(a.issued_at,'%Y-%m-%dT%H:%i:%s.%fZ'),'expiresAt',DATE_FORMAT(a.expires_at,'%Y-%m-%dT%H:%i:%s.%fZ'))
            FROM submission s INNER JOIN submission_scope c ON c.id=s.scope_id
            INNER JOIN temporary_work_origin t ON t.creator_device_id=c.creator_device_id AND t.client_order_id=c.client_order_id
            INNER JOIN temporary_authorization a ON a.id=t.authorization_id AND a.id=s.temporary_authorization_id
             AND a.device_id=c.creator_device_id AND a.device_id=s.freeze_device_id AND a.user_id=s.operator_id
             AND a.warehouse_id=c.warehouse_id AND a.recovery_epoch=s.source_recovery_epoch
            WHERE s.id=? AND s.assignment_id IS NULL AND s.continuation_id IS NULL AND c.source_order_id IS NULL
            """,String.class,Ids.bytes(head.control().id()));
        if(values.size()!=1)throw SourceEvidenceAccess.corrupt();return parse(values.getFirst());
    }
    JsonNode mapping(SubmissionStore.Head head) {
        var result=object().put("canonicalOrderId",head.order()==null?null:head.order().toString()).putNull("order").putNull("slot");
        if(head.order()==null)return result;
        var order=jdbc.queryForList("""
            SELECT JSON_OBJECT('id',BIN_TO_UUID(id),'orderNo',order_no,'source',source,'status',status,'revision',CAST(current_revision AS CHAR))
            FROM outbound_order WHERE id=? AND warehouse_id=?
            """,String.class,Ids.bytes(head.order()),Ids.bytes(head.warehouse()));
        if(order.size()!=1)throw SourceEvidenceAccess.corrupt();result.set("order",parse(order.getFirst()));
        var slots=jdbc.queryForList("""
            SELECT JSON_OBJECT('scopeId',BIN_TO_UUID(scope_id),'workRound',CAST(work_round AS CHAR),
              'currentSubmissionId',BIN_TO_UUID(current_submission_id),'phase',phase)
            FROM order_submission_slot WHERE order_id=?
            """,String.class,Ids.bytes(head.order()));
        if(!slots.isEmpty())result.set("slot",parse(slots.getFirst()));return result;
    }
    FileRecord payload(UUID id) {
        var rows=jdbc.query("SELECT payload_sha256,byte_count,file_key FROM submission_payload WHERE submission_id=?",
            (rs,n)->new FileRecord(rs.getString(1),rs.getInt(2),rs.getString(3)),Ids.bytes(id));
        return rows.isEmpty()?null:rows.getFirst();
    }
    Upload upload(UUID id) {
        var rows=jdbc.query("SELECT payload_sha256,byte_count,chunk_count FROM submission_upload WHERE submission_id=?",
            (rs,n)->new Upload(rs.getString(1),rs.getInt(2),rs.getInt(3)),Ids.bytes(id));
        return rows.isEmpty()?null:rows.getFirst();
    }
    List<Chunk> chunks(UUID id) {
        return jdbc.query("SELECT chunk_index,sha256,byte_count,file_key FROM submission_chunk WHERE submission_id=? ORDER BY chunk_index",
            (rs,n)->new Chunk(rs.getInt(1),new FileRecord(rs.getString(2),rs.getInt(3),rs.getString(4))),Ids.bytes(id));
    }
    record FileRecord(String sha256,int bytes,String privateKey) { }
    record Upload(String sha256,int bytes,int chunkCount) { }
    record Chunk(int index,FileRecord file) { }
}
