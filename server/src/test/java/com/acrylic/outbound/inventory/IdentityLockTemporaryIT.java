package com.acrylic.outbound.inventory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class IdentityLockTemporaryIT extends IdentityLockFixture {
    @ParameterizedTest @CsvSource({"prepare,status","upload,status","abort,status","prepare,admission","upload,admission","abort,admission"})
    void committedDeviceChangeStopsOldRequestWithoutRewritingOriginalSource(String action,String change) throws Exception {
        var f=new TemporarySubmissionFixture(new SubmissionFixture(web(),jdbc,false,false));
        if(action.equals("abort")) f.submit();
        var before=facts(f);var request=request(action,f);var checked=new CountDownLatch(1);var release=new CountDownLatch(1);var once=new AtomicBoolean(true);
        doAnswer(call->{
            if(Thread.currentThread().getName().equals("l1-temporary") && once.compareAndSet(true,false)) {
                checked.countDown();if(!release.await(15,TimeUnit.SECONDS)) throw new AssertionError("TEMP precheck barrier timed out");
            }
            return call.callRealMethod();
        }).when(guardTarget()).lock(anyString());
        try(var pool=Executors.newSingleThreadExecutor(Thread.ofPlatform().name("l1-temporary").factory())) {
            var response=pool.submit(()->code(f.f.pda.send(request,f.f.tokens)));
            try {
                assertThat(checked.await(10,TimeUnit.SECONDS)).isTrue();changeDevice(f,change);
            } finally { release.countDown(); }
            assertThat(response.get(20,TimeUnit.SECONDS)).isEqualTo(change.equals("status")?"DEVICE_DISABLED":"AUTH_EXPIRED");
        }
        assertThat(facts(f)).isEqualTo(before);
    }
    private MockHttpServletRequestBuilder request(String action,TemporarySubmissionFixture f) {
        if(action.equals("prepare")) return get(TemporaryFixture.BASE+"/baseline").header("X-Server-Recovery-Epoch","1");
        if(action.equals("upload")) return f.request("/api/v1/pda/temporary-orders/submissions",f.body);
        return f.request(f.base()+"/abort",f.control().put("reason","已装实物保留"));
    }
    private void changeDevice(TemporarySubmissionFixture f,String change) throws Exception {
        var body=json.createObjectNode().put("expectedVersion","1").put("reason","受控设备权限调整");
        String suffix=change.equals("status")?"/status":"/temporary-order";
        if(change.equals("status")) body.put("status","DISABLED");else body.put("enabled",false);
        f.f.web.data(post("/api/v1/devices/"+f.a.device()+suffix).header("Idempotency-Key",UUID.randomUUID().toString())
            .contentType("application/json").content(body.toString()));
    }
    private Map<String,Object> facts(TemporarySubmissionFixture f) {
        var result=new LinkedHashMap<String,Object>();
        result.put("authorizations",jdbc.queryForList("SELECT BIN_TO_UUID(id) id,manifest_sha256,signed_token,signed_manifest FROM temporary_authorization WHERE device_id=UUID_TO_BIN(?) ORDER BY id",f.a.device()));
        result.put("origins",jdbc.queryForList("SELECT BIN_TO_UUID(authorization_id) authorization,BIN_TO_UUID(client_order_id) clientOrder FROM temporary_work_origin WHERE creator_device_id=UUID_TO_BIN(?)",f.a.device()));
        result.put("submissions",f.submissions());result.put("orders",f.orders());
        result.put("commitments",count("SELECT COUNT(*) FROM bale_commitment WHERE order_id IN (SELECT canonical_order_id FROM submission_scope WHERE creator_device_id=UUID_TO_BIN(?))",f.a.device()));
        return result;
    }
}
