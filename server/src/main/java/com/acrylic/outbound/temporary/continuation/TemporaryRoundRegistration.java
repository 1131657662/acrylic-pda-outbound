package com.acrylic.outbound.temporary.continuation;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.pda.PdaSessions;
import com.acrylic.outbound.submissions.SubmissionAccess;
import com.acrylic.outbound.temporary.command.TemporaryTrustedScanHighWatermark;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.temporary.continuation.TemporaryRoundFacts.conflict;

/** Internal registration primitive. Caller owns E/G/identity/warehouse locks and pda:submit permission.
 * Registration grants no scan/confirmation capability and does not select a round or move K owners. */
@Component
@Transactional(propagation=Propagation.MANDATORY)
public class TemporaryRoundRegistration {
    private final JdbcTemplate jdbc;private final TemporaryRoundFacts facts;private final TemporaryRoundPredecessor predecessor;
    private final TemporaryTrustedScanHighWatermark watermarks;
    public TemporaryRoundRegistration(JdbcTemplate jdbc,TemporaryRoundFacts facts,TemporaryRoundPredecessor predecessor,
        TemporaryTrustedScanHighWatermark watermarks) {
        this.jdbc=jdbc;this.facts=facts;this.predecessor=predecessor;this.watermarks=watermarks;
    }
    public Registered register(TemporaryRoundControl request,PdaSessions.Principal principal) {
        if(!principal.deviceId().equals(request.control().device()))throw conflict("TEMP_CONTINUATION_BINDING_MISMATCH");
        var actor=new SubmissionAccess.Actor(principal.userId(),principal.warehouseId(),principal.userAuthVersion());
        var source=facts.read(request,actor);lockSource(source,request);
        source=facts.read(request,actor);predecessor.read(request,source);
        if(replay(request,source))return new Registered(source.scope(),request.control().id());
        if(!"ACTIVATED".equals(source.status()))throw conflict("CONTINUATION_NOT_ACTIVATED");
        watermarks.requireNext(source.evidenceSet(),source.scope(),source.previous(),source.fromRound(),source.nextSequence());
        requireCurrentPredecessor(source);insert(request,source);
        return new Registered(source.scope(),request.control().id());
    }
    private void lockSource(TemporaryRoundFacts.Source source,TemporaryRoundControl request) {
        var orders=jdbc.queryForList("SELECT BIN_TO_UUID(canonical_order_id) FROM submission_scope WHERE id=? FOR UPDATE",
            String.class,Ids.bytes(source.scope()));
        if(orders.size()!=1)throw conflict("TEMP_CONTINUATION_BINDING_MISMATCH");
        if(orders.getFirst()!=null) {
            jdbc.queryForList("SELECT id FROM outbound_order WHERE id=UUID_TO_BIN(?) FOR UPDATE",orders.getFirst());
            jdbc.queryForList("SELECT order_id FROM order_submission_slot WHERE order_id=UUID_TO_BIN(?) FOR UPDATE",orders.getFirst());
        }
        jdbc.queryForList("SELECT id FROM submission WHERE id IN (?,?) ORDER BY id FOR UPDATE",
            Ids.bytes(source.previous()),Ids.bytes(request.control().id()));
        jdbc.queryForList("SELECT id FROM temporary_authorization WHERE id=? FOR UPDATE",Ids.bytes(source.authorization()));
        jdbc.queryForList("SELECT id FROM temporary_source_continuation WHERE id=? FOR UPDATE",Ids.bytes(request.predecessor().ticket()));
    }
    private boolean replay(TemporaryRoundControl request,TemporaryRoundFacts.Source source) {
        var rows=jdbc.queryForList("""
            SELECT BIN_TO_UUID(submission_id) submission_id,predecessor_payload_sha256
            FROM temporary_source_continuation_submission WHERE ticket_id=?
            """,Ids.bytes(request.predecessor().ticket()));
        if(rows.isEmpty())return false;
        var row=rows.getFirst();
        if(!request.control().id().toString().equals(row.get("submission_id"))
            || !request.predecessor().payloadSha256().equals(row.get("predecessor_payload_sha256")))
            throw conflict("TEMP_CONTINUATION_SUBMISSION_ALREADY_BOUND");
        int valid=jdbc.queryForObject("""
            SELECT COUNT(*) FROM submission WHERE id=? AND scope_id=? AND temporary_authorization_id=?
              AND freeze_device_id=? AND operator_id=? AND work_round=? AND source_recovery_epoch=?
              AND assignment_id IS NULL AND continuation_id IS NULL
            """,Integer.class,Ids.bytes(request.control().id()),Ids.bytes(source.scope()),Ids.bytes(source.authorization()),
            Ids.bytes(source.device()),Ids.bytes(source.operator()),source.toRound(),source.epoch());
        if(valid!=1)throw conflict("TEMP_CONTINUATION_BINDING_MISMATCH");return true;
    }
    private void requireCurrentPredecessor(TemporaryRoundFacts.Source source) {
        int valid=jdbc.queryForObject("""
            SELECT COUNT(*) FROM submission_scope c JOIN submission p ON p.id=c.current_submission_id AND p.scope_id=c.id
            WHERE c.id=? AND c.source_order_id IS NULL AND c.work_round=? AND p.id=? AND p.status='ABORTED'
              AND p.work_round=c.work_round AND p.temporary_authorization_id=?
            """,Integer.class,Ids.bytes(source.scope()),source.fromRound(),Ids.bytes(source.previous()),Ids.bytes(source.previousAuthorization()));
        if(valid!=1)throw conflict("TEMP_CONTINUATION_PREDECESSOR_CHANGED");
    }
    private void insert(TemporaryRoundControl request,TemporaryRoundFacts.Source source) {
        try {
            jdbc.update("""
                INSERT INTO submission(id,scope_id,freeze_device_id,operator_id,work_round,source_recovery_epoch,
                  status,temporary_authorization_id,created_at,updated_at)
                VALUES(?,?,?,?,?,?,'REGISTERED',?,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))
                """,Ids.bytes(request.control().id()),Ids.bytes(source.scope()),Ids.bytes(source.device()),
                Ids.bytes(source.operator()),source.toRound(),source.epoch(),Ids.bytes(source.authorization()));
            jdbc.update("""
                INSERT INTO temporary_source_continuation_submission
                  (ticket_id,scope_id,from_work_round,to_work_round,submission_id,predecessor_payload_sha256,registered_at)
                VALUES(?,?,?,?,?,?,UTC_TIMESTAMP(3))
                """,Ids.bytes(request.predecessor().ticket()),Ids.bytes(source.scope()),source.fromRound(),source.toRound(),
                Ids.bytes(request.control().id()),request.predecessor().payloadSha256());
        } catch(org.springframework.dao.DuplicateKeyException collision) {
            throw conflict("TEMP_CONTINUATION_SUBMISSION_ALREADY_BOUND");
        }
    }
    public record Registered(UUID scope,UUID submission) { }
}
