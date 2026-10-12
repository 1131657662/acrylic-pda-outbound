package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;

/** Synthetic stock/personnel only: the separate Android app creates its own key, draft and captures. */
@SpringBootTest @AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="P04_DEVICE_INPUT",matches=".+")
class TemporaryExclusionDeviceFixtureIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;

    @Test void preparesFreshStockAndCandidatesWithoutCreatingDeviceOrSubmission() throws Exception {
        var path=Path.of(System.getenv("P04_DEVICE_INPUT")).toAbsolutePath().normalize();
        assertThat(path.toString().replace('\\','/')).startsWith("D:/acrylic-pda-outbound/.local/goal-20261012/");
        assertThat(Files.exists(path)).isFalse();
        var identity=IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN");
        jdbc.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code='TEMPORARY_ORDER_CREATOR'",Ids.bytes(identity.user().id()));
        var api=new PublicationDriver(mvc,json,identity);var orders=new OrderTestDriver(api);
        orders.stock();orders.policy("0",true);
        var plan=orders.plan().put("vehicleNo","P04原机页面验证车");
        plan.withArray("lines").forEach(line->((ObjectNode)line).put("plannedCount",1));
        String target=orders.create(plan).path("orderId").asText();
        api.data(orders.command("/"+target+"/publish",orders.release("1","1"),UUID.randomUUID().toString()));
        String excluded=orders.create(plan).path("orderId").asText();
        api.data(orders.command("/"+excluded+"/cancel",orders.cancellation("1"),UUID.randomUUID().toString()));
        var value=SubmissionJson.object().put("purpose","SYNTHETIC_P04_NATIVE_DEVICE")
            .put("baseUrl","http://127.0.0.1:19435/api/v1/").put("warehouseId",identity.warehouse().toString())
            .put("targetId",target).put("excludedId",excluded).put("vehicleNo",plan.path("vehicleNo").asText());
        value.set("operator",account(identity));value.set("supervisor",supervisor(identity));
        value.putArray("barcodes").add("003B0051601").add("571B0051602");
        Files.createDirectories(path.getParent());Files.writeString(path,SubmissionJson.canonical(value),StandardOpenOption.CREATE_NEW);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submission",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM device",Integer.class)).isZero();
    }

    private ObjectNode supervisor(IdentityFixture operator) {
        var actor=IdentityFixture.create(jdbc,passwords,"SUPERVISOR");
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)",Ids.bytes(actor.user().id()),Ids.bytes(operator.warehouse()));
        var role=Ids.next();
        jdbc.update("INSERT INTO role VALUES(?,?,?)",Ids.bytes(role),"P04_"+role.toString().substring(24),"隔离主管候选权限");
        for(String permission:new String[]{"temporary_order:link","temporary_order:confirm","submission:review","order:read"})
            jdbc.update("INSERT INTO role_permission SELECT ?,id FROM permission WHERE code=?",Ids.bytes(role),permission);
        jdbc.update("INSERT INTO user_role VALUES(?,?)",Ids.bytes(actor.user().id()),Ids.bytes(role));
        return account(actor);
    }

    private ObjectNode account(IdentityFixture actor) {
        return SubmissionJson.object().put("username",actor.user().username()).put("password","Test-password-123");
    }
}
