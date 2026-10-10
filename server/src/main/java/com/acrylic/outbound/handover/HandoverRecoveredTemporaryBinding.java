package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.recovery.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Explicit original-custody input for REQUEST/FAULT_FENCE only. No Bean, route, ticket or release permit. */
public record HandoverRecoveredTemporaryBinding(RecoveryReceivedTemporaryArchiveConsumer consumer,
        RecoveryReceivedTemporaryArchiveConsumer.Request request,JsonNode nativeFacts) {
    public HandoverRecoveredTemporaryBinding {
        Objects.requireNonNull(consumer); Objects.requireNonNull(request); nativeFacts=Objects.requireNonNull(nativeFacts).deepCopy();
    }
    @Override public JsonNode nativeFacts() { return nativeFacts.deepCopy(); }

    void require(HandoverCommand.Input command) {
        var source=request.evidence().expected();
        require(Set.of("REQUEST","FAULT_FENCE").contains(command.kind()) && source.sourceKind().equals("TEMP")
            && Objects.equals(command.uuid("warehouseId"),source.warehouseId()));
        if(command.kind().equals("REQUEST")) require(Objects.equals(command.uuid("scopeId"),source.scopeId()));
        else require(Objects.equals(command.uuid("sourceDeviceId"),source.deviceId()));
    }

    void requireAnchor(HandoverCommand.Input command,JsonNode anchor,RecoveryRecoveredTemporaryArchive archive) {
        var source=archive.source();
        require(text(anchor,"sourceKind").equals("TEMP") && uuid(anchor,"warehouseId").equals(source.warehouseId())
            && uuid(anchor,"scopeId").equals(source.scopeId()) && uuid(anchor,"canonicalOrderId").equals(source.canonicalOrderId())
            && number(anchor,"fromWorkRound")==source.workRound());
        require(uuid(anchor,command.kind().equals("REQUEST")?"currentSubmissionId":"submissionId").equals(source.submissionId()));
    }

    void requireArchive(HandoverCommand.Input command,RecoveryTemporaryNativeHandoverFacts.Facts verified) {
        if(!command.kind().equals("FAULT_FENCE")) return;
        var reference=command.body().path("archivedEvidence");
        require(uuid(reference,"sourceEvidenceSetId").equals(verified.evidenceSetId())
            && uuid(reference,"verificationId").equals(verified.verificationId()) && text(reference,"setSha256").equals(verified.setSha256())
            && number(reference,"captureHighWatermark")==verified.captureHighWatermark()
            && number(reference,"auditThroughSeq")==verified.auditThroughSeq()
            && number(reference,"orderScanHighWatermark")==verified.orderScanHighWatermark());
    }

    private static void require(boolean valid) {
        if(!valid) throw new BusinessException(409,"HANDOVER_RECOVERED_SOURCE_MISMATCH","恢复交接命令须绑定完整原TEMP来源，仅可申请或主管故障围栏");
    }
}
