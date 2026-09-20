package com.acrylic.outbound.inventory;

import com.acrylic.outbound.audit.AuditWriter;
import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class DeviceTemporaryOrderAtomicityIT {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc; @Autowired PasswordEncoder passwords;
    @MockitoSpyBean AuditWriter audit;
    private PublicationDriver web;private PdaTestDriver pda;
    @BeforeEach void fixture() {
        web=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));pda=new PdaTestDriver(web);
    }
    @Test void auditFailureRollsBackAdmissionVersionSessionAndReceipt() throws Exception {
        var device=pda.register();var tokens=pda.login(device);String key=UUID.randomUUID().toString();
        doThrow(new IllegalStateException("injected audit failure")).when(audit).append(any(),anyString());
        try { web.send(command(device.id(),key,true)).andExpect(status().isInternalServerError()); }
        finally { doCallRealMethod().when(audit).append(any(),anyString()); }
        var item=web.data(get("/api/v1/devices")).at("/items/0");
        assertThat(item.get("rowVersion").asText()).isEqualTo("1");assertThat(item.get("temporaryOrderEnabled").asBoolean()).isFalse();
        pda.send(get("/api/v1/pda/devices/me"),tokens).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM device_command_receipt WHERE device_id=?",Integer.class,Ids.bytes(UUID.fromString(device.id())))).isEqualTo(1);
        assertThat(web.data(command(device.id(),key,true)).get("rowVersion").asText()).isEqualTo("2");
    }
    @Test void concurrentReplayProducesOneReceiptAndOneVersionChange() throws Exception {
        var device=pda.register();String key=UUID.randomUUID().toString();var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var one=pool.submit(()->{gate.await();return web.data(command(device.id(),key,true));});
            var two=pool.submit(()->{gate.await();return web.data(command(device.id(),key,true));});gate.countDown();
            assertThat(one.get(20,TimeUnit.SECONDS)).isEqualTo(two.get(20,TimeUnit.SECONDS));
        }
        assertThat(web.data(get("/api/v1/devices")).at("/items/0/rowVersion").asText()).isEqualTo("2");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM device_command_receipt WHERE device_id=?",Integer.class,Ids.bytes(UUID.fromString(device.id())))).isEqualTo(2);
    }
    @Test void competingChangesCannotOverwriteTheSameDeviceVersion() throws Exception {
        var device=pda.register();var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var one=pool.submit(()->{gate.await();return web.send(command(device.id(),UUID.randomUUID().toString(),true)).andReturn().getResponse().getStatus();});
            var two=pool.submit(()->{gate.await();return web.send(command(device.id(),UUID.randomUUID().toString(),false)).andReturn().getResponse().getStatus();});gate.countDown();
            assertThat(List.of(one.get(20,TimeUnit.SECONDS),two.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        }
        assertThat(web.data(get("/api/v1/devices")).at("/items/0/rowVersion").asText()).isEqualTo("2");
    }
    private MockHttpServletRequestBuilder command(String device,String key,boolean enabled) {
        var body=json.createObjectNode().put("expectedVersion","1").put("enabled",enabled).put("reason","并发或故障核验");
        return post("/api/v1/devices/"+device+"/temporary-order").header("Idempotency-Key",key).contentType("application/json").content(body.toString());
    }
}
