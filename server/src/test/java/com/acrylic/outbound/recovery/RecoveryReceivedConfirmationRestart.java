package com.acrylic.outbound.recovery;

import com.acrylic.outbound.e2averify.E2aMigrationSnapshot;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

/** Another JVM verifies the original session, exact command and receipt; no re-enrollment or new signature. */
public final class RecoveryReceivedConfirmationRestart {
    public static void verify(WebApplicationContext app,Path attempt,Path original) throws Exception {
        var jdbc=app.getBean(JdbcTemplate.class); var input=RecoveryReceivedSessionRestart.reload(jdbc,attempt,original);
        var result=SubmissionJson.parse(Files.readAllBytes(attempt.resolve("confirmation-result.json")));
        assertThat(result.path("sourcePid").asLong()).isNotEqualTo(ProcessHandle.current().pid());
        var snapshots=new E2aMigrationSnapshot(jdbc,attempt,attempt.resolve("storage")); var before=snapshots.capture();
        var command=SubmissionJson.parse(Files.readAllBytes(attempt.resolve("confirmation-command.json")));
        byte[] receipt=RecoveryReceivedConfirmationChecks.confirm(app,attempt,input,command);
        assertThat(receipt).isEqualTo(Files.readAllBytes(attempt.resolve("confirmation-receipt.json"))); assertThat(snapshots.capture()).isEqualTo(before);
        var changed=((com.fasterxml.jackson.databind.node.ObjectNode)command.deepCopy()).put("commandId",java.util.UUID.randomUUID().toString());
        assertThatThrownBy(()->RecoveryReceivedConfirmationChecks.confirm(app,attempt,input,changed))
            .isInstanceOfSatisfying(com.acrylic.outbound.common.BusinessException.class,error->assertThat(error.code()).isEqualTo("IDEMPOTENCY_CONTENT_MISMATCH"));
        assertThat(snapshots.capture()).isEqualTo(before);
        Files.write(attempt.resolve("confirmation-restart-result.json"),RecoveryReceivedConfirmationChecks.encode(SubmissionJson.object()
            .put("pid",ProcessHandle.current().pid()).put("sourcePid",result.path("sourcePid").asLong()).put("originalReceiptBytes",true)
            .put("originalPinsAndLogin",true).put("databaseUnchanged",true).put("newLoginIssued",false).put("newSignatureIssued",false)
            .put("changedCommandRefused",true).put("mayResumeScanning",false).put("localDeletionAllowed",false)));
    }
    private RecoveryReceivedConfirmationRestart() { }
}
