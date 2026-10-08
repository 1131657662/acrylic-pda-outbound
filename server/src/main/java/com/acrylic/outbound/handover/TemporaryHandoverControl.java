package com.acrylic.outbound.handover;

import com.acrylic.outbound.submissions.SubmissionControl;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Explicit receiving round identity; it cannot be parsed as an R06 continuation. */
public record TemporaryHandoverControl(SubmissionControl control, Reference predecessor) {
    public static TemporaryHandoverControl read(JsonNode body) {
        require(body.path("schemaVersion").isInt() && body.path("schemaVersion").intValue()==2,"临时交接正文版本错误");
        var source=body.path("source");
        require(source.isObject() && source.size()==3 && "TEMP".equals(text(source,"kind")),"临时交接来源错误");
        require(body.path("assignmentId").isNull() && body.path("continuationId").isNull()
            && !body.has("temporaryContinuation"),"临时交接不得借用正式授权或续作票据");
        UUID device=uuid(body,"freezeDeviceId");equal(uuid(source,"creatorDeviceId"),device,"临时交接须接收机冻结");
        long round=number(body,"workRound");String epoch=text(body,"sourceRecoveryEpoch");
        require(round>1 && epoch.matches("[1-9][0-9]{0,17}"),"临时交接轮次或恢复代次错误");
        var origin=new SubmissionControl.Temporary(device,uuid(source,"clientOrderId"),uuid(body,"authorizationId"));
        var control=new SubmissionControl(uuid(body,"submissionId"),null,null,device,round,epoch,null,origin);
        var reference=Reference.read(body.path("temporaryHandover"));
        require(!control.id().equals(reference.submission()),"临时交接不得重开原提交");
        return new TemporaryHandoverControl(control,reference);
    }
    public record Reference(UUID ticket,UUID caseId,UUID submission,String payloadSha256,String prefixSha256) {
        public static Reference read(JsonNode row) {
            require(row.isObject() && row.size()==6 && row.path("version").isInt()
                && row.path("version").intValue()==1,"临时交接引用字段或版本错误");
            String payload=text(row,"predecessorPayloadSha256"),prefix=text(row,"sourcePrefixSha256");
            require(payload.matches("[0-9a-f]{64}") && prefix.matches("[0-9a-f]{64}"),"临时交接摘要错误");
            return new Reference(uuid(row,"ticketId"),uuid(row,"caseId"),uuid(row,"predecessorSubmissionId"),payload,prefix);
        }
    }
}
