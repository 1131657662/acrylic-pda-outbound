package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest @AutoConfigureMockMvc
class UserManagementConcurrencyIT {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc; @Autowired PasswordEncoder passwords;
    private UserManagementDriver fixture() { return new UserManagementDriver(new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"))); }
    @Test void concurrentSameKeyReturnsOneAccountAndReceipt() throws Exception {
        var api=fixture();var body=api.createBody();String key=UUID.randomUUID().toString();var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var one=pool.submit(()->{gate.await();return api.web().data(api.command("",body,key));});
            var two=pool.submit(()->{gate.await();return api.web().data(api.command("",body,key));});gate.countDown();
            assertThat(one.get(20,TimeUnit.SECONDS)).isEqualTo(two.get(20,TimeUnit.SECONDS));
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE username=?",Integer.class,body.get("username").asText())).isEqualTo(1);
    }
    @Test void simultaneousUpdatesCannotOverwriteTheSameVersion() throws Exception {
        var api=fixture();String id=api.create(api.createBody()).get("userId").asText();var body=api.updateBody(api.item(id));var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var one=pool.submit(()->{gate.await();return api.web().send(api.command("/"+id,body,UUID.randomUUID().toString())).andReturn().getResponse().getStatus();});
            var two=pool.submit(()->{gate.await();return api.web().send(api.command("/"+id,body.deepCopy().put("displayName","第二个调整"),UUID.randomUUID().toString())).andReturn().getResponse().getStatus();});gate.countDown();
            assertThat(List.of(one.get(20,TimeUnit.SECONDS),two.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        }
        assertThat(api.item(id).get("rowVersion").asText()).isEqualTo("2");
    }
    @Test void administratorsCannotConcurrentlyDisableEachOther() throws Exception {
        var one=fixture();var other=IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN");
        jdbc.update("DELETE FROM user_warehouse WHERE user_id=?",Ids.bytes(other.user().id()));
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)",Ids.bytes(other.user().id()),Ids.bytes(one.web().identity().warehouse()));
        var two=new UserManagementDriver(new PublicationDriver(mvc,json,new IdentityFixture(other.user(),one.web().identity().warehouse())));
        String firstId=one.web().identity().user().id().toString(),secondId=other.user().id().toString();
        var firstBody=one.updateBody(one.item(secondId)).put("status","DISABLED");
        var secondBody=two.updateBody(two.item(firstId)).put("status","DISABLED");var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->{gate.await();return one.web().send(one.command("/"+secondId,firstBody,UUID.randomUUID().toString())).andReturn().getResponse().getStatus();});
            var b=pool.submit(()->{gate.await();return two.web().send(two.command("/"+firstId,secondBody,UUID.randomUUID().toString())).andReturn().getResponse().getStatus();});gate.countDown();
            assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,401);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE id IN (?,?) AND status='ACTIVE'",Integer.class,
            Ids.bytes(UUID.fromString(firstId)),Ids.bytes(UUID.fromString(secondId)))).isEqualTo(1);
    }
}
