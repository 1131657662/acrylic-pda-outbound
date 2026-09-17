package com.acrylic.outbound.submissions;

import com.acrylic.outbound.common.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Service
public class SubmissionReview {
    private final SubmissionAccess access;private final SubmissionStore store;private final SubmissionService service;private final SubmissionFiles files;
    private final JdbcTemplate jdbc;private final SubmissionFinalizer finalizer;private final SubmissionEvents events;
    public SubmissionReview(SubmissionAccess access,SubmissionStore store,SubmissionService service,SubmissionFiles files,JdbcTemplate jdbc,SubmissionFinalizer finalizer,SubmissionEvents events) {
        this.access=access;this.store=store;this.service=service;this.files=files;this.jdbc=jdbc;this.finalizer=finalizer;this.events=events;
    }
    @Transactional(readOnly=true)
    public JsonNode forOrder(UUID order,UUID warehouse) {
        access.web(warehouse,"",false);
        var ids=jdbc.queryForList("SELECT BIN_TO_UUID(current_submission_id) FROM submission_scope WHERE warehouse_id=? AND source_order_id=?",String.class,Ids.bytes(warehouse),Ids.bytes(order));
        var result=object();if(ids.isEmpty() || ids.getFirst()==null) return result.putNull("submission");
        var head=store.required(UUID.fromString(ids.getFirst()));result.set("submission",service.response(head));String hash=store.payloadHash(head.control().id());
        if(hash!=null) {
            var body=parse(files.read(hash));result.put("shortShipmentReason",body.path("shortShipmentReason").asText(""));
            result.put("expectedCount",text(body,"expectedCount")).put("expectedWeightGrams",text(body,"expectedWeightGrams"));
            result.set("scans",array(body,"scans"));result.set("captureSummary",body.path("captureSummary"));
        }
        return result;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public JsonNode decide(UUID id,Command command,String epoch) {
        var before=store.required(id);var actor=access.web(before.warehouse(),epoch,true);var head=store.lockExisting(before);store.current(head);
        if(!epoch.equals(head.control().epoch())) throw new BusinessException(409,"ORDER_RECOVERY_REQUIRED","旧恢复代次提交须先对账");
        String hash=store.payloadHash(id);equal(command.payloadSha256(),hash,"审批摘要与原正文不一致");
        require(Set.of("APPROVE","REJECT").contains(command.decision()) && command.reason()!=null && !command.reason().isBlank() && command.reason().length()<=500,"请填写审批决定及原因");
        var previous=jdbc.query("SELECT decision,reason FROM submission_review WHERE submission_id=?",(rs,n)->new Decision(rs.getString(1),rs.getString(2)),Ids.bytes(id));
        if(!previous.isEmpty()) {
            equal(previous.getFirst(),new Decision(command.decision(),command.reason()),"批次已有不同审批决定");return service.response(head);
        }
        if(!head.status().equals("REVIEW_REQUIRED") || hash==null) throw new BusinessException(409,"SUBMISSION_STATE_CONFLICT","仅当前待复核批次可以审批");
        jdbc.update("INSERT INTO submission_review VALUES(?,?,?,?,?,?,UTC_TIMESTAMP(3))",Ids.bytes(id),hash,command.decision(),command.reason(),Ids.bytes(actor.user()),Long.parseLong(actor.permissionVersion()));
        if(command.decision().equals("APPROVE")) return finalizer.finish(head,parse(files.read(hash)),new SubmissionFinalizer.Approval(true,actor.user()));
        var issues=object().putArray("issues");issues.add(object().put("code","SUPERVISOR_REJECTED").put("reason",command.reason()));
        store.state(id,"REJECTED",issues,null);events.transition(head,"REVIEW_REQUIRED",actor.user());return service.response(store.required(id));
    }
    public record Command(String payloadSha256,String decision,String reason) { }
    private record Decision(String decision,String reason) { }
}
