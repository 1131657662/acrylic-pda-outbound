package com.acrylic.outbound.inventory;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class IdentityLockBusinessIT extends IdentityLockFixture {
    @ParameterizedTest @ValueSource(strings={"review","resume"})
    void personnelUpdatePrecedesPcOriginalOperatorCheck(String action) throws Exception {
        var f=new PcFixture(web(),false,2,1);f.start();f.add(0);f.submit("保留原车短装");
        if(action.equals("resume")) f.send("review",f.reviewBody("REJECT"));
        var supervisor=manager(f.api).web();
        var body=action.equals("review")?f.reviewBody("APPROVE"):json.createObjectNode()
            .put("expectedVersion",f.view().at("/work/version").asText()).put("physicalEvidence","原车原包全部保留");
        var request=f.request(action,body,UUID.randomUUID().toString());
        String before=f.view().at("/work/status").asText();
        assertThat(afterUserDisabled("C01-pc-"+action,f.api,()->code(supervisor.send(request)))).isEqualTo("PC_OPERATOR_UNAVAILABLE");
        assertThat(count("SELECT COUNT(*) FROM dispatch_record WHERE order_id=UUID_TO_BIN(?)",f.order)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM pc_work WHERE order_id=UUID_TO_BIN(?)",String.class,f.order)).isEqualTo(before);
        assertThat(count("SELECT COUNT(*) FROM bale_commitment WHERE order_id=UUID_TO_BIN(?)",f.order)).isEqualTo(1);
    }
    @Test void personnelUpdatePrecedesContinuationOriginalPrincipalCheck() throws Exception {
        var f=new SubmissionFixture(web(),jdbc,false,false);var c=new ContinuationFixture(f);c.request();
        var supervisor=manager(f.web).web();var request=c.approve(c.approveBody(),UUID.randomUUID().toString());
        long assignments=count("SELECT COUNT(*) FROM order_assignment WHERE order_id=UUID_TO_BIN(?)",f.order);
        assertThat(afterUserDisabled("C01-continuation",f.web,()->code(supervisor.send(request))))
            .isEqualTo("OFFLINE_AUTH_REAUTHORIZATION_REQUIRED");
        assertThat(count("SELECT COUNT(*) FROM order_assignment WHERE order_id=UUID_TO_BIN(?)",f.order)).isEqualTo(assignments);
        assertThat(count("SELECT COUNT(*) FROM dispatch_record WHERE order_id=UUID_TO_BIN(?)",f.order)).isZero();
        assertThat(count("SELECT COUNT(*) FROM bale_commitment WHERE order_id=UUID_TO_BIN(?)",f.order)).isEqualTo(1);
    }
}
