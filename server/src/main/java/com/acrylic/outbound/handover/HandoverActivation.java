package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.devices.DeviceCrypto;
import com.acrylic.outbound.inventory.master.WarehouseBaseline;
import com.acrylic.outbound.pda.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Issues the target qualification on the command transaction; original package commitments remain held. */
@Component
public class HandoverActivation {
    private final AssignmentStore assignments;private final OfflineSigner signer;private final JdbcTemplate jdbc;
    private final WarehouseBaseline warehouses;
    public HandoverActivation(AssignmentStore assignments,OfflineSigner signer,JdbcTemplate jdbc,WarehouseBaseline warehouses) {
        this.assignments=assignments;this.signer=signer;this.jdbc=jdbc;this.warehouses=warehouses;
    }
    public int terminateActive(UUID warehouse,UUID order) {
        return jdbc.update("UPDATE order_assignment SET status='REVOKED' WHERE order_id=? AND warehouse_id=? AND status IN ('ACTIVE','RECALL_PENDING')",
            Ids.bytes(order),Ids.bytes(warehouse));
    }
    public void advanceRounds(UUID order,UUID scope,int fromRound,int toRound) {
        int slot=jdbc.update("UPDATE order_submission_slot SET work_round=? WHERE order_id=? AND scope_id=? AND work_round=?",
            (long)toRound,Ids.bytes(order),Ids.bytes(scope),(long)fromRound);
        if(slot!=1)throw new BusinessException(409,"HANDOVER_ROUND_CHANGED","来源活动轮次已变化，不能激活交接");
        int scopeRows=jdbc.update("UPDATE submission_scope SET work_round=? WHERE id=? AND work_round=?",toRound,Ids.bytes(scope),(long)fromRound);
        if(scopeRows!=1)throw new BusinessException(409,"HANDOVER_ROUND_CHANGED","来源轮次已变化，不能激活交接");
    }
    public long retainedReservations(UUID submission) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE submission_id=?",Long.class,Ids.bytes(submission));
    }
    public Activated activate(UUID warehouse,UUID order,UUID targetDevice,UUID targetUser,String recoveryEpoch,String areaNote) {
        var device=targetDevice(targetDevice,warehouse);var user=targetUser(targetUser);
        int revision=jdbc.queryForObject("SELECT current_revision FROM outbound_order WHERE id=? AND warehouse_id=?",Integer.class,Ids.bytes(order),Ids.bytes(warehouse));
        var publication=assignments.publication(order,revision);
        if(!publication.recoveryEpoch().equals(recoveryEpoch))throw new BusinessException(409,"ORDER_RECOVERY_REQUIRED","发布版本来自旧恢复代次，需先对账");
        var baseline=warehouses.read(warehouse,false);
        if(!publication.snapshotId().equals(baseline.activeSnapshotId())||!publication.mappingRevision().equals(baseline.mappingRevision()))
            throw new BusinessException(409,"ORDER_BASELINE_CHANGED","库存或映射已变化，需后台核对并重新发布后激活");
        var principal=new PdaSessions.Principal(null,user.id(),user.username(),device.id(),warehouse,user.authVersion(),device.rowVersion());
        var issued=Instant.now().truncatedTo(ChronoUnit.SECONDS);
        var work=new AssignmentStore.Work(Ids.next(),Ids.next(),order,revision,assignments.nextEpoch(order),principal,
            DeviceCrypto.sha(publication.snapshotJson()),areaNote,issued,issued.plusSeconds(48*3600));
        var signed=signer.sign(new Claims("acrylic-outbound","acrylic-pda-offline",work.authId().toString(),user.id().toString(),device.id().toString(),
            warehouse.toString(),work.id().toString(),order.toString(),revision,Long.toString(work.epoch()),recoveryEpoch,publication.snapshotId(),
            publication.policyVersion(),publication.autoDeduct(),work.planSha(),publication.inventorySha(),"1","0",user.authVersion(),device.rowVersion(),
            "pda:scan",issued.getEpochSecond(),work.expires().getEpochSecond()));
        assignments.insert(work,publication,signed);return new Activated(work.id(),work.epoch(),work.expires(),principal.sessionId());
    }
    private TargetDevice targetDevice(UUID device,UUID warehouse) {
        var rows=jdbc.query("SELECT row_version FROM device WHERE id=? AND warehouse_id=? AND status='ACTIVE'",(rs,n)->rs.getString(1),Ids.bytes(device),Ids.bytes(warehouse));
        if(rows.isEmpty())throw new BusinessException(409,"HANDOVER_TARGET_DEVICE_INVALID","目标设备无效、已停用或不属于本仓，不能激活");
        return new TargetDevice(device,rows.getFirst());
    }
    private TargetUser targetUser(UUID user) {
        var rows=jdbc.query("SELECT username,CAST(auth_version AS CHAR) FROM app_user WHERE id=? AND status='ACTIVE'",(rs,n)->new String[]{rs.getString(1),rs.getString(2)},Ids.bytes(user));
        if(rows.isEmpty())throw new BusinessException(409,"HANDOVER_TARGET_USER_INVALID","目标人员无效或已停用，不能激活");
        return new TargetUser(user,rows.getFirst()[0],rows.getFirst()[1]);
    }
    private record TargetDevice(UUID id,String rowVersion){}
    private record TargetUser(UUID id,String username,String authVersion){}
    public record Activated(UUID assignmentId,long epoch,Instant expires,UUID sessionId){}
    private record Claims(String iss,String aud,String jti,String sub,String deviceId,String warehouseId,String assignmentId,
        String orderId,int revision,String assignmentEpoch,String serverRecoveryEpoch,String inventorySnapshotId,String policyVersion,
        boolean autoDeductInventory,String planSha256,String inventorySha256,String startOrderScanSeq,String allocationVersion,
        String userAuthVersion,String deviceVersion,String scope,long iat,long exp){}

    /** Empty checklists are legal only after the same complete-package review as nonempty lists. */
    static final class Prepare implements HandoverCommand.Operation {
        private final HandoverStore store;
        Prepare(HandoverStore store){this.store=store;}
        @Override public String commandType(){return "PREPARE";}
        @Override public JsonNode execute(HandoverCommand.Write write) {
            var input=write.command();var facts=write.caseFacts();
            if(!facts.path("status").asText().equals("APPROVED"))throw new BusinessException(409,"HANDOVER_STATE_INVALID","只有已批准的案件可以准备接收机");
            UUID device=input.uuid("targetDeviceId"),user=input.uuid("targetUserId");
            if(device==null||user==null)throw new BusinessException(400,"HANDOVER_TARGET_REQUIRED","准备必须指定目标设备与目标人员");
            UUID approver=UUID.fromString(write.actor().path("principalId").asText());
            byte[] ticket=HandoverReceiptCodec.bytes(SubmissionJson.object().put("caseId",write.caseId().toString()).put("targetDeviceId",device.toString())
                .put("targetUserId",user.toString()).put("inputHighWatermark",facts.path("inputHighWatermark").asText())
                .put("canonicalOrderId",facts.path("canonicalOrderId").asText()).put("issuedAt",write.recordedAt().toString()));
            store.advanceCase(write.caseId(),write.caseVersion(),"TARGET_PREPARED",null,null,null,null,null,null,device,user,ticket,null,approver,write.recordedAt());
            return SubmissionJson.object().put("caseId",write.caseId().toString()).put("status","TARGET_PREPARED")
                .put("targetDeviceId",device.toString()).put("targetUserId",user.toString()).put("preparedTicketSha256",HandoverCommand.sha(ticket));
        }
    }
    static final class Activate implements HandoverCommand.Operation {
        private final HandoverStore store;private final HandoverActivation activation;
        Activate(HandoverStore store,HandoverActivation activation){this.store=store;this.activation=activation;}
        @Override public String commandType(){return "ACTIVATE";}
        @Override public JsonNode execute(HandoverCommand.Write write) {
            var input=write.command();var facts=write.caseFacts();
            if(!facts.path("status").asText().equals("TARGET_PREPARED"))throw new BusinessException(409,"HANDOVER_STATE_INVALID","只有已准备接收机的案件可以激活");
            UUID warehouse=SubmissionJson.uuid(facts,"warehouseId"),order=SubmissionJson.uuid(facts,"canonicalOrderId"),scope=SubmissionJson.uuid(facts,"scopeId");
            UUID device=SubmissionJson.uuid(facts,"targetDeviceId"),user=SubmissionJson.uuid(facts,"targetUserId");
            if(!device.equals(input.uuid("targetDeviceId"))||!user.equals(input.uuid("targetUserId")))throw new BusinessException(409,"HANDOVER_CONTEXT_CHANGED","激活目标与准备票据不一致");
            long before=activation.retainedReservations(SubmissionJson.uuid(facts,"submissionId"));activation.terminateActive(warehouse,order);
            var activated=activation.activate(warehouse,order,device,user,input.text("transportRecoveryEpoch"),"交接激活 "+write.caseId());
            long after=activation.retainedReservations(SubmissionJson.uuid(facts,"submissionId"));
            if(before!=after)throw new BusinessException(409,"HANDOVER_RESERVATION_RELEASED","激活不得释放保留占用");
            activation.advanceRounds(order,scope,Integer.parseInt(facts.path("fromWorkRound").asText()),Integer.parseInt(facts.path("toWorkRound").asText()));
            var decision=SubmissionJson.object().put("caseId",write.caseId().toString()).put("assignmentId",activated.assignmentId().toString())
                .put("assignmentEpoch",Long.toString(activated.epoch())).put("expiresAt",activated.expires().toString()).put("targetDeviceId",device.toString())
                .put("targetUserId",user.toString()).put("workRound",facts.path("toWorkRound").asText());
            byte[] receipt=HandoverReceiptCodec.bytes(decision.deepCopy().put("activatedAt",write.recordedAt().toString()));
            store.advanceCase(write.caseId(),write.caseVersion(),"ACTIVATED",null,null,null,null,null,null,null,null,null,receipt,
                SubmissionJson.uuid(write.actor(),"principalId"),write.recordedAt());
            long epoch=store.serverRecoveryEpoch();
            var payload=decision.deepCopy();payload.remove("expiresAt");
            payload.put("orderId",order.toString()).put("status","ASSIGNED").put("activatedAt",write.recordedAt().toString());
            store.insertOutbox(order,epoch,store.nextOutboxEntityVersion(order,epoch),"HANDOVER_ACTIVATED",SubmissionJson.canonical(payload),write.recordedAt());
            return decision.put("status","ACTIVATED").put("retainedReservations",Long.toString(after));
        }
    }
}
