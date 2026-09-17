package com.acrylic.outbound.inventory;

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

@SpringBootTest @AutoConfigureMockMvc
class PairingConcurrencyIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    private PairingTestDriver fixture() throws Exception {
        return PairingTestDriver.fresh(new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN")));
    }
    @Test void twoAdministratorsCannotReplaceApprovedWarehouseOrName() throws Exception {
        var p=fixture();p.call("CREATE");var other=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));
        var one=p.approval(p.approvalBody(),UUID.randomUUID().toString());var two=p.approval(p.approvalBody().put("name","其他管理员"),UUID.randomUUID().toString());
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->{start.await();return p.web().send(one).andReturn().getResponse().getStatus();});
            var second=pool.submit(()->{start.await();return other.send(two).andReturn().getResponse().getStatus();});start.countDown();
            assertThat(List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS))).contains(200).allMatch(s->s==200||s==404||s==409);
            assertThat(List.of(first.get(),second.get()).stream().filter(s->s==200).count()).isEqualTo(1);
        }
    }
    @Test void competingConfirmationsReturnOneDeviceIdentity() throws Exception {
        var p=fixture();p.call("CREATE");p.approve();var one=p.command("CONFIRM",p.proof("CONFIRM"));var two=p.command("CONFIRM",p.proof("CONFIRM"));
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->{start.await();return p.data(one);});
            var second=pool.submit(()->{start.await();return p.data(two);});start.countDown();
            assertThat(first.get(20,TimeUnit.SECONDS).get("deviceId")).isEqualTo(second.get(20,TimeUnit.SECONDS).get("deviceId"));
        }
    }
    @Test void cancellationAndConfirmationHaveOneAtomicOutcome() throws Exception {
        var p=fixture();p.call("CREATE");p.approve();var one=p.command("CONFIRM",p.proof("CONFIRM"));var two=p.command("CANCEL",p.proof("CANCEL"));
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->{start.await();return p.data(one);});
            var second=pool.submit(()->{start.await();return p.data(two);});start.countDown();
            assertThat(first.get(20,TimeUnit.SECONDS).get("status")).isEqualTo(second.get(20,TimeUnit.SECONDS).get("status"));
        }
        var finalState=p.call("STATUS");int count=jdbc.queryForObject("SELECT COUNT(*) FROM device WHERE public_key=?",Integer.class,p.payload().get("publicKey").asText());
        assertThat(count).isEqualTo(finalState.get("status").asText().equals("COMPLETED")?1:0);
    }
    @Test void duplicateDeviceCodeRollsBackFinalRegistrationAndOriginalApplicationRemainsRecoverable() throws Exception {
        var p=fixture();p.call("CREATE");p.approve();var other=fixture();other.call("CREATE");
        var approval=other.web().data(other.approval(other.approvalBody().put("deviceCode",p.approvalBody().get("deviceCode").asText()),UUID.randomUUID().toString()));
        other.payload().put("approvalSha256",approval.get("approvalSha256").asText());p.call("CONFIRM");
        other.web().mvc().perform(other.command("CONFIRM",other.proof("CONFIRM"))).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());
        assertThat(other.call("STATUS").get("status").asText()).isEqualTo("PENDING");
        assertThat(other.call("CANCEL").get("status").asText()).isEqualTo("CANCELLED");
    }
}
