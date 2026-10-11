package com.acrylic.outbound.recovery;

import com.acrylic.outbound.handover.HandoverRecoveredTemporaryDownload;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

/** Closed concurrent reads either return identical bytes or reject the competing lease. */
final class RecoveryDownloadConcurrentChecks {
    static void verify(WebApplicationContext app,Path attempt,RecoveryReceivedSessionRestart.Reloaded input) throws Exception {
        RecoveryReceivedDownloadChecks.authenticate(app,attempt);
        HandoverRecoveredTemporaryDownload.Result expected;
        try { expected=app.getBean(HandoverRecoveredTemporaryDownload.class).read(input.session(),input.request(),"download-before-concurrent"); }
        finally { SecurityContextHolder.clearContext(); }
        var barrier=new CyclicBarrier(2); var outcomes=new ArrayList<String>();
        try(var workers=Executors.newFixedThreadPool(2)) {
            var calls=new ArrayList<Future<String>>();
            for(int i=0;i<2;i++) calls.add(workers.submit(()->read(app,attempt,input,barrier,expected)));
            for(var call:calls) outcomes.add(call.get(60,TimeUnit.SECONDS));
        }
        assertThat(outcomes).contains("READ").allMatch(v->Set.of("READ","CONTROL_COORDINATOR_BUSY").contains(v));
        Files.writeString(attempt.resolve("download-concurrent.json"),new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(outcomes));
    }
    private static String read(WebApplicationContext app,Path attempt,RecoveryReceivedSessionRestart.Reloaded input,
            CyclicBarrier barrier,HandoverRecoveredTemporaryDownload.Result expected) throws Exception {
        RecoveryReceivedDownloadChecks.authenticate(app,attempt);
        try {
            barrier.await(10,TimeUnit.SECONDS);
            var actual=app.getBean(HandoverRecoveredTemporaryDownload.class).read(input.session(),input.request(),"download-concurrent");
            assertThat(actual.manifest()).isEqualTo(expected.manifest()); assertThat(actual.recoveryPackage()).isEqualTo(expected.recoveryPackage());
            return "READ";
        } catch(RecoveryControlException error) { return error.getMessage(); }
        finally { SecurityContextHolder.clearContext(); }
    }
    private RecoveryDownloadConcurrentChecks() { }
}
