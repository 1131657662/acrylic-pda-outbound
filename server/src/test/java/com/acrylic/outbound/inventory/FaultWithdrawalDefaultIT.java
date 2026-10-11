package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Uses the unmodified production default; isolation does not implicitly open revocation. */
@SpringBootTest @AutoConfigureMockMvc
class FaultWithdrawalDefaultIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;

    @Test void defaultClosedCommandCannotRevokeCredentialOrLoseOriginalSubmission() throws Exception {
        var f=new SubmissionFixture(new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN")),jdbc,true,false);
        var body=SubmissionJson.object().put("type","FAULT_WITHDRAWAL").put("reason","隔离默认关闭反例")
            .put("expectedVersion",new OrderTestDriver(f.web).detail(f.order).at("/order/rowVersion").asText());
        f.web.send(post("/api/v1/orders/"+f.order+"/change-requests").contentType("application/json")
            .content(body.toString()).header("Idempotency-Key",UUID.randomUUID().toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FAULT_WITHDRAWAL_DISABLED"));
        assertThat(f.count("order_change_request")).isZero();assertThat(f.count("order_revision")).isEqualTo(1);
        assertThat(f.submit().path("status").asText()).isEqualTo("SUCCEEDED");assertThat(f.count("dispatch_record")).isEqualTo(2);
    }
}
