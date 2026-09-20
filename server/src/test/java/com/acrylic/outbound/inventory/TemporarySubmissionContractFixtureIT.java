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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest @AutoConfigureMockMvc
class TemporarySubmissionContractFixtureIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    @Test void exportOriginalTemporaryBodyAndSignedAbortProofs() throws Exception {
        var t=new TemporarySubmissionFixture(new SubmissionFixture(new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN")),jdbc,false,false));
        var out=json.createObjectNode();out.set("body",t.body);out.set("review",t.submit());out.set("abort",t.abort());
        out.set("signingKey",t.f.pda.data(get("/api/v1/pda/signing-key"),t.f.tokens));
        var before=new TemporarySubmissionFixture(new SubmissionFixture(new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN")),jdbc,false,false));
        out.set("beforeBody",before.body);out.set("beforeAbort",before.abort());
        var work=new TemporaryFixture(new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN")),jdbc);work.stock();
        out.set("preparation",TemporaryContractFixtureIT.exportPreparation(work));
        Files.writeString(Path.of("build","pda-temporary-submission-fixture.json"),json.writeValueAsString(out));
    }
}
