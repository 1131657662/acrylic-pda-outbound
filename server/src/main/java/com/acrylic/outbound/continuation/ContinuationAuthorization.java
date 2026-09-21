package com.acrylic.outbound.continuation;

import com.acrylic.outbound.auth.IdentityStore;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.pda.*;
import com.acrylic.outbound.recall.RecallStore;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Component
public class ContinuationAuthorization {
    private final AssignmentStore assignments;private final OfflineSigner signer;private final JdbcTemplate jdbc;
    private final PdaAuthLocks locks;private final IdentityStore identities;
    public ContinuationAuthorization(AssignmentStore assignments,OfflineSigner signer,JdbcTemplate jdbc,PdaAuthLocks locks,IdentityStore identities) {
        this.assignments=assignments;this.signer=signer;this.jdbc=jdbc;this.locks=locks;this.identities=identities;
    }
    public Origin origin(RecallStore.Head r) {
        var old=assignments.get(r.assignment());
        return new Origin(r.assignment(),UUID.fromString(old.operatorId()),UUID.fromString(old.deviceId()));
    }
    public PdaSessions.Principal principal(RecallStore.Head r,Origin before) {
        if(!before.equals(origin(r))) throw changed();
        var device=locks.device(before.device());
        UUID user=before.user();var u=jdbc.queryForMap("SELECT username,auth_version,status FROM app_user WHERE id=?",Ids.bytes(user));
        var permissions=identities.permissions(user);
        if(!u.get("status").equals("ACTIVE") || !permissions.containsAll(List.of("pda:login","pda:scan","pda:submit")) ||
            identities.warehouses(user).stream().noneMatch(w->w.id().equals(r.warehouse())))
            throw new BusinessException(409,"OFFLINE_AUTH_REAUTHORIZATION_REQUIRED","原人员或设备资格无效，须先核对");
        return new PdaSessions.Principal(null,user,(String)u.get("username"),device.id(),r.warehouse(),u.get("auth_version").toString(),device.rowVersion());
    }
    public void recheck(RecallStore.Head current,Origin before) {
        if(!before.equals(origin(current))) throw changed();
    }
    private BusinessException changed() {
        return new BusinessException(409,"CONTINUATION_IDENTITY_CONTEXT_CHANGED","原授权人员或设备已变化，请刷新后重新核对");
    }
    public record Origin(UUID assignment,UUID user,UUID device) { }
    public AssignmentStore.View issue(ContinuationStore.Head h,PdaSessions.Principal actor) {
        var publication=assignments.publication(h.order(),h.revision());
        var issued=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);var old=assignments.get(UUID.fromString(
            jdbc.queryForObject("SELECT BIN_TO_UUID(assignment_id) FROM order_change_request WHERE id=?",String.class,Ids.bytes(h.recall()))));
        var work=new AssignmentStore.Work(Ids.next(),Ids.next(),h.order(),h.revision(),assignments.nextEpoch(h.order()),actor,old.planSha256(),old.areaNote(),issued,issued.plusSeconds(172800));
        var claims=object().put("iss","acrylic-outbound").put("aud","acrylic-pda-offline").put("jti",work.authId().toString())
            .put("sub",actor.userId().toString()).put("deviceId",actor.deviceId().toString()).put("warehouseId",actor.warehouseId().toString())
            .put("assignmentId",work.id().toString()).put("orderId",h.order().toString()).put("revision",h.revision()).put("assignmentEpoch",Long.toString(work.epoch()))
            .put("serverRecoveryEpoch",old.serverRecoveryEpoch()).put("inventorySnapshotId",old.inventorySnapshotId()).put("policyVersion",publication.policyVersion())
            .put("autoDeductInventory",publication.autoDeduct()).put("planSha256",old.planSha256()).put("inventorySha256",old.inventorySha256())
            .put("startOrderScanSeq",Long.toString(h.nextSeq())).put("allocationVersion","0").put("userAuthVersion",actor.userAuthVersion())
            .put("deviceVersion",actor.deviceVersion()).put("scope","pda:scan").put("iat",issued.getEpochSecond()).put("exp",work.expires().getEpochSecond());
        assignments.insert(work,publication,signer.sign(claims));
        jdbc.update("UPDATE order_assignment SET start_order_scan_seq=? WHERE id=?",h.nextSeq(),Ids.bytes(work.id()));
        return assignments.get(work.id());
    }
}
