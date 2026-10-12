package com.acrylic.outbound.inventory;

import com.acrylic.outbound.auth.SessionUser;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.review.TemporaryDecisionCandidates;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.web.context.WebApplicationContext;

/** Each scenario owns a fresh synthetic source and distinct warehouse identities. */
final class TemporaryExclusionHttpFixture {
    final TemporaryLinkedFixture linked;
    final TemporaryConfirmationFixture source;
    final SessionUser supervisor;
    final TemporaryExclusionHttp http=new TemporaryExclusionHttp();
    TemporaryExclusionHttpFixture(WebApplicationContext context) throws Exception {
        linked=new TemporaryLinkedFixture(context);source=linked.source;supervisor=source.supervisor();
        source.only(supervisor,List.of("temporary_order:link","temporary_order:confirm","submission:review"));
        http.login(supervisor.getUsername());
    }
    String cancelled() {
        String id=linked.createTarget();
        source.jdbc.update("UPDATE outbound_order SET status='CANCELLED' WHERE id=UUID_TO_BIN(?)",id);return id;
    }
    String unrelated() {
        String id=linked.createTarget();
        source.jdbc.update("UPDATE order_revision SET vehicle_no='其他车辆',planned_total=2 WHERE order_id=UUID_TO_BIN(?)",id);
        source.jdbc.update("UPDATE order_line SET planned_count=2 WHERE revision_id=(SELECT id FROM order_revision WHERE order_id=UUID_TO_BIN(?))",id);
        return id;
    }
    ObjectNode command(String id,String basis) {
        var candidate=source.bean(TemporaryDecisionCandidates.class).candidate(source.source.f.web.identity().warehouse(),
            UUID.fromString(source.source.id()),source.source.body,UUID.fromString(id));
        var command=source.command("DUPLICATE_EXCLUDE",supervisor).put("reason","主管核对该候选原事实及无作业冲突");
        command.set("exclusion",SubmissionJson.object().put("orderId",id).put("revision",Integer.toString(candidate.order().revision()))
            .put("rowVersion",candidate.order().rowVersion()).put("basis",basis).put("evidence","隔离合成资料逐项核验，依据随签名回执保存")
            .putNull("withdrawalRequestId"));return command;
    }
    Map<String,Object> state() { return source.state(); }
    String originalPath(ObjectNode command) {
        return "/temporary-command-receipts/"+command.path("commandId").asText()+"/original?warehouseId="+source.source.f.web.identity().warehouse();
    }
}
