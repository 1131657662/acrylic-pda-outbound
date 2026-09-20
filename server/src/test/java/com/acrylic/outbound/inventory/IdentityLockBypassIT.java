package com.acrylic.outbound.inventory;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class IdentityLockBypassIT extends IdentityLockFixture {
    @ParameterizedTest @ValueSource(strings={"device","import","pairing"})
    void independentWarehouseAndBypassWritesWaitBeforeWarehouseOrChallengeLocks(String path) throws Exception {
        var first=web();var firstPda=new PdaTestDriver(first);var device=firstPda.register();var second=web();
        var competing=competing(path,second);var held=new CountDownLatch(1);var release=new CountDownLatch(1);var reached=new CountDownLatch(1);
        var evidence=new IdentityLockEvidence(jdbc);var blocker=new AtomicLong();var waiter=new AtomicLong();var once=new AtomicBoolean(true);
        doAnswer(call->{
            boolean a=Thread.currentThread().getName().equals("l1-warehouse-first");
            String actor=a?"firstWarehouse":"secondWarehouse";long connection=evidence.connection(actor);
            if(!a) { waiter.set(connection);reached.countDown(); }
            Object result=call.callRealMethod();evidence.acquired(actor);
            if(a && once.compareAndSet(true,false)) {
                blocker.set(connection);held.countDown();
                if(!release.await(15,TimeUnit.SECONDS)) throw new AssertionError("Warehouse barrier timed out");
            }
            return result;
        }).when(guardTarget()).lock(anyString());
        long start=System.nanoTime();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->{Thread.currentThread().setName("l1-warehouse-first");return firstPda.setStatus(device,"1","DISABLED");});
            Future<String> b;
            try {
                assertThat(held.await(10,TimeUnit.SECONDS)).isTrue();b=pool.submit(competing);
                assertThat(reached.await(10,TimeUnit.SECONDS)).isTrue();evidence.waiting(blocker.get(),waiter.get(),false);
            } finally { release.countDown(); }
            a.get(20,TimeUnit.SECONDS);assertThat(b.get(20,TimeUnit.SECONDS)).isEqualTo("OK");
        }
        evidence.save("C06-"+path,TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start),"OK");
    }
    private Callable<String> competing(String path,PublicationDriver web) throws Exception {
        if(path.equals("pairing")) {
            var pairing=PairingTestDriver.fresh(web);pairing.call("CREATE");pairing.approve();
            var request=pairing.command("CONFIRM",pairing.proof("CONFIRM"));
            return ()->code(mvc.perform(request));
        }
        if(path.equals("import")) {
            var request=multipart("/api/v1/inventory/imports").file(new MockMultipartFile("file","sku.txt","text/plain",
                SkuFixture.row("997X"+UUID.randomUUID().toString().replace("-","").substring(0,7),"302"))).header("Idempotency-Key",UUID.randomUUID().toString());
            return ()->code(web.send(request));
        }
        var pda=new PdaTestDriver(web);var device=pda.register();
        var body=json.createObjectNode().put("expectedVersion","1").put("status","DISABLED").put("reason","第二仓独立写");
        var request=post("/api/v1/devices/"+device.id()+"/status").header("Idempotency-Key",UUID.randomUUID().toString())
            .contentType("application/json").content(body.toString());
        return ()->code(web.send(request));
    }
}
