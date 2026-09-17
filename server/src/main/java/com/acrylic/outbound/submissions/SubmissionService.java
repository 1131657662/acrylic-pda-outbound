package com.acrylic.outbound.submissions;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.pda.AssignmentStore;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Service
public class SubmissionService {
    private final SubmissionAccess access;private final SubmissionStore store;private final SubmissionFiles files;
    private final AssignmentStore assignments;private final SubmissionEvidence evidence;private final SubmissionSnapshot snapshots;
    private final SubmissionEvents events;private final SubmissionFinalizer finalizer;private final SubmissionReceipts receipts;
    public SubmissionService(SubmissionAccess access,SubmissionStore store,SubmissionFiles files,AssignmentStore assignments,SubmissionEvidence evidence,
        SubmissionSnapshot snapshots,SubmissionEvents events,SubmissionFinalizer finalizer,SubmissionReceipts receipts) {
        this.access=access;this.store=store;this.files=files;this.assignments=assignments;this.evidence=evidence;
        this.snapshots=snapshots;this.events=events;this.finalizer=finalizer;this.receipts=receipts;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public JsonNode submit(UUID order,JsonNode body,String epoch) {
        UUID id=uuid(body,"submissionId");var control=SubmissionControl.read(id,body);
        equal(control.order(),order,"正文订单与路径不一致");var actor=access.pda(control,epoch,true);
        var head=store.lock(control,actor);store.current(head);return accept(head,body);
    }
    public JsonNode accept(SubmissionStore.Head head,JsonNode body) {
        equal(SubmissionControl.read(uuid(body,"submissionId"),body),head.control(),"分块正文的原控制身份不一致");
        String hash=sha(body),previous=store.payloadHash(head.control().id());
        if(previous!=null && !previous.equals(hash)) throw new BusinessException(409,"IDEMPOTENCY_CONTENT_MISMATCH","同一提交ID正文不同，请恢复原正文");
        if(head.status().equals("ABORTED")) throw new BusinessException(409,"SUBMISSION_ABORTED","此批次已取消，不能复活或自动续扫");
        if(head.status().equals("REJECTED")) throw new BusinessException(409,"SUBMISSION_REJECTED","此批次已退回，需受控续作");
        if(previous!=null) return response(head);
        var assignment=assignments.get(head.control().assignment());evidence.validate(head,assignment,body);snapshots.validate(assignment,body);
        byte[] bytes=canonical(body).getBytes(StandardCharsets.UTF_8);equal(files.store(bytes),hash,"归档摘要不符");
        store.payload(head,body,hash,bytes.length);events.evidence(head,body);
        return finalizer.finish(head,body,new SubmissionFinalizer.Approval(false,head.actor()));
    }
    @Transactional(readOnly=true)
    public JsonNode read(UUID id) { var head=store.required(id);access.pda(head.control(),head.control().epoch(),false);return response(head); }
    public JsonNode response(SubmissionStore.Head head) {
        if(head.receipt()!=null) return parse(head.receipt().getBytes(StandardCharsets.UTF_8));
        var result=receipts.base(head,head.status(),store.payloadHash(head.control().id()));
        if(head.issues()!=null) result.set("issues",parse(head.issues().getBytes(StandardCharsets.UTF_8)));return result;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public JsonNode abort(UUID id,JsonNode body,String epoch) {
        var c=SubmissionControl.read(id,body);var actor=access.pda(c,epoch,true);var head=store.lock(c,actor);store.current(head);
        if(head.status().equals("SUCCEEDED") || head.status().equals("ABORTED") || head.status().equals("REJECTED")) return response(head);
        String reason=text(body,"reason").strip();require(!reason.isBlank() && reason.length()<=500,"取消必须填写1至500字原因");
        var receipt=receipts.abort(head,store.payloadHash(id),reason);store.state(id,"ABORTED",null,canonical(receipt));
        events.transition(head,"REVIEW_REQUIRED",actor.user());return receipt;
    }
}
