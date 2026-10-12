package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;

/** Stock and synthetic personnel only; the original Android creates every device and work fact. */
@SpringBootTest @AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="P05_DEVICE_INPUT",matches=".+")
class TemporaryNonzeroCancellationFixtureIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;

    @Test void preparesIsolatedStockWithoutSourceEvidenceOrDevice() throws Exception {
        var path=Path.of(System.getenv("P05_DEVICE_INPUT")).toAbsolutePath().normalize();
        assertThat(path.toString().replace('\\','/')).startsWith("D:/acrylic-pda-outbound/.local/goal-20261012/");
        assertThat(Files.exists(path)).isFalse();
        assertThat(jdbc.queryForObject("SELECT @@port",Integer.class)).isEqualTo(19331);
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).matches("acrylic_e2a_cross_20260925_140[0-9]");
        var operator=IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN");
        jdbc.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code='TEMPORARY_ORDER_CREATOR'",Ids.bytes(operator.user().id()));
        var orders=new OrderTestDriver(new PublicationDriver(mvc,json,operator));
        orders.stock();orders.policy("0",true);
        var value=SubmissionJson.object().put("purpose","SYNTHETIC_P05_NATIVE_DEVICE")
            .put("baseUrl","http://127.0.0.1:19435/api/v1/").put("warehouseId",operator.warehouse().toString())
            .put("vehicleNo","P05原机主管取消验证车");
        value.set("operator",account(operator));value.set("supervisor",supervisor(operator));
        value.putArray("barcodes").add("003B0051601").add("571B0051602");
        Files.createDirectories(path.getParent());Files.writeString(path,SubmissionJson.canonical(value),StandardOpenOption.CREATE_NEW);
        for(String table:new String[]{"device","submission","source_evidence_set","source_evidence_verification"})
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class)).isZero();
    }

    private ObjectNode supervisor(IdentityFixture operator) {
        var actor=IdentityFixture.create(jdbc,passwords,"SUPERVISOR");
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)",Ids.bytes(actor.user().id()),Ids.bytes(operator.warehouse()));
        var role=Ids.next();
        jdbc.update("INSERT INTO role VALUES(?,?,?)",Ids.bytes(role),"P05_"+role.toString().substring(24),"隔离主管实物关闭权限");
        for(String permission:new String[]{"temporary_order:confirm","submission:review","order:dispose","order:read"})
            jdbc.update("INSERT INTO role_permission SELECT ?,id FROM permission WHERE code=?",Ids.bytes(role),permission);
        jdbc.update("INSERT INTO user_role VALUES(?,?)",Ids.bytes(actor.user().id()),Ids.bytes(role));
        return account(actor);
    }

    private ObjectNode account(IdentityFixture actor) {
        return SubmissionJson.object().put("username",actor.user().username()).put("password","Test-password-123");
    }
}
