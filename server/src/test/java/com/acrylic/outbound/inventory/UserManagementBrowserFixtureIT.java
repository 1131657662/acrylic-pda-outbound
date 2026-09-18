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
class UserManagementBrowserFixtureIT {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc; @Autowired PasswordEncoder passwords;
    @Test void prepareIsolatedManagedUsersForBrowserChecks() throws Exception {
        var admin=IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN");var other=IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN");
        var viewer=IdentityFixture.create(jdbc,passwords,"ORDER_VIEWER");
        jdbc.update("UPDATE warehouse SET name='人员验证一仓' WHERE id=?",Ids.bytes(admin.warehouse()));
        jdbc.update("UPDATE warehouse SET name='人员验证二仓' WHERE id=?",Ids.bytes(other.warehouse()));
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)",Ids.bytes(admin.user().id()),Ids.bytes(other.warehouse()));
        var api=new UserManagementDriver(new PublicationDriver(mvc,json,admin));
        for(int i=0;i<22;i++) api.create(api.createBody().put("displayName","现场人员"+String.format("%02d",i+1)));
        var value=json.createObjectNode().put("username",admin.user().username()).put("password","Test-password-123")
            .put("warehouseId",admin.warehouse().toString()).put("otherWarehouseId",other.warehouse().toString())
            .put("viewer",viewer.user().username());
        Files.writeString(Path.of("build/user-management-browser-fixture.json"),json.writeValueAsString(value));
    }
}
