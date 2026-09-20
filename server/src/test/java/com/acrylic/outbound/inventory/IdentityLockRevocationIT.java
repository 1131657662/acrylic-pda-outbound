package com.acrylic.outbound.inventory;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import com.acrylic.outbound.common.Ids;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdentityLockRevocationIT extends IdentityLockFixture {
    @ParameterizedTest @CsvSource({"inventory,status","inventory,role","inventory,warehouse","order,status","order,role","order,warehouse","pc,status","pc,role","pc,warehouse"})
    void realPersonnelRevocationAfterWebPrecheckPreventsMutation(String path,String change) throws Exception {
        var web=web();var manager=manager(web);String target=web.identity().user().id().toString();
        var f=new PcFixture(web,false,2,1);
        if(change.equals("warehouse")) manager=otherWarehouse(manager,web);
        final var administration=manager;
        MockHttpServletRequestBuilder request=request(path,f);
        String permission=switch(path) { case "inventory" -> "inventory:publish";case "order" -> "order:publish";default -> "submission:review"; };
        var checked=new CountDownLatch(1);var release=new CountDownLatch(1);var first=new AtomicBoolean(true);
        doAnswer(call->{
            Object result=call.callRealMethod();
            if(Thread.currentThread().getName().equals("l1-precheck") && first.compareAndSet(true,false)) {
                checked.countDown();if(!release.await(15,TimeUnit.SECONDS)) throw new AssertionError("Precheck barrier timed out");
            }
            return result;
        }).when(access).require(permission,web.identity().warehouse());
        long dispatches=count("SELECT COUNT(*) FROM dispatch_record WHERE order_id=UUID_TO_BIN(?)",f.order);
        try(var pool=Executors.newSingleThreadExecutor(Thread.ofPlatform().name("l1-precheck").factory())) {
            var result=pool.submit(()->code(web.send(request)));
            try {
                assertThat(checked.await(10,TimeUnit.SECONDS)).isTrue();revoke(administration,target,change);
            } finally { release.countDown(); }
            assertThat(result.get(20,TimeUnit.SECONDS)).isEqualTo("SESSION_REVOKED");
        }
        assertThat(count("SELECT COUNT(*) FROM dispatch_record WHERE order_id=UUID_TO_BIN(?)",f.order)).isEqualTo(dispatches);
    }
    private UserManagementDriver otherWarehouse(UserManagementDriver manager,PublicationDriver target) {
        UUID other=UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(warehouse_id) FROM user_warehouse WHERE user_id=? AND warehouse_id<>?",String.class,
            Ids.bytes(manager.web().identity().user().id()),Ids.bytes(target.identity().warehouse())));
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)",Ids.bytes(target.identity().user().id()),Ids.bytes(other));
        return new UserManagementDriver(new PublicationDriver(mvc,json,new IdentityFixture(manager.web().identity().user(),other)));
    }
    private void revoke(UserManagementDriver manager,String target,String change) throws Exception {
        var body=manager.updateBody(manager.item(target));
        switch(change) {
            case "status" -> body.put("status","DISABLED");
            case "role" -> body.putArray("roles").add("PDA_OPERATOR");
            case "warehouse" -> body.putArray("warehouseIds").add(manager.web().identity().warehouse().toString());
            default -> throw new IllegalArgumentException(change);
        }
        manager.web().data(manager.command("/"+target,body,UUID.randomUUID().toString()));
    }
    private MockHttpServletRequestBuilder request(String path,PcFixture f) throws Exception {
        if(path.equals("inventory")) {
            String id=f.api.stage(SkuFixture.row("997X"+UUID.randomUUID().toString().replace("-","").substring(0,7),"301"));
            return f.api.publication(id,f.api.decisions(id),UUID.randomUUID().toString());
        }
        if(path.equals("order")) return f.orders.command("/"+f.order+"/publish",f.orders.release("1","1"),UUID.randomUUID().toString());
        f.start();f.add(0);f.submit("短装待审");return f.request("review",f.reviewBody("APPROVE"),UUID.randomUUID().toString());
    }
}
