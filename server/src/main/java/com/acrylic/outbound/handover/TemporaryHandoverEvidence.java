package com.acrylic.outbound.handover;

import com.acrylic.outbound.submissions.SubmissionStore;
import com.acrylic.outbound.temporary.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Validates one receiving round against a fully verified predecessor qualification history. */
@Component
public final class TemporaryHandoverEvidence {
    private final TemporarySubmissionEvidence evidence;
    public TemporaryHandoverEvidence(TemporarySubmissionEvidence evidence) { this.evidence = evidence; }

    public TemporaryEvidenceOrigins validate(Input input, JsonNode body, TemporaryEvidenceOrigins previousOrigins) {
        var previous = input.previous(); var head = input.current(); var request = TemporaryHandoverControl.read(body);
        equal(request.control(), head.control(), "交接正文控制身份不符");
        equal(request.predecessor().submission(), previous.control().id(), "交接前驱批次不符");
        equal(sha(input.original()), request.predecessor().payloadSha256(), "交接前驱摘要不符");
        require("ABORTED".equals(previous.status()) && head.control().round() == previous.control().round() + 1,
            "交接前驱终态或轮次不符");
        equal(previous.warehouse(), head.warehouse(), "交接前驱仓库不符");
        equal(previous.control().epoch(), head.control().epoch(), "交接前驱恢复代次不符");
        evidence.validate(previous, input.oldPlan(), input.original(), previousOrigins);
        prefix(input.original(), body); fixedPlan(input, body);
        var origins = previousOrigins.append(head, input.plan(), input.original());
        evidence.validate(head, input.plan(), body, origins);
        return origins;
    }

    private void prefix(JsonNode original, JsonNode body) {
        for (String field : List.of("captures", "scans", "events")) {
            var old = array(original, field); var current = array(body, field);
            require(current.size() >= old.size(), "交接原事实前缀缺失");
            for (int index = 0; index < old.size(); index++)
                equal(old.get(index), current.get(index), "交接不得改写原事实前缀");
        }
        require(number(body, "captureHighWatermark") >= number(original, "captureHighWatermark"), "交接输入高水位倒退");
    }

    private void fixedPlan(Input input, JsonNode body) {
        var old = input.oldPlan().body(); var current = input.plan().body();
        require(old.size() == current.size(), "交接原计划字段不符");
        old.fields().forEachRemaining(field -> {
            if (!List.of("authorizationId", "clientOrderId", "creatorDeviceId").contains(field.getKey()))
                equal(field.getValue(), current.get(field.getKey()), "交接不得改写原计划");
        });
        for (String field : List.of("inventorySnapshotId", "policyVersion", "autoDeductInventory", "orderRevision"))
            equal(input.original().get(field), body.get(field), "交接固定基线或政策不符");
        require(!input.oldPlan().authorization().id().equals(input.plan().authorization().id()), "交接须使用本人独立新资格");
        equal(input.oldPlan().authorization().warehouseId(), input.plan().authorization().warehouseId(), "交接资格仓库不符");
        equal(input.oldPlan().authorization().recoveryEpoch(), input.plan().authorization().recoveryEpoch(), "交接资格恢复代次不符");
    }

    public record Input(SubmissionStore.Head previous, SubmissionStore.Head current, TemporarySubmissionPlan.Plan oldPlan,
        TemporarySubmissionPlan.Plan plan, JsonNode original) { }
}
