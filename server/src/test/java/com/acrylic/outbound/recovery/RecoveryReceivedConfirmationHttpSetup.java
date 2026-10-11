package com.acrylic.outbound.recovery;

import com.acrylic.outbound.handover.HandoverRecoveredTemporaryDownload;
import java.nio.file.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.WebApplicationContext;

/** Records prerequisites only; the first confirmation is made by the actual product POST. */
public final class RecoveryReceivedConfirmationHttpSetup {
    public static void prepare(WebApplicationContext app,Path attempt,Path original) throws Exception {
        RecoveryReceivedSessionChecks.verify(app,attempt,original);
        var jdbc=app.getBean(JdbcTemplate.class); var input=RecoveryReceivedSessionRestart.reload(jdbc,attempt,original);
        RecoveryReceivedDownloadChecks.authenticate(app,attempt);
        try {
            var document=app.getBean(HandoverRecoveredTemporaryDownload.class).read(input.session(),input.request(),"http-confirmation-original");
            Files.write(attempt.resolve("confirmation-manifest.json"),document.manifest()); Files.write(attempt.resolve("confirmation-payload.json"),document.recoveryPackage());
            Files.write(attempt.resolve("confirmation-command.json"),RecoveryReceivedConfirmationChecks.encode(RecoveryReceivedConfirmationChecks.command(input.session(),document)));
        } finally { SecurityContextHolder.clearContext(); }
        RecoveryReceivedDownloadHttpInput.save(attempt,original);
    }
    public static String[] options(Path attempt) throws Exception {
        return new String[]{"--outbound.handover.recovered-confirmation.enabled=true",
            "--outbound.handover.recovered-confirmation.input="+attempt.resolve("download-http-input.json"),
            "--outbound.handover.recovered-confirmation.input-sha256="+Files.readString(attempt.resolve("download-http-input.sha256"))};
    }
    private RecoveryReceivedConfirmationHttpSetup() { }
}
