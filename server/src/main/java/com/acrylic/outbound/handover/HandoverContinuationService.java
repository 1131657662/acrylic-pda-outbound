package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.continuation.DispositionProofs;
import com.acrylic.outbound.pda.*;
import com.acrylic.outbound.submissions.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Converts a verified imported handover into one scan credential and one continuation ticket. */
@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix="outbound.handover.commands", name="enabled", havingValue="true")
public class HandoverContinuationService {
    private final PdaAccess access; private final HandoverPdaEvidenceService evidence;
    private final HandoverPdaAssignmentService assignments; private final AssignmentStore assignmentStore;
    private final SubmissionFiles files; private final OfflineSigner signer; private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public HandoverContinuationService(PdaAccess access, HandoverPdaEvidenceService evidence,
            HandoverPdaAssignmentService assignments, AssignmentStore assignmentStore, SubmissionFiles files,
            OfflineSigner signer, JdbcTemplate jdbc, ObjectMapper mapper) {
        this.access=access; this.evidence=evidence; this.assignments=assignments; this.assignmentStore=assignmentStore;
        this.files=files; this.signer=signer; this.jdbc=jdbc; this.mapper=mapper;
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public JsonNode continueCase(UUID caseId, JsonNode command, String recoveryEpoch) {
        var principal=access.lock(recoveryEpoch); access.require("pda:submit");
        var input=validate(command); var replay=replay(caseId,input.commandId(),input.digest(),principal); if(replay!=null)return replay;
        var assignment=assignments.assignment(caseId);
        check(assignment.downloadAckAt()!=null,"HANDOVER_DOWNLOAD_NOT_ACKED","接收机尚未确认固定资料下载");
        check(assignment.id().equals(input.assignmentId().toString()) && assignment.offlineAuthorizationId().equals(input.authorizationId().toString())
            && assignment.deviceId().equals(principal.deviceId().toString()) && assignment.operatorId().equals(principal.userId().toString()),
            "HANDOVER_ASSIGNMENT_MISMATCH","续作请求未绑定当前接收授权");
        var manifest=evidence.manifest(caseId); check(manifest.path("recoveryPackageSha256").asText().equals(input.recoverySha256()),
            "HANDOVER_RECOVERY_CHANGED","原恢复包摘要已变化");
        check(manifest.path("inputHighWatermark").asLong()==input.nextSeq()-1,"HANDOVER_HIGH_WATERMARK_CHANGED","原输入高水位已变化");
        var body=parse(files.read(input.recoverySha256()));
        check(sha(DispositionProofs.effective(body)).equals(input.retainedSha256()),"HANDOVER_RETAINED_SET_CHANGED","保留清单摘要已变化");
        check(assignment.planSha256().equals(input.planSha256()),"HANDOVER_PLAN_CHANGED","固定计划摘要已变化");
        var facts=caseFacts(caseId); replay=replay(caseId,input.commandId(),input.digest(),principal); if(replay!=null)return replay;
        var continuation=createContinuation(facts,body,assignment,input,principal);
        var authorization=issueScanAuthorization(assignment,principal,input.authorizationId());
        transition(facts,continuation);
        var receipt=receipt(caseId,continuation.id(),assignment,authorization,input,principal);
        persist(input,caseId,continuation.id(),authorization,principal,receipt);
        return receipt;
    }

    private Input validate(JsonNode command) {
        check(command.isObject() && command.size()==8,"INVALID_HANDOVER_CONTINUATION","续作命令格式无效");
        var fields=new HashSet<String>(); command.fieldNames().forEachRemaining(fields::add);
        check(fields.equals(Set.of("commandVersion","commandId","assignmentId","authorizationId","payloadSha256","retainedManifestSha256","nextOrderScanSeq","planSha256")),
            "INVALID_HANDOVER_CONTINUATION","续作命令字段无效");
        UUID commandId=uuid(command,"commandId"),assignmentId=uuid(command,"assignmentId"),authorizationId=uuid(command,"authorizationId");
        String recovery=text(command,"payloadSha256"),retained=text(command,"retainedManifestSha256"),plan=text(command,"planSha256");
        long next=number(command,"nextOrderScanSeq"); check(command.path("commandVersion").asInt()==1,"INVALID_HANDOVER_CONTINUATION","命令版本不支持");
        check(recovery.matches("[a-f0-9]{64}")&&retained.matches("[a-f0-9]{64}")&&plan.matches("[a-f0-9]{64}")&&next>0,
            "INVALID_HANDOVER_CONTINUATION","续作命令摘要或序号无效");
        String canonical=canonical(command); return new Input(commandId,assignmentId,authorizationId,recovery,retained,next,plan,canonical,sha(command));
    }

    private JsonNode replay(UUID caseId,UUID id,String digest,PdaSessions.Principal principal) {
        var rows=jdbc.query("SELECT case_id,actor_id,device_id,command_sha256,receipt_text FROM handover_continuation WHERE command_id=? FOR SHARE",
            (rs,n)->new Replay(rs.getBytes(1),rs.getBytes(2),rs.getBytes(3),rs.getBytes(4),rs.getString(5)),Ids.bytes(id)); if(rows.isEmpty())return null;
        var row=rows.getFirst();
        if(!Arrays.equals(row.caseId(),Ids.bytes(caseId)) || !Arrays.equals(row.actorId(),Ids.bytes(principal.userId()))
                || !Arrays.equals(row.deviceId(),Ids.bytes(principal.deviceId())))
            throw new BusinessException(404,"HANDOVER_CASE_NOT_FOUND","交接案件不存在或无权访问");
        check(HexFormat.of().formatHex(row.digest()).equals(digest),"IDEMPOTENCY_CONTENT_MISMATCH","同一原键对应不同命令");
        return parse(row.receipt());
    }

    private JsonNode caseFacts(UUID id) {
        return jdbc.queryForObject("SELECT BIN_TO_UUID(id),BIN_TO_UUID(scope_id),BIN_TO_UUID(submission_id),BIN_TO_UUID(canonical_order_id),from_work_round,to_work_round,status,BIN_TO_UUID(warehouse_id),input_high_watermark FROM handover_case WHERE id=? FOR UPDATE",
            (rs,n)->object().put("caseId",rs.getString(1)).put("scopeId",rs.getString(2)).put("submissionId",rs.getString(3)).put("orderId",rs.getString(4)).put("fromRound",rs.getLong(5)).put("toRound",rs.getLong(6)).put("status",rs.getString(7)).put("warehouseId",rs.getString(8)).put("high",rs.getLong(9)),Ids.bytes(id));
    }

    private CreatedContinuation createContinuation(JsonNode facts,JsonNode body,AssignmentStore.View assignment,Input input,PdaSessions.Principal principal) {
        check("ACTIVATED".equals(facts.path("status").asText()),"HANDOVER_STATE_INVALID","交接案件尚未激活");
        UUID recall=uuid(evidence.manifest(UUID.fromString(facts.path("caseId").asText())).path("stopReceipt").path("originalCommand").path("command"),"changeRequestId");
        var recoveryRows=jdbc.query("SELECT BIN_TO_UUID(id) FROM recovery_package WHERE payload_sha256=? AND request_id=? FOR UPDATE",
            (rs,n)->rs.getString(1),input.recoverySha256(),Ids.bytes(recall));
        check(recoveryRows.size()==1,"HANDOVER_RECOVERY_CHANGED","原恢复包已变化");
        String recoveryId=recoveryRows.getFirst();
        UUID id=Ids.next(); UUID scope=UUID.fromString(facts.path("scopeId").asText()),order=UUID.fromString(facts.path("orderId").asText()),source=UUID.fromString(facts.path("submissionId").asText());
        int rows=jdbc.update("INSERT INTO order_continuation(id,request_id,scope_id,order_id,previous_submission_id,from_round,to_round,revision,assigned_device_id,recovery_package_id,retained_manifest_sha256,next_order_scan_seq,status,command_text,requested_by,new_assignment_id,approved_by,physical_evidence,receipt_text,created_at,approved_at) VALUES(?,?,?,?,?,?,?,?,?,?,UNHEX(?),?,'ACTIVATED',?,?,?,?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
            Ids.bytes(id),Ids.bytes(recall),Ids.bytes(scope),Ids.bytes(order),Ids.bytes(source),facts.path("fromRound").asLong(),facts.path("toRound").asLong(),assignment.revision(),Ids.bytes(UUID.fromString(assignment.deviceId())),Ids.bytes(UUID.fromString(recoveryId)),input.retainedSha256(),input.nextSeq(),continuationCommand(body,input),Ids.bytes(principal.userId()),Ids.bytes(UUID.fromString(assignment.id())),Ids.bytes(principal.userId()),"接收机交接续作已核验",input.command());
        check(rows==1,"HANDOVER_CONTEXT_CHANGED","交接案件已变化，请重新核对");
        for(JsonNode row:DispositionProofs.effective(body)) jdbc.update("INSERT INTO continuation_bale(continuation_id,bale_id,origin_scan_id,expected_cycle,record_sha256) VALUES(?,?,?,?,UNHEX(?))",Ids.bytes(id),Ids.bytes(uuid(row,"baleId")),Ids.bytes(uuid(row,"captureId")),number(row,"expectedDispatchCycle"),sha(row));
        return new CreatedContinuation(id);
    }

    /** Internal continuation projection; the original eight-field command stays in handover_continuation. */
    private String continuationCommand(JsonNode body,Input input) {
        var command=object(); command.setAll((com.fasterxml.jackson.databind.node.ObjectNode)parse(input.command()));
        var actions=command.putArray("dispositions");
        for(var row:DispositionProofs.effective(body)) actions.add(object().put("barcode",text(row,"barcode"))
            .put("expectedDispatchCycle",text(row,"expectedDispatchCycle")).put("action","RETAIN"));
        return canonical(command);
    }

    private Authorization issueScanAuthorization(AssignmentStore.View assignment,PdaSessions.Principal principal,UUID previous) {
        var current=assignmentStore.lockCurrentAuthorization(UUID.fromString(assignment.id())); check(current.id().equals(previous),"HANDOVER_AUTHORIZATION_CHANGED","接收授权已变化，请重新下载固定资料");
        Instant issued=Instant.now().truncatedTo(ChronoUnit.SECONDS),expires=Instant.parse(assignment.expiresAt()); check(expires.isAfter(issued),"HANDOVER_ASSIGNMENT_EXPIRED","接收授权已过期");
        UUID auth=Ids.next();long sequence=Math.addExact(current.sequence(),1);var claims=mapper.createObjectNode().put("iss","acrylic-outbound").put("aud","acrylic-pda-offline").put("jti",auth.toString()).put("sub",principal.userId().toString()).put("deviceId",principal.deviceId().toString()).put("warehouseId",principal.warehouseId().toString()).put("assignmentId",assignment.id()).put("orderId",assignment.orderId()).put("revision",assignment.revision()).put("assignmentEpoch",assignment.epoch()).put("serverRecoveryEpoch",assignment.serverRecoveryEpoch()).put("inventorySnapshotId",assignment.inventorySnapshotId()).put("planSha256",assignment.planSha256()).put("inventorySha256",assignment.inventorySha256()).put("startOrderScanSeq",assignment.startOrderScanSeq()).put("allocationVersion",assignment.allocationVersion()).put("userAuthVersion",principal.userAuthVersion()).put("deviceVersion",principal.deviceVersion()).put("authorizationSequence",sequence).put("renewedFromAuthorizationId",previous.toString()).put("scope","pda:scan").put("iat",issued.getEpochSecond()).put("exp",expires.getEpochSecond());
        var signed=signer.sign(claims);
        jdbc.update("INSERT INTO offline_authorization(id,assignment_id,authorization_sequence,user_id,device_id,issued_at,expires_at,revoked_at,replaced_by,key_id,signed_token) VALUES(?,?,?,?,?,?,?,NULL,NULL,?,?)",Ids.bytes(auth),Ids.bytes(UUID.fromString(assignment.id())),sequence,Ids.bytes(principal.userId()),Ids.bytes(principal.deviceId()),java.sql.Timestamp.from(issued),java.sql.Timestamp.from(expires),signed.keyId(),signed.token());
        check(jdbc.update("UPDATE offline_authorization SET revoked_at=COALESCE(revoked_at,UTC_TIMESTAMP(3)),replaced_by=? WHERE id=? AND revoked_at IS NULL AND replaced_by IS NULL",Ids.bytes(auth),Ids.bytes(previous))==1,
            "HANDOVER_AUTHORIZATION_CHANGED","接收导入授权已变化，请重新下载固定资料");
        return new Authorization(auth,UUID.fromString(assignment.id()),expires);
    }

    private void transition(JsonNode facts,CreatedContinuation continuation) {
        UUID source=UUID.fromString(facts.path("submissionId").asText()),scope=UUID.fromString(facts.path("scopeId").asText()),order=UUID.fromString(facts.path("orderId").asText());
        check(jdbc.update("UPDATE submission SET superseded_by=? WHERE id=? AND superseded_by IS NULL",Ids.bytes(continuation.id()),Ids.bytes(source))==1,"HANDOVER_SOURCE_CHANGED","原提交已变化");
        jdbc.update("UPDATE submission_scope SET work_round=?,current_submission_id=NULL WHERE id=?",facts.path("toRound").asLong(),Ids.bytes(scope));
        jdbc.update("UPDATE order_submission_slot SET work_round=?,current_submission_id=NULL,phase='CONTINUING',slot_version=slot_version+1 WHERE order_id=?",facts.path("toRound").asLong(),Ids.bytes(order));
        jdbc.update("UPDATE bale_commitment SET state='CONTINUATION_RESERVED',submission_id=NULL,recovery_package_id=NULL,continuation_id=? WHERE order_id=? AND submission_id=? AND source_evidence_set_id IS NULL",Ids.bytes(continuation.id()),Ids.bytes(order),Ids.bytes(source));
    }

    private JsonNode receipt(UUID caseId,UUID continuation,AssignmentStore.View assignment,Authorization authorization,Input input,PdaSessions.Principal principal) {
        var current=assignmentStore.get(authorization.assignmentId());
        var result=object().put("caseId",caseId.toString()).put("continuationId",continuation.toString()).put("status","ACTIVATED")
            .put("assignmentId",authorization.assignmentId().toString()).put("authorizationId",authorization.id().toString())
            .put("scope","pda:scan").put("previousAssignmentId",assignment.id()).put("previousAuthorizationId",input.authorizationId().toString())
            .put("recoveryPackageSha256",input.recoverySha256()).put("retainedManifestSha256",input.retainedSha256())
            .put("nextOrderScanSeq",input.nextSeq()).put("expiresAt",authorization.expires().toString()).put("stockReleased",false)
            .put("targetDeviceId",principal.deviceId().toString()).put("targetUserId",principal.userId().toString())
            .put("signedToken",current.offlineToken());
        result.set("assignment",mapper.valueToTree(current));
        var claims=object().put("iss","acrylic-outbound").put("aud","acrylic-pda-handover-continuation");claims.set("receipt",result.deepCopy());result.put("signedReceipt",signer.sign(claims).token());return result;
    }

    private void persist(Input input,UUID caseId,UUID continuation,Authorization authorization,PdaSessions.Principal actor,JsonNode receipt) {
        try { jdbc.update("INSERT INTO handover_continuation(id,case_id,command_id,command_sha256,command_text,order_continuation_id,assignment_id,authorization_id,actor_id,device_id,recovery_package_sha256,retained_manifest_sha256,next_order_scan_seq,receipt_sha256,receipt_text,created_at) VALUES(?,?,?,UNHEX(?),?,?,?,?,?,?,UNHEX(?),UNHEX(?),?,UNHEX(?),?,UTC_TIMESTAMP(6))",Ids.bytes(Ids.next()),Ids.bytes(caseId),Ids.bytes(input.commandId()),input.digest(),input.command(),Ids.bytes(continuation),Ids.bytes(authorization.assignmentId()),Ids.bytes(authorization.id()),Ids.bytes(actor.userId()),Ids.bytes(actor.deviceId()),input.recoverySha256(),input.retainedSha256(),input.nextSeq(),sha(receipt),canonical(receipt)); }
        catch(DuplicateKeyException conflict) { throw new BusinessException(409,"HANDOVER_CONTINUATION_RETRY","续作写入发生并发冲突，请使用原命令重试"); }
    }
    private void check(boolean valid,String code,String message) { if(!valid)throw new BusinessException(409,code,message); }
    private record Input(UUID commandId,UUID assignmentId,UUID authorizationId,String recoverySha256,String retainedSha256,long nextSeq,String planSha256,String command,String digest) { }
    private record Replay(byte[] caseId,byte[] actorId,byte[] deviceId,byte[] digest,String receipt) { }
    private record CreatedContinuation(UUID id) { }
    private record Authorization(UUID id,UUID assignmentId,Instant expires) { }
}
