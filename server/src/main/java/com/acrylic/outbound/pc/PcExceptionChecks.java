package com.acrylic.outbound.pc;

import com.acrylic.outbound.auth.IdentityStore;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.inventory.master.MutationAccess;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Component
public class PcExceptionChecks {
    private final JdbcTemplate jdbc;private final PcEntries entries;private final PcExceptionStore exceptions;private final JsonText json;
    public PcExceptionChecks(JdbcTemplate jdbc,PcEntries entries,PcExceptionStore exceptions,JsonText json) {
        this.jdbc=jdbc;this.entries=entries;this.exceptions=exceptions;this.json=json;
    }
    public JsonNode snapshot(PcStore.Work work) {
        var body=object().put("workId",work.id().toString()).put("orderId",work.order().toString()).put("round",work.round())
            .put("operatorId",work.operator().toString()).put("recoveryEpoch",work.epoch());
        body.set("entries",parse(json.encode(entries.all(work))));body.set("dispositions",exceptions.dispositions(work));return body;
    }
    public void epoch(PcStore.Work work,MutationAccess.Context context) {
        check(work.epoch().equals(context.epoch()),"ORDER_RECOVERY_REQUIRED","原作业来自旧恢复代次，需保留记录并先对账");
    }
    public PcExceptionStore.Case stopped(PcStore.Work work,UUID id) {
        var stopped=exceptions.find(work,id);
        check(stopped!=null && stopped.status().equals("STOPPED") && work.status().equals("STOPPED"),"PC_EXCEPTION_STATE","本单当前没有待处理的停止作业");
        check(stopped.round()==work.round() && sha(stopped.snapshot()).equals(stopped.snapshotSha256()) && sha(snapshot(work)).equals(stopped.snapshotSha256()),
            "PC_STOP_EVIDENCE_CHANGED","停止时的原始记录已变化，保留占用并先核对");return stopped;
    }
    public PcExceptionStore.Plan plan(PcStore.Work work,UUID id,String hash) {
        var plan=exceptions.current(stopped(work,null));
        check(plan!=null && plan.id().equals(id),"PC_PLAN_SUPERSEDED","本方案已被后续方案替代，请核对最新记录");
        check(plan.payloadSha256().equals(hash) && sha(plan.body()).equals(hash),"PC_PLAN_HASH_MISMATCH","处置方案不一致，请重新核对原方案");return plan;
    }
    public User target(PcStore.Work work,String username) {
        var rows=jdbc.query("SELECT BIN_TO_UUID(id),username,display_name FROM app_user WHERE username=?",
            (rs,n)->new User(UUID.fromString(rs.getString(1)),rs.getString(2),rs.getString(3)),username.strip());
        check(rows.size()==1,"PC_TARGET_UNAVAILABLE","接续账号不存在或不具备本仓库作业权限");var target=rows.getFirst();operator(work,target.id());return target;
    }
    public void operator(PcStore.Work work,UUID user) { permission(work,user,"pc:operate");permission(work,user,"order:read"); }
    public void permission(PcStore.Work work,UUID user,String permission) {
        var statuses=jdbc.queryForList("SELECT status FROM app_user WHERE id=? FOR SHARE",String.class,Ids.bytes(user));var identities=new IdentityStore(jdbc);
        check(statuses.size()==1 && statuses.getFirst().equals("ACTIVE") && identities.permissions(user).contains(permission)
            && identities.warehouses(user).stream().anyMatch(w->w.id().equals(work.warehouse())),"PC_HANDOVER_PERMISSION_CHANGED","处置或接续人员的账号、权限或仓库授权已变化，请重新核对");
    }
    public void approver(PcStore.Work work,UUID actor,JsonNode plan) {
        permission(work,actor,"order:resume");
        if(array(plan,"lines").findValuesAsText("action").stream().anyMatch(a->!a.equals("RETAIN")))permission(work,actor,"order:dispose");
        if(text(plan,"outcome").equals("CANCEL"))permission(work,actor,"order:recall");
    }
    public void lines(PcStore.Work work,PcExceptionCommands.Plan command) {
        var active=new HashMap<UUID,PcEntries.Entry>();entries.all(work).stream().filter(e->e.status().equals("ACTIVE")).forEach(e->active.put(UUID.fromString(e.id()),e));
        var seen=new HashSet<UUID>();
        for(var line:command.lines()) {
            var entry=active.get(line.entryId());
            check(entry!=null && seen.add(line.entryId()) && entry.cycle()==line.expectedCycle(),"PC_DISPOSITION_SET_MISMATCH","处置方案包含重复、过期或不属于本车的包");
            check(line.action().equals("RETAIN") || !line.physicalEvidence().isBlank(),"PC_PHYSICAL_CHECK_REQUIRED","卸货和隔离必须逐包填写位置、核验结果及凭据");
            check(!command.outcome().equals("CANCEL") || !line.action().equals("RETAIN"),"PC_CANCEL_HAS_RETAINED","结束本单前必须确认全部有效包均已卸下");
        }
        check(seen.size()==active.size(),"PC_DISPOSITION_SET_MISMATCH","请逐包核对全部有效录入，不能遗漏原包");
    }
    public static void physical(boolean confirmed) { check(confirmed,"PC_PHYSICAL_CHECK_REQUIRED","请核对本单、实物及操作影响后明确确认"); }
    public static void check(boolean valid,String code,String message) { if(!valid)throw new BusinessException(409,code,message); }
    public record User(UUID id,String username,String name) { }
}
