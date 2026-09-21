package com.acrylic.outbound.inventory;

import com.acrylic.outbound.auth.Access;
import com.acrylic.outbound.common.*;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest @AutoConfigureMockMvc
abstract class IdentityLockFixture {
    static {
        String url=Objects.toString(System.getenv("OUTBOUND_DB_URL"),"");
        boolean original=url.startsWith("jdbc:mysql://127.0.0.1:13316/acrylic_outbound_l1_20260920?")
            && "outbound_l1_20260920".equals(System.getenv("OUTBOUND_DB_USER"));
        boolean combined=url.startsWith("jdbc:mysql://127.0.0.1:13316/acrylic_outbound_wave3_20260920?")
            && "outbound_wave3_20260920".equals(System.getenv("OUTBOUND_DB_USER"));
        boolean secondLocks=url.startsWith("jdbc:mysql://127.0.0.1:19321/acrylic_outbound_l2_20260921?")
            && "outbound_l2_20260921".equals(System.getenv("OUTBOUND_DB_USER"));
        boolean fourthWave=url.startsWith("jdbc:mysql://127.0.0.1:19322/acrylic_outbound_wave4_20260921?")
            && "outbound_wave4_20260921".equals(System.getenv("OUTBOUND_DB_USER"));
        if(!original && !combined && !secondLocks && !fourthWave)
            throw new IllegalStateException("Identity lock tests require a dedicated database and matching account");
    }
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired PasswordEncoder passwords;
    @MockitoSpyBean JdbcTemplate jdbc;
    @MockitoSpyBean Access access;
    @MockitoSpyBean IdentityWriteGuard guard;
    @BeforeEach void isolatedDatabase() {
        String expected="outbound_wave3_20260920".equals(System.getenv("OUTBOUND_DB_USER"))
            ? "acrylic_outbound_wave3_20260920" : "acrylic_outbound_l1_20260920";
        if("outbound_l2_20260921".equals(System.getenv("OUTBOUND_DB_USER"))) expected="acrylic_outbound_l2_20260921";
        if("outbound_wave4_20260921".equals(System.getenv("OUTBOUND_DB_USER"))) expected="acrylic_outbound_wave4_20260921";
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).isEqualTo(expected);
    }
    PublicationDriver web() {
        return new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));
    }
    UserManagementDriver manager(PublicationDriver target) {
        var manager=IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN");
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)",Ids.bytes(manager.user().id()),Ids.bytes(target.identity().warehouse()));
        return new UserManagementDriver(new PublicationDriver(mvc,json,new IdentityFixture(manager.user(),target.identity().warehouse())));
    }
    String code(ResultActions response) throws Exception {
        return json.readTree(response.andReturn().getResponse().getContentAsString()).get("code").asText();
    }
    String afterUserDisabled(String scenario,PublicationDriver target,Callable<String> competing) throws Exception {
        var manager=manager(target);String id=target.identity().user().id().toString();
        var body=manager.updateBody(manager.item(id)).put("status","DISABLED");
        var request=manager.command("/"+id,body,UUID.randomUUID().toString());
        var paused=new CountDownLatch(1);var release=new CountDownLatch(1);var reached=new CountDownLatch(1);
        var evidence=new IdentityLockEvidence(jdbc);var blocker=new AtomicLong();var waiter=new AtomicLong();
        interceptUserWrite(paused,release,evidence,blocker);interceptGuard(reached,evidence,waiter);
        long started=System.nanoTime();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->{Thread.currentThread().setName("l1-personnel");return code(manager.web().send(request));});
            Future<String> b;
            try {
                assertThat(paused.await(10,TimeUnit.SECONDS)).isTrue();
                b=pool.submit(()->{Thread.currentThread().setName("l1-competing");return competing.call();});
                assertThat(reached.await(10,TimeUnit.SECONDS)).isTrue();evidence.waiting(blocker.get(),waiter.get(),true);
            } finally { release.countDown(); }
            assertThat(a.get(20,TimeUnit.SECONDS)).isEqualTo("OK");String result=b.get(20,TimeUnit.SECONDS);
            evidence.save(scenario,TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started),result);return result;
        }
    }
    private void interceptUserWrite(CountDownLatch paused,CountDownLatch release,IdentityLockEvidence evidence,AtomicLong id) {
        doAnswer(call->{
            Object result=call.callRealMethod();
            if(Thread.currentThread().getName().equals("l1-personnel")) {
                id.set(jdbc.queryForObject("SELECT CONNECTION_ID()",Long.class));paused.countDown();
                if(!release.await(15,TimeUnit.SECONDS)) throw new AssertionError("User update barrier timed out");
            }
            return result;
        }).when(jdbc).update(eq("UPDATE app_user SET display_name=?,status=?,auth_version=auth_version+1 WHERE id=?"),any(Object[].class));
    }
    private void interceptGuard(CountDownLatch reached,IdentityLockEvidence evidence,AtomicLong id) {
        org.mockito.stubbing.Answer<Object> answer=call->{
            boolean competing=Thread.currentThread().getName().equals("l1-competing");
            String actor=competing?"competing":"personnel";
            long connection=evidence.connection(actor);
            if(competing) { id.set(connection);reached.countDown(); }
            Object result=call.callRealMethod();evidence.acquired(actor);return result;
        };
        doAnswer(answer).when(guardTarget()).lock(anyString());doAnswer(answer).when(guardTarget()).authentication();
    }
    IdentityWriteGuard guardTarget() { return org.springframework.test.util.AopTestUtils.getUltimateTargetObject(guard); }
    long count(String sql,Object... args) { return jdbc.queryForObject(sql,Long.class,args); }
}
