package com.acrylic.outbound.inventory;

import com.acrylic.outbound.auth.Access;
import com.acrylic.outbound.common.Ids;
import java.sql.Connection;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class R08AuthorizationRaceIT extends R08CenterFixture {
    @Autowired DataSource dataSource;
    @MockitoSpyBean Access access;
    @ParameterizedTest @CsvSource({"false,session,401","true,session,401","false,role,403","true,role,403","false,warehouse,404","true,warehouse,404"})
    void committedRevocationAfterControllerPrecheckCannotEnqueue(boolean retry,String revoke,int expected) throws Exception {
        UUID original=id(create(UUID.randomUUID().toString(),"XLSX"));state(original,"FAILED");
        String key=UUID.randomUUID().toString();String day=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).toString();
        var request=retry?retry(original,key):request(key,"XLSX",day,day);
        var checked=new CountDownLatch(1);var resume=new CountDownLatch(1);interceptPrecheck(checked,resume);
        try(var connection=dataSource.getConnection();var pool=Executors.newSingleThreadExecutor()) {
            connection.setAutoCommit(false);revoke(connection,revoke);
            var response=pool.submit(()->api.send(request));
            try {
                assertThat(checked.await(10,TimeUnit.SECONDS)).isTrue();connection.commit();
            } finally { resume.countDown(); }
            response.get(15,TimeUnit.SECONDS).andExpect(status().is(expected));
        }
        assertThat(jobs.findByDedupeKey("r08:"+api.identity().user().id()+":"+key)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_event WHERE actor_id=? AND event_type='REPORT_EXPORT_RETRIED'",
                Long.class,Ids.bytes(api.identity().user().id()))).isZero();
    }
    private void interceptPrecheck(CountDownLatch checked,CountDownLatch resume) {
        var first=new AtomicBoolean(true);
        doAnswer(call->{
            Object result=call.callRealMethod();
            if(first.compareAndSet(true,false)) {
                checked.countDown();
                if(!resume.await(15,TimeUnit.SECONDS)) throw new AssertionError("Revocation barrier timed out");
            }
            return result;
        }).when(access).require(eq("order:read"),eq(api.identity().warehouse()));
    }
    private void revoke(Connection connection,String kind) throws Exception {
        byte[] actor=Ids.bytes(api.identity().user().id());
        try(var lock=connection.prepareStatement("SELECT id FROM app_user WHERE id=? FOR UPDATE")) {
            lock.setBytes(1,actor);try(var ignored=lock.executeQuery()) { assertThat(ignored.next()).isTrue(); }
        }
        String sql=switch(kind) {
            case "session" -> "UPDATE app_user SET auth_version=auth_version+1 WHERE id=?";
            case "role" -> "DELETE FROM user_role WHERE user_id=?";
            case "warehouse" -> "DELETE FROM user_warehouse WHERE user_id=?";
            default -> throw new IllegalArgumentException(kind);
        };
        try(var statement=connection.prepareStatement(sql)) { statement.setBytes(1,actor);statement.executeUpdate(); }
    }
}
