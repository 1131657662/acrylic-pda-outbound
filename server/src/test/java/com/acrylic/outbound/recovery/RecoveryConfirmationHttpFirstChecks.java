package com.acrylic.outbound.recovery;

import com.acrylic.outbound.e2averify.E2aMigrationSnapshot;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Two first product POSTs must create exactly one immutable recovery confirmation. */
final class RecoveryConfirmationHttpFirstChecks {
    static void verify(Path attempt,RecoveryReceivedConfirmationHttpChecks.Request request,E2aMigrationSnapshot snapshots) throws Exception {
        var before=snapshots.capture(); byte[] receipt=concurrent(attempt,request); var after=snapshots.capture();
        conserve(before,after);
        Files.write(attempt.resolve("confirmation-receipt.json"),receipt);
        Files.writeString(attempt.resolve("confirmation-before.json"),before.toPrettyString());
        Files.writeString(attempt.resolve("confirmation-after.json"),after.toPrettyString());
        Files.write(attempt.resolve("confirmation-result.json"),RecoveryControlJson.encode(SubmissionJson.object()
            .put("sourcePid",ProcessHandle.current().pid()).put("firstConfirmationThroughProductHttp",true)
            .put("firstConcurrentProductPosts",true).put("fullOriginalRowsConserved",true)));
    }
    private static byte[] concurrent(Path attempt,RecoveryReceivedConfirmationHttpChecks.Request request) throws Exception {
        var barrier=new CyclicBarrier(2); var outcomes=SubmissionJson.object().putArray("outcomes"); var responses=new ArrayList<byte[]>();
        try(var workers=Executors.newFixedThreadPool(2)) {
            var calls=new ArrayList<Future<HttpResponse<byte[]>>>();
            for(int i=0;i<2;i++) calls.add(workers.submit(()->{barrier.await(10,TimeUnit.SECONDS);
                return RecoveryReceivedConfirmationHttpChecks.post(request,request.command(),text(request.command(),"transportRecoveryEpoch"));}));
            for(var call:calls) {
                var response=call.get(90,TimeUnit.SECONDS);
                if(response.statusCode()==200) responses.add(response.body());
                else { assertThat(response.statusCode()).isEqualTo(503);
                    assertThat(SubmissionJson.parse(response.body()).path("code").asText()).isEqualTo("CONTROL_COORDINATOR_BUSY"); }
                outcomes.addObject().put("status",response.statusCode()).put("bodySha256",sha256(response.body()));
            }
        }
        assertThat(responses).isNotEmpty(); for(byte[] response:responses) assertThat(response).isEqualTo(responses.getFirst());
        Files.write(attempt.resolve("confirmation-http-first-concurrent.json"),RecoveryControlJson.encode(outcomes));
        return responses.getFirst();
    }
    private static void conserve(JsonNode before,JsonNode after) {
        assertThat(after.path("tables").size()).isEqualTo(before.path("tables").size());
        before.path("tables").fields().forEachRemaining(e->{if(!e.getKey().equals("recovery_handover_confirmation"))
            assertThat(after.path("tables").path(e.getKey())).as(e.getKey()).isEqualTo(e.getValue());});
        assertThat(before.path("tables").path("recovery_handover_confirmation").path("rows").size()).isZero();
        assertThat(after.path("tables").path("recovery_handover_confirmation").path("rows").size()).isEqualTo(1);
        assertThat(after.path("files")).isEqualTo(before.path("files")); assertThat(after.path("flywayHistory")).isEqualTo(before.path("flywayHistory"));
    }
    private RecoveryConfirmationHttpFirstChecks() { }
}
