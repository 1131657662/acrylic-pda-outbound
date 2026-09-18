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
class PcBrowserFixtureIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    @Test void generatesIndependentPcBrowserScenarios() throws Exception {
        var scenarios=json.createArrayNode();
        for(int i=0;i<12;i++) {
            var identity=IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN");
            jdbc.update("UPDATE warehouse SET name=? WHERE id=?","PC手工出库验证仓"+(i+1),Ids.bytes(identity.warehouse()));
            var f=new PcFixture(new PublicationDriver(mvc,json,identity),i%2==0,i==9?8:2,8);
            if(i==9) { f.start();for(int entry=0;entry<24;entry++) f.add(entry); }
            if(i==0) for(int extra=0;extra<20;extra++) f.orders.create(f.orders.plan());
            var viewer=IdentityFixture.create(jdbc,passwords,"ORDER_VIEWER");
            jdbc.update("INSERT INTO user_warehouse VALUES(?,?)",Ids.bytes(viewer.user().id()),Ids.bytes(identity.warehouse()));
            var item=scenarios.addObject().put("username",identity.user().username()).put("password","Test-password-123")
                .put("warehouse",identity.warehouse().toString()).put("order",f.order).put("query",f.serial).put("viewer",viewer.user().username());
            item.set("barcodes",json.valueToTree(f.barcodes));
        }
        Files.writeString(Path.of("build/pc-browser-fixture.json"),json.writeValueAsString(scenarios));
    }
}
