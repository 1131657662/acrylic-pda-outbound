package com.acrylic.outbound.handover;

import com.acrylic.outbound.recovery.RecoveryControlException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** Closed-session journal contention remains fail-closed; the original command recovers after contention. */
final class HandoverRecoveredPhysicalConcurrency {
    static void verify(HandoverRecoveredTemporaryFixture f,HandoverRecoveredPhysicalReview service,
            HandoverRecoveredPhysicalReview.Request request,JsonNode command,byte[] original) throws Exception {
        var before=f.snapshots.capture(); var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<byte[]> call=()->{ start.await(); return HandoverRecoveredPhysicalChecks.execute(service,f.supervisor,command,request).receipt(); };
            var first=pool.submit(call); var second=pool.submit(call); start.countDown();
            for(var pending:java.util.List.of(first,second)) {
                try { assertThat(pending.get(60,TimeUnit.SECONDS)).isEqualTo(original); }
                catch(ExecutionException blocked) {
                    assertThat(blocked.getCause()).isInstanceOf(RecoveryControlException.class);
                    assertThat(blocked.getCause().getMessage()).isIn(
                        "CONTROL_TRANSACTION_OUTCOME_UNCONFIRMED","CONTROL_COORDINATOR_BUSY");
                }
            }
        }
        assertThat(HandoverRecoveredPhysicalChecks.execute(service,f.supervisor,command,request).receipt()).isEqualTo(original);
        assertThat(f.snapshots.capture()).isEqualTo(before);
    }
    private HandoverRecoveredPhysicalConcurrency() { }
}
