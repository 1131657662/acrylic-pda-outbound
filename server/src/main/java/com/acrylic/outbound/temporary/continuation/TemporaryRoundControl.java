package com.acrylic.outbound.temporary.continuation;

import com.acrylic.outbound.submissions.SubmissionControl;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Explicit next-round identity. This parser does not authorize intake or scanning. */
public record TemporaryRoundControl(SubmissionControl control, Reference predecessor) {
    public static TemporaryRoundControl read(JsonNode body) {
        require(body.path("schemaVersion").isInt() && body.path("schemaVersion").intValue()==2,
            "TEMP续作正文版本错误");
        var source=body.path("source");
        require(source.isObject() && source.size()==3 && "TEMP".equals(text(source,"kind")),"TEMP续作来源错误");
        require(body.path("assignmentId").isNull() && body.path("continuationId").isNull(),"TEMP续作不得使用正式授权");
        UUID creator=uuid(source,"creatorDeviceId"),device=uuid(body,"freezeDeviceId");
        equal(creator,device,"TEMP续作须原设备冻结");
        long round=number(body,"workRound");String epoch=text(body,"sourceRecoveryEpoch");
        require(round>1 && epoch.matches("[1-9][0-9]{0,17}"),"TEMP续作轮次或恢复代次错误");
        var origin=new SubmissionControl.Temporary(creator,uuid(source,"clientOrderId"),uuid(body,"authorizationId"));
        var control=new SubmissionControl(uuid(body,"submissionId"),null,null,device,round,epoch,null,origin);
        var reference=Reference.read(body.path("temporaryContinuation"));
        require(!control.id().equals(reference.submission()),"TEMP续作不得重开原提交");
        return new TemporaryRoundControl(control,reference);
    }

    public record Reference(UUID ticket,UUID submission,String payloadSha256,String retainedSha256) {
        private static Reference read(JsonNode row) {
            require(row.isObject() && row.size()==5 && row.path("version").isInt()
                && row.path("version").intValue()==1,"TEMP续作引用版本或字段错误");
            String payload=text(row,"predecessorPayloadSha256"),retained=text(row,"retainedManifestSha256");
            require(payload.matches("[0-9a-f]{64}") && retained.matches("[0-9a-f]{64}"),"TEMP续作摘要错误");
            return new Reference(uuid(row,"ticketId"),uuid(row,"predecessorSubmissionId"),payload,retained);
        }
    }
}
