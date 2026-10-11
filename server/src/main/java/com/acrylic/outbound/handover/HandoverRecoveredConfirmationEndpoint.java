package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.recovery.RecoveryRecipientDownloadInput;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Default-off configured trust for one scoped confirmation; HTTP input never supplies authority. */
@Component
@ConditionalOnProperty(prefix="outbound.handover.recovered-confirmation",name="enabled",havingValue="true")
final class HandoverRecoveredConfirmationEndpoint {
    private final RecoveryRecipientDownloadInput input;
    private final HandoverRecoveredTemporaryConfirmation confirmations;
    private final JdbcTemplate jdbc;
    HandoverRecoveredConfirmationEndpoint(@Value("${outbound.handover.recovered-confirmation.input}") String file,
            @Value("${outbound.handover.recovered-confirmation.input-sha256}") String pin,
            HandoverRecoveredTemporaryConfirmation confirmations,JdbcTemplate jdbc) {
        input=new RecoveryRecipientDownloadInput(Path.of(file),pin); this.confirmations=confirmations; this.jdbc=jdbc;
    }
    byte[] confirm(UUID caseId,String epoch,byte[] raw,String requestId) {
        var command=HandoverRecoveredConfirmationCommand.parse(raw); var body=command.body();
        if(!caseId.toString().equals(body.path("caseId").asText()) || !body.path("transportRecoveryEpoch").asText().equals(epoch))
            throw new BusinessException(409,"RECOVERED_CONFIRMATION_CHANGED","接收案件或目标恢复代次不一致，请保留原请求");
        return confirmations.confirm(raw,input.load(jdbc.getDataSource(),caseId),requestId);
    }
}
