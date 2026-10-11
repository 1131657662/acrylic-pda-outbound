package com.acrylic.outbound.recovery;

import com.acrylic.outbound.handover.HandoverRecoveredTemporaryConfirmation;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.List;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

/** Original intent, old epoch and receiving identity mismatches cannot create confirmation evidence. */
final class RecoveryConfirmationNegativeChecks {
    static void verify(WebApplicationContext app,Path attempt,RecoveryReceivedSessionRestart.Reloaded input,ObjectNode original) throws Exception {
        for(String field:List.of("manifestSha256","preparedTicketSha256","payloadSha256","checklistSha256","qualificationSha256","recipientSessionSha256"))
            reject(app,attempt,input,original.deepCopy().put(field,"a".repeat(64)));
        reject(app,attempt,input,original.deepCopy().put("transportRecoveryEpoch",original.path("sourceRecoveryEpoch").asText()));
        reject(app,attempt,input,original.deepCopy().put("sourceRecoveryEpoch",original.path("transportRecoveryEpoch").asText()));
        reject(app,attempt,input,original.deepCopy().put("qualificationId",java.util.UUID.randomUUID().toString()));
        reject(app,attempt,input,original.deepCopy().put("physicalConfirmed",false));
        assertThatThrownBy(()->app.getBean(HandoverRecoveredTemporaryConfirmation.class).confirm(RecoveryReceivedConfirmationChecks.encode(original),
            new RecoveryRecipientDownloadInput.Context(input.session(),input.request(),()->{}),"missing-confirmation-identity"))
            .isInstanceOf(RecoveryControlException.class).hasMessage("RECOVERY_SESSION_PDA_REQUIRED");
    }
    private static void reject(WebApplicationContext app,Path attempt,RecoveryReceivedSessionRestart.Reloaded input,ObjectNode command) {
        assertThatThrownBy(()->RecoveryReceivedConfirmationChecks.confirm(app,attempt,input,command))
            .isInstanceOf(com.acrylic.outbound.common.BusinessException.class);
    }
    private RecoveryConfirmationNegativeChecks() { }
}
