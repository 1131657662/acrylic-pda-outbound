package com.acrylic.outbound.handover;

import com.acrylic.outbound.submissions.*;
import com.acrylic.outbound.temporary.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Revalidates both qualifications and the exact immutable predecessor before accepting any new fact. */
@Component
final class TemporaryHandoverPayload {
    private final HandoverApprovalEvidence originals;private final SubmissionStore submissions;
    private final TemporarySubmissionPlan plans;private final TemporarySubmissionEvidence evidence;
    private final TemporarySubmissionSnapshot snapshots;
    TemporaryHandoverPayload(HandoverApprovalEvidence originals,SubmissionStore submissions,TemporarySubmissionPlan plans,
        TemporarySubmissionEvidence evidence,TemporarySubmissionSnapshot snapshots) {
        this.originals=originals;this.submissions=submissions;this.plans=plans;this.evidence=evidence;this.snapshots=snapshots;
    }
    Checked validate(TemporaryHandoverIntakeFacts.Facts facts,SubmissionStore.Head head,JsonNode body) {
        var t=facts.ticket();var original=originals.verify(facts.caseFacts(),t.inputHighWatermark()).body();
        equal(sha(original),t.payloadSha(),"交接原正文摘要不符");
        var previous=submissions.required(t.submissionId());var oldPlan=plans.validate(previous,original);var plan=plans.validate(head,body);
        require(previous.control().round()==1,"交接高轮前驱须重验完整历史链");
        equal(TemporaryHandoverControl.read(body).control(),head.control(),"交接正文控制身份不符");
        prefix(original,body);fixedPlan(oldPlan,plan,original,body);
        var origins=TemporaryEvidenceOrigins.initial(previous,oldPlan).append(head,plan,original);
        evidence.validate(head,plan,body,origins);
        return new Checked(plan,snapshots.validate(plan,body),original.deepCopy());
    }
    private void prefix(JsonNode original,JsonNode body) {
        for(String field:List.of("captures","scans","events")) {
            var old=array(original,field);var current=array(body,field);
            require(current.size()>=old.size(),"交接原事实前缀缺失");
            for(int index=0;index<old.size();index++)equal(old.get(index),current.get(index),"交接不得改写原事实前缀");
        }
        require(number(body,"captureHighWatermark")>=number(original,"captureHighWatermark"),"交接输入高水位倒退");
    }
    private void fixedPlan(TemporarySubmissionPlan.Plan oldPlan,TemporarySubmissionPlan.Plan plan,JsonNode original,JsonNode body) {
        var old=oldPlan.body();var current=plan.body();require(old.size()==current.size(),"交接原计划字段不符");
        old.fields().forEachRemaining(field->{
            if(!List.of("authorizationId","clientOrderId","creatorDeviceId").contains(field.getKey()))
                equal(field.getValue(),current.get(field.getKey()),"交接不得改写原计划");
        });
        for(String field:List.of("inventorySnapshotId","policyVersion","autoDeductInventory","orderRevision"))
            equal(original.get(field),body.get(field),"交接固定基线或政策不符");
        require(!oldPlan.authorization().id().equals(plan.authorization().id()),"交接须使用本人独立新资格");
        equal(oldPlan.authorization().warehouseId(),plan.authorization().warehouseId(),"交接资格仓库不符");
        equal(oldPlan.authorization().recoveryEpoch(),plan.authorization().recoveryEpoch(),"交接资格恢复代次不符");
    }
    record Checked(TemporarySubmissionPlan.Plan plan,SubmissionSnapshot.Validated snapshot,JsonNode predecessor) { }
}
