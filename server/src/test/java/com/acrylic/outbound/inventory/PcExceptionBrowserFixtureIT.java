package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest @AutoConfigureMockMvc
class PcExceptionBrowserFixtureIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    @Test void generatesIndependentExceptionScenariosWithoutTouchingOriginalIdentities() throws Exception {
        var scenarios=json.createArrayNode();
        for(int index=0;index<8;index++) {
            var identity=IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN");var api=new PublicationDriver(mvc,json,identity);
            var f=new PcExceptionFixture(api,index==7?9:1);var target=f.user(jdbc,passwords,"WAREHOUSE_ADMIN");var viewer=f.user(jdbc,passwords,"ORDER_VIEWER");
            jdbc.update("UPDATE app_user SET display_name=? WHERE id=?","接续人员"+(index+1),Ids.bytes(target.identity().user().id()));
            jdbc.update("UPDATE warehouse SET name=? WHERE id=?","PC处置验证仓"+(index+1),Ids.bytes(identity.warehouse()));
            var scenario=scenarios.addObject().put("username",identity.user().username()).put("password","Test-password-123")
                .put("warehouse",identity.warehouse().toString()).put("order",f.pc.order).put("target",target.identity().user().username())
                .put("viewer",viewer.identity().user().username()).put("query",f.pc.serial);
            scenario.set("barcodes",json.valueToTree(f.pc.barcodes));
        }
        Files.writeString(Path.of("build/pc-exception-browser-fixture.json"),json.writeValueAsString(scenarios));
    }
}
