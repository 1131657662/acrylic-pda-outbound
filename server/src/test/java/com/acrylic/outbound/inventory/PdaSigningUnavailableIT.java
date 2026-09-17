package com.acrylic.outbound.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"outbound.signing.private-key=","outbound.signing.public-key="})
@AutoConfigureMockMvc @Transactional
class PdaSigningUnavailableIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    @Test void missingSigningKeyLeavesOrderReadyWithoutAssignment() throws Exception {
        var web=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));var pda=new PdaTestDriver(web);
        String order=pda.readyOrder();var tokens=pda.login(pda.register());
        pda.send(pda.claim(order,UUID.randomUUID().toString()),tokens).andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("SIGNING_KEY_UNAVAILABLE"));
        assertThat(web.data(get("/api/v1/orders/"+order+"/assignment")).get("assignment").isNull()).isTrue();
        assertThat(new OrderTestDriver(web).detail(order).at("/order/status").asText()).isEqualTo("READY");
    }
}
