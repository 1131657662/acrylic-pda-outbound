package com.acrylic.outbound.inventory;

import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest @AutoConfigureMockMvc
class TemporaryReviewContractIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    @Test void producesRealReadOnlyResponsesForWebContractValidation() throws Exception {
        var f=new TemporaryReviewFixture(jdbc,mvc,json,passwords);f.temporary.submit();
        var sample=json.createObjectNode();sample.set("detail",f.detail());sample.set("page",f.list());
        sample.set("scans",f.reviewer.data(get(f.path()+"/scans")));
        assertThat(sample.at("/detail/writeConfirmationAvailable").asBoolean()).isFalse();
        var target=Path.of("build/temporary-review-contract.json");Files.createDirectories(target.getParent());
        Files.writeString(target,json.writeValueAsString(sample));
    }
}
