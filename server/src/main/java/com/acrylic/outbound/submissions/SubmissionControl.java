package com.acrylic.outbound.submissions;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

public record SubmissionControl(UUID id,UUID order,UUID assignment,UUID device,long round,String epoch,UUID continuation) {
    public SubmissionControl(UUID id,UUID order,UUID assignment,UUID device,long round,String epoch) { this(id,order,assignment,device,round,epoch,null); }
    public static SubmissionControl read(UUID id,JsonNode body) {
        equal(text(body.path("source"),"kind"),"FORMAL","当前仅支持正式订单提交");
        var value=new SubmissionControl(id,uuid(body.path("source"),"sourceOrderId"),uuid(body,"assignmentId"),
            uuid(body,"freezeDeviceId"),number(body,"workRound"),text(body,"sourceRecoveryEpoch"),body.path("continuationId").isNull() || body.path("continuationId").isMissingNode()?null:uuid(body,"continuationId"));
        require(value.round()>0 && (value.round()==1)==(value.continuation()==null) && value.epoch().matches("[1-9][0-9]{0,17}"),"作业轮次或恢复代次无效，续作须专用票据");
        return value;
    }
}
