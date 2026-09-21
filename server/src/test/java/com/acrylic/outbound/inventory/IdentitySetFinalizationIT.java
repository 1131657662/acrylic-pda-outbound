package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class IdentitySetFinalizationIT extends IdentitySetFixture {
    @ParameterizedTest @ValueSource(strings={"review","resume"})
    void changedOriginalOperatorCannotBeAuthorizedAfterOrderLock(String action) throws Exception {
        var f=new PcFixture(web(),false,2,1);f.start();f.add(0);f.submit("L2 original operator fixture");
        if(action.equals("resume")) f.send("review",f.reviewBody("REJECT"));
        var supervisor=manager(f.api).web();var replacement=supervisor.identity().user().id();
        var body=action.equals("review")?f.reviewBody("APPROVE"):json.createObjectNode()
            .put("expectedVersion",f.view().at("/work/version").asText()).put("physicalEvidence","L2 retained original goods");
        var request=f.request(action,body,UUID.randomUUID().toString());String status=f.view().at("/work/status").asText();
        assertThat(changedBeforeLock("C04-pc-"+action+"-origin-fixture",s->s.startsWith("UPDATE pc_work SET operator_id="),
            ()->transactional(()->{
                guard.lock("1");guard.users(List.of(f.api.identity().user().id(),replacement));
                jdbc.queryForList("SELECT id FROM warehouse WHERE id=? FOR UPDATE",Ids.bytes(f.api.identity().warehouse()));
                jdbc.queryForList("SELECT id FROM outbound_order WHERE id=UUID_TO_BIN(?) FOR UPDATE",f.order);
                jdbc.update("UPDATE pc_work SET operator_id=? WHERE order_id=UUID_TO_BIN(?)",Ids.bytes(replacement),f.order);
            }),()->code(supervisor.send(request)))).isEqualTo("PC_IDENTITY_CONTEXT_CHANGED");
        assertThat(jdbc.queryForObject("SELECT status FROM pc_work WHERE order_id=UUID_TO_BIN(?)",String.class,f.order)).isEqualTo(status);
        assertThat(count("SELECT COUNT(*) FROM dispatch_record WHERE order_id=UUID_TO_BIN(?)",f.order)).isZero();
        assertThat(count("SELECT COUNT(*) FROM bale_commitment WHERE order_id=UUID_TO_BIN(?)",f.order)).isEqualTo(1);
        recordState("C04-pc-"+action+"-origin-fixture",f.order);
    }
}
