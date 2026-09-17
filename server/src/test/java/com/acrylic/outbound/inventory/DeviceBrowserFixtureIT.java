package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest @AutoConfigureMockMvc
class DeviceBrowserFixtureIT {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc; @Autowired PasswordEncoder passwords;
    @Test void prepareDeviceRegistrationAndIsolatedWarehouseUsers() throws Exception {
        var admin=IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN");
        var other=IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN");
        var viewer=IdentityFixture.create(jdbc,passwords,"ORDER_VIEWER");
        jdbc.update("UPDATE warehouse SET name='设备测试仓A' WHERE id=?",Ids.bytes(admin.warehouse()));
        jdbc.update("UPDATE warehouse SET name='设备测试仓B' WHERE id=?",Ids.bytes(other.warehouse()));
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)",Ids.bytes(admin.user().id()),Ids.bytes(other.warehouse()));
        var pda=new PdaTestDriver(new PublicationDriver(mvc,json,admin));
        for(int i=0;i<51;i++)pda.register();
        var key=KeyPairGenerator.getInstance("EC");key.initialize(new ECGenParameterSpec("secp256r1"));
        var value=json.createObjectNode().put("username",admin.user().username()).put("password","Test-password-123")
            .put("warehouseId",admin.warehouse().toString()).put("otherWarehouseId",other.warehouse().toString())
            .put("viewerUsername",viewer.user().username()).put("publicKey",Base64.getEncoder().encodeToString(key.generateKeyPair().getPublic().getEncoded()));
        Files.writeString(Path.of("build/device-browser-fixture.json"),json.writeValueAsString(value));
    }
}
