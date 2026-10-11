package com.acrylic.outbound.recovery;

import com.acrylic.outbound.e2averify.E2aMigrationSnapshot;
import com.acrylic.outbound.handover.HandoverRecoveredTemporaryConfirmation;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.WebApplicationContext;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Configured input is guarded at the actual beforeCommit boundary, including replay. */
final class RecoveryConfirmationHttpInputTailChecks {
    static void verify(WebApplicationContext app,Path attempt,ObjectNode command) throws Exception {
        var jdbc=app.getBean(JdbcTemplate.class); var snapshots=new E2aMigrationSnapshot(jdbc,attempt,attempt.resolve("storage")); var before=snapshots.capture();
        Path file=attempt.resolve("download-http-input.json"); byte[] original=Files.readAllBytes(file); var counter=new AtomicInteger();
        var input=new RecoveryRecipientDownloadInput(file,sha256(original)); var loaded=input.load(jdbc.getDataSource(),UUID.fromString(text(command,"caseId")));
        var guard=new RecoveryRecipientDownloadInput.Context(loaded.session(),loaded.request(),()->{
            if(counter.incrementAndGet()==3) { try { Files.writeString(file,"confirmation beforeCommit input change"); }
                catch(java.io.IOException error) { throw new UncheckedIOException(error); } }
            loaded.requireUnchanged().run();
        });
        RecoveryReceivedDownloadChecks.authenticate(app,attempt);
        try { assertThatThrownBy(()->app.getBean(HandoverRecoveredTemporaryConfirmation.class)
                .confirm(RecoveryReceivedConfirmationChecks.encode(command),guard,"confirmation-input-tail"))
            .hasMessage("RECOVERY_DOWNLOAD_INPUT_PIN_MISMATCH"); assertThat(counter.get()).isEqualTo(3); }
        finally { Files.write(file,original); SecurityContextHolder.clearContext(); }
        assertThat(snapshots.capture()).isEqualTo(before);
        Files.write(attempt.resolve("confirmation-http-input-tail.json"),RecoveryControlJson.encode(com.acrylic.outbound.submissions.SubmissionJson.object()
            .put("beforeCommitInputChangeRefused",true).put("databaseUnchanged",true).put("guardCalls",counter.get())));
    }
    private RecoveryConfirmationHttpInputTailChecks() { }
}
