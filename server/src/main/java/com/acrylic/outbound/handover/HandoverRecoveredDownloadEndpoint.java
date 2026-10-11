package com.acrylic.outbound.handover;

import com.acrylic.outbound.recovery.RecoveryRecipientDownloadInput;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Explicit, default-off adapter for existing receiving routes; trust is configured outside the request. */
@Component
@ConditionalOnProperty(prefix="outbound.handover.recovered-download",name="enabled",havingValue="true")
final class HandoverRecoveredDownloadEndpoint {
    private final RecoveryRecipientDownloadInput input;
    private final HandoverRecoveredTemporaryDownload downloads;
    private final JdbcTemplate jdbc;
    HandoverRecoveredDownloadEndpoint(@Value("${outbound.handover.recovered-download.input}") String file,
            @Value("${outbound.handover.recovered-download.input-sha256}") String independentSha256,
            HandoverRecoveredTemporaryDownload downloads,JdbcTemplate jdbc) {
        input=new RecoveryRecipientDownloadInput(Path.of(file),independentSha256); this.downloads=downloads; this.jdbc=jdbc;
    }
    HandoverRecoveredTemporaryDownload.Result read(UUID caseId,String requestId) {
        return downloads.read(input.load(jdbc.getDataSource(),caseId),requestId);
    }
}
