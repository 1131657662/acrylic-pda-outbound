package com.acrylic.outbound.temporary.continuation;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.submissions.SubmissionAccess;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Immutable activation identity; callers acquire source locks before using it to write. */
@Repository
public class TemporaryRoundFacts {
    private final JdbcTemplate jdbc;
    public TemporaryRoundFacts(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public List<com.acrylic.outbound.temporary.command.TemporaryRetainedManifest.Expected> retained(TemporaryRoundControl request,Source source) {
        return jdbc.query("""
            SELECT BIN_TO_UUID(bale_id),expected_cycle,record_sha256 FROM temporary_source_continuation_bale
            WHERE continuation_id=? AND scope_id=? AND from_work_round=? ORDER BY bale_id
            """,(rs,n)->new com.acrylic.outbound.temporary.command.TemporaryRetainedManifest.Expected(
                UUID.fromString(rs.getString(1)),rs.getLong(2),rs.getString(3)),
            Ids.bytes(request.predecessor().ticket()),Ids.bytes(source.scope()),source.fromRound());
    }
    public TemporaryRoundControl.Reference reference(UUID submission) {
        var rows=jdbc.query("""
            SELECT BIN_TO_UUID(t.id),BIN_TO_UUID(t.previous_submission_id),r.predecessor_payload_sha256,t.retained_manifest_sha256
            FROM temporary_source_continuation_submission r JOIN temporary_source_continuation t
              ON t.id=r.ticket_id AND t.scope_id=r.scope_id AND t.from_work_round=r.from_work_round
            WHERE r.submission_id=?
            """,(rs,n)->new TemporaryRoundControl.Reference(UUID.fromString(rs.getString(1)),UUID.fromString(rs.getString(2)),
                rs.getString(3),rs.getString(4)),Ids.bytes(submission));
        if(rows.size()!=1)throw SubmissionAccess.missing();return rows.getFirst();
    }

    public Source read(TemporaryRoundControl request,SubmissionAccess.Actor actor) {
        var rows=jdbc.query("""
            SELECT BIN_TO_UUID(t.scope_id),BIN_TO_UUID(t.warehouse_id),BIN_TO_UUID(t.previous_submission_id),
              BIN_TO_UUID(t.previous_authorization_id),t.from_work_round,t.to_work_round,t.source_recovery_epoch,
              BIN_TO_UUID(t.evidence_set_id),t.next_order_scan_seq,t.retained_manifest_sha256,t.status,
              BIN_TO_UUID(a.new_authorization_id),BIN_TO_UUID(a.new_client_order_id),
              BIN_TO_UUID(a.target_device_id),BIN_TO_UUID(a.target_operator_id)
            FROM temporary_source_continuation t
            JOIN temporary_source_continuation_activation a ON a.ticket_id=t.id AND a.scope_id=t.scope_id
              AND a.from_work_round=t.from_work_round AND a.new_authorization_id=t.new_authorization_id
              AND a.target_device_id=t.freeze_device_id AND a.target_operator_id=t.previous_operator_id
            WHERE t.id=?
            """,(rs,n)->new Source(UUID.fromString(rs.getString(1)),UUID.fromString(rs.getString(2)),
                UUID.fromString(rs.getString(3)),UUID.fromString(rs.getString(4)),rs.getLong(5),rs.getLong(6),rs.getString(7),
                UUID.fromString(rs.getString(8)),rs.getLong(9),rs.getString(10),rs.getString(11),
                UUID.fromString(rs.getString(12)),UUID.fromString(rs.getString(13)),
                UUID.fromString(rs.getString(14)),UUID.fromString(rs.getString(15))),Ids.bytes(request.predecessor().ticket()));
        if(rows.size()!=1)throw conflict("TEMP_CONTINUATION_BINDING_MISMATCH");
        var source=rows.getFirst();source.require(request,actor);return source;
    }

    static BusinessException conflict(String code) {
        return new BusinessException(409,code,"TEMP续作来源不可核验，保留原提交与占用");
    }

    public record Source(UUID scope,UUID warehouse,UUID previous,UUID previousAuthorization,long fromRound,long toRound,
        String epoch,UUID evidenceSet,long nextSequence,String retainedHash,String status,UUID authorization,
        UUID client,UUID device,UUID operator) {
        void require(TemporaryRoundControl request,SubmissionAccess.Actor actor) {
            var c=request.control();var ref=request.predecessor();var temp=c.temporary();
            if(temp==null || !warehouse.equals(actor.warehouse()) || !operator.equals(actor.user())
                || !device.equals(c.device()) || !device.equals(temp.creator()) || !client.equals(temp.client())
                || !authorization.equals(temp.authorization()) || c.round()!=toRound || !epoch.equals(c.epoch())
                || !previous.equals(ref.submission()) || !retainedHash.equals(ref.retainedSha256())
                || c.order()!=null || c.assignment()!=null || c.continuation()!=null)
                throw conflict("TEMP_CONTINUATION_BINDING_MISMATCH");
        }
    }
}
