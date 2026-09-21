package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class IdentitySetContinuationIT extends IdentitySetFixture {
    @Test void changedAssignmentOperatorCannotIssueContinuationForOldHint() throws Exception {
        var f=new SubmissionFixture(web(),jdbc,false,false);var continuation=new ContinuationFixture(f);continuation.request();
        var replacement=manager(f.web).web().identity().user().id();
        var request=continuation.approve(continuation.approveBody(),UUID.randomUUID().toString());
        String assignment=f.assignment.get("id").asText();long before=count("SELECT COUNT(*) FROM order_assignment WHERE order_id=UUID_TO_BIN(?)",f.order);
        assertThat(changedBeforeLock("C04-continuation-origin-fixture",s->s.startsWith("UPDATE order_assignment SET operator_id="),
            ()->transactional(()->{
                guard.lock("1");guard.users(List.of(f.web.identity().user().id(),replacement));
                jdbc.queryForList("SELECT id FROM warehouse WHERE id=? FOR UPDATE",Ids.bytes(f.web.identity().warehouse()));
                jdbc.queryForList("SELECT id FROM outbound_order WHERE id=UUID_TO_BIN(?) FOR UPDATE",f.order);
                jdbc.update("UPDATE order_assignment SET operator_id=? WHERE id=UUID_TO_BIN(?)",Ids.bytes(replacement),assignment);
            }),()->code(f.web.send(request)))).isEqualTo("CONTINUATION_IDENTITY_CONTEXT_CHANGED");
        assertThat(count("SELECT COUNT(*) FROM order_assignment WHERE order_id=UUID_TO_BIN(?)",f.order)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT status FROM order_continuation WHERE id=UUID_TO_BIN(?)",String.class,
            continuation.ticket.get("continuationId").asText())).isEqualTo("REQUESTED");
        assertThat(count("SELECT COUNT(*) FROM dispatch_record WHERE order_id=UUID_TO_BIN(?)",f.order)).isZero();
        assertThat(count("SELECT COUNT(*) FROM bale_commitment WHERE order_id=UUID_TO_BIN(?)",f.order)).isEqualTo(1);
        recordState("C04-continuation-origin-fixture",f.order);
    }
}
