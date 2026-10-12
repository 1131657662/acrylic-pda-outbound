package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Observes the exact complete stop and physical set under the command lock order; creates no decision. */
@Service
public class TemporaryDispositionFacts {
    private final CommandIdentity identities;
    private final CommandFacts facts;
    private final CommandLocks locks;
    private final PhysicalDispositionVerification verification;
    private final JdbcTemplate jdbc;

    public TemporaryDispositionFacts(CommandIdentity identities,CommandFacts facts,CommandLocks locks,
        PhysicalDispositionVerification verification,JdbcTemplate jdbc) {
        this.identities=identities;this.facts=facts;this.locks=locks;this.verification=verification;this.jdbc=jdbc;
    }

    @Transactional
    public ObjectNode read(UUID warehouse,UUID submission) {
        var actor=identities.current(warehouse);var hint=facts.hint(submission);
        if(!warehouse.equals(hint.id("warehouseId")))throw CommandErrors.missing();
        locks.identity(actor,hint);actor=identities.current(warehouse);
        CommandPermissions.requireWrite(actor.permissions(),"CLOSE_DISPOSITION");
        requireIndependent(actor.id(),hint.id("originalOperatorId"));
        locks.sourceDisposition(hint);var current=facts.read(submission);locks.sameHint(hint,current);
        if(!submission.equals(current.id("currentSubmissionId")) || current.version("workRound")!=1
            || current.version("scopeRound")!=1)throw CommandErrors.stale();
        var proof=proof(current);
        var result=SubmissionJson.object().put("status",current.text("status")).put("checkedAt",Instant.now().toString());
        result.set("facts",commandFacts(current,actor));
        var rows=physical(proof);var items=result.putArray("items");
        for(var bale:proof.activeBales()) {
            var row=rows.get(bale);if(row==null)throw CommandErrors.stale();
            items.add(SubmissionJson.object().put("baleId",bale.toString()).put("barcode",row.barcode())
                .put("dispatchCycle",Long.toString(row.cycle())));
        }
        return result;
    }

    static void requireIndependent(UUID actor,UUID original) {
        if(actor.equals(original))throw new BusinessException(403,"INDEPENDENT_DISPOSITION_REQUIRED","实物关闭须由另一有权主管逐包核验");
    }

    private PhysicalDispositionVerification.Verified proof(CommandContext current) {
        for(String key:List.of("evidenceSetId","verificationId","evidenceSetSha256","verificationVersion","verificationAlgorithmVersion"))
            if(current.text(key)==null)throw new BusinessException(409,"DISPOSITION_VERIFICATION_MISSING","须先补传并核验完整原机停止证据");
        return verification.readForClose(current.id("warehouseId"),current.id("scopeId"),current.id("submissionId"),
            current.version("workRound"),current.id("evidenceSetId"),current.id("verificationId"),current.text("evidenceSetSha256"),
            current.version("verificationVersion"),current.version("verificationAlgorithmVersion"));
    }

    private ObjectNode commandFacts(CommandContext current,CommandIdentity.Actor actor) {
        var expected=CommandExpected.current(current,actor.authVersion());
        for(String key:List.of("evidenceSetId","evidenceSetSha256","verificationId","verificationVersion","verificationAlgorithmVersion"))
            expected.set(key,current.json().get(key));
        var body=SubmissionJson.object();body.set("expected",expected);
        for(String key:List.of("warehouseId","submissionId","workRound","authorizationId","freezeDeviceId","sourceRecoveryEpoch"))
            body.set(key,current.json().get(key));
        body.set("source",SubmissionJson.object().put("kind","TEMP").put("creatorDeviceId",current.text("creatorDeviceId"))
            .put("clientOrderId",current.text("clientOrderId")));
        return body;
    }

    private Map<UUID,Physical> physical(PhysicalDispositionVerification.Verified proof) {
        var values=jdbc.query("""
            SELECT k.bale_id,b.barcode,k.dispatch_cycle FROM bale_commitment k JOIN bale b ON b.id=k.bale_id
            WHERE k.source_scope_id=? AND k.source_warehouse_id=? AND b.warehouse_id=?
            """,(rs,n)->new Physical(uuid(rs.getBytes(1)),rs.getString(2),rs.getLong(3)),
            Ids.bytes(proof.scope()),Ids.bytes(proof.warehouse()),Ids.bytes(proof.warehouse()));
        var result=new HashMap<UUID,Physical>();values.forEach(row->result.put(row.id(),row));return result;
    }

    private static UUID uuid(byte[] bytes) {var b=java.nio.ByteBuffer.wrap(bytes);return new UUID(b.getLong(),b.getLong());}
    private record Physical(UUID id,String barcode,long cycle) { }
}
