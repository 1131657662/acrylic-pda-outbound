package com.acrylic.outbound.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class RescueDelegationDefaultIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    @Test void productionDefaultRefusesRequestBeforeStoringAnyMaterial() throws Exception {
        var f=new RescueFixture(mvc,json,jdbc,passwords);int before=jdbc.queryForObject("SELECT COUNT(*) FROM rescue_request",Integer.class);
        f.pda.send(f.request(f.signed(f.document),f.key),f.tokens).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("RESCUE_DELEGATION_DISABLED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rescue_request",Integer.class)).isEqualTo(before);
        f.pda.send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/pda/rescue-grants/"+java.util.UUID.randomUUID()),f.tokens)
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESCUE_DELEGATION_DISABLED"));
    }
}
