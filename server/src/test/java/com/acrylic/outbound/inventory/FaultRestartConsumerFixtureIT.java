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
import static org.assertj.core.api.Assertions.*;

/** One-shot synthetic identities for browser and Android consumers on the same retained MySQL. */
@SpringBootTest @AutoConfigureMockMvc
class FaultRestartConsumerFixtureIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;

    @Test void prepareAssignedOrdersForRealConsumers() throws Exception {
        String destination=System.getenv("FAULT_RESTART_CONSUMER_INPUT");
        assertThat(destination).isNotBlank();
        var path=Path.of(destination).toAbsolutePath().normalize();
        assertThat(path.toString().replace('\\','/')).startsWith("D:/acrylic-pda-outbound/.local/goal-20261011/");
        assertThat(Files.exists(path)).isFalse();
        var first=new SubmissionFixture(new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN")),jdbc,true,false);
        var storage=new SubmissionFixture(first);
        var android=new SubmissionFixture(new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN")),jdbc,true,false);
        var revocation=new SubmissionFixture(android);
        var value=SubmissionJson.object().put("purpose","SYNTHETIC_FAULT_RESTART_CONSUMERS");
        value.set("web",context(first));value.set("storage",context(storage));value.set("android",context(android).set("revocation",context(revocation)));
        Files.createDirectories(path.getParent());Files.writeString(path,json.writeValueAsString(value),StandardOpenOption.CREATE_NEW);
        assertThat(first.count("dispatch_record")).isZero();assertThat(android.count("dispatch_record")).isZero();
    }
    private com.fasterxml.jackson.databind.node.ObjectNode context(SubmissionFixture f) {
        var value=SubmissionJson.object().put("username",f.web.identity().user().username()).put("password","Test-password-123")
            .put("warehouseId",f.web.identity().warehouse().toString()).put("orderId",f.order);
        value.set("oldAssignment",f.assignment);value.set("oldSession",f.tokens);value.set("oldBody",f.body);
        return value;
    }
}
