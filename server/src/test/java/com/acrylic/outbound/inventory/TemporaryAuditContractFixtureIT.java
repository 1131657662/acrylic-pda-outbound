package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class TemporaryAuditContractFixtureIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    @Test void exportSignedMixedArchiveForAndroid() throws Exception {
        var formal=new SubmissionFixture(new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN")),jdbc,false,false);
        var a=new TemporaryAuditFixture(formal);var mixed=a.mixed(a.event(8));a.send(mixed);
        var fixture=SubmissionJson.object();fixture.set("payload",mixed.deepCopy().put("afterSeq","0").put("throughSeq","8"));
        fixture.set("proof",a.archive(mixed));fixture.set("signingKey",formal.pda.data(get("/api/v1/pda/signing-key"),formal.tokens));
        Files.writeString(Path.of("build","pda-temporary-audit-fixture.json"),SubmissionJson.canonical(fixture));
    }
}
