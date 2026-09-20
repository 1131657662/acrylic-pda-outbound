package com.acrylic.outbound.submissions;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

public record SubmissionControl(UUID id,UUID order,UUID assignment,UUID device,long round,String epoch,UUID continuation,Temporary temporary) {
    public SubmissionControl(UUID id,UUID order,UUID assignment,UUID device,long round,String epoch) { this(id,order,assignment,device,round,epoch,null,null); }
    public SubmissionControl(UUID id,UUID order,UUID assignment,UUID device,long round,String epoch,UUID continuation) { this(id,order,assignment,device,round,epoch,continuation,null); }
    public record Temporary(UUID creator,UUID client,UUID authorization) { }
    public JsonNode source() {
        if(temporary==null)return object().put("kind","FORMAL").put("sourceOrderId",order.toString());
        return object().put("kind","TEMP").put("creatorDeviceId",temporary.creator().toString()).put("clientOrderId",temporary.client().toString());
    }
    public static SubmissionControl read(UUID id,JsonNode body) {
        if(text(body.path("source"),"kind").equals("TEMP"))return temporary(id,body);
        equal(text(body.path("source"),"kind"),"FORMAL","当前仅支持正式订单提交");
        var value=new SubmissionControl(id,uuid(body.path("source"),"sourceOrderId"),uuid(body,"assignmentId"),
            uuid(body,"freezeDeviceId"),number(body,"workRound"),text(body,"sourceRecoveryEpoch"),body.path("continuationId").isNull() || body.path("continuationId").isMissingNode()?null:uuid(body,"continuationId"));
        require(value.round()>0 && (value.round()==1)==(value.continuation()==null) && value.epoch().matches("[1-9][0-9]{0,17}"),"作业轮次或恢复代次无效，续作须专用票据");
        return value;
    }
    private static SubmissionControl temporary(UUID id,JsonNode body) {
        var source=body.path("source");require(source.size()==3,"临时来源字段无效");
        var origin=new Temporary(uuid(source,"creatorDeviceId"),uuid(source,"clientOrderId"),uuid(body,"authorizationId"));
        var value=new SubmissionControl(id,null,null,uuid(body,"freezeDeviceId"),number(body,"workRound"),text(body,"sourceRecoveryEpoch"),null,origin);
        equal(value.device(),origin.creator(),"临时首轮须原设备冻结");
        require(value.round()==1 && body.path("continuationId").isNull() && body.path("assignmentId").isNull(),"临时首轮不允许伪造正式授权或续作");
        require(value.epoch().matches("[1-9][0-9]{0,17}"),"恢复代次无效");return value;
    }
}
