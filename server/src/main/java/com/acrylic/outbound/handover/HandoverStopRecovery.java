package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.pda.AssignmentStore;
import com.acrylic.outbound.recall.RecallStore;
import com.acrylic.outbound.recall.RecallStops;
import com.acrylic.outbound.recall.RecoveryEvidence;
import com.acrylic.outbound.submissions.SubmissionFiles;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Reads the durable original bytes and revalidates their source, capture coverage and complete device audit. */
@Component
final class HandoverStopRecovery {
    private final RecallStore recalls;
    private final RecallStops stops;
    private final AssignmentStore assignments;
    private final RecoveryEvidence evidence;
    private final SubmissionFiles files;
    private final HandoverTemporaryStopEvidence temporary;

    HandoverStopRecovery(RecallStore recalls, RecallStops stops, AssignmentStore assignments,
            RecoveryEvidence evidence, SubmissionFiles files, HandoverTemporaryStopEvidence temporary) {
        this.recalls = recalls; this.stops = stops; this.assignments = assignments;
        this.evidence = evidence; this.files = files;
        this.temporary = temporary;
    }

    JsonNode stop(HandoverStopCommand command, HandoverStopStore.Origin origin) {
        if (command.temporary() != null) return temporary.verify(command, origin);
        var recall = recalls.get(command.recallId());
        require(recall.warehouse().equals(origin.warehouse()) && recall.order().equals(origin.order())
            && recall.assignment().equals(origin.assignment()) && recall.epoch().equals(origin.epoch()));
        recall = recalls.lock(recall);
        require(Set.of("REQUESTED", "STOPPED").contains(recall.status()));
        var pack = recalls.recovery(recall.id());
        require(pack != null && pack.id().equals(command.packageId()) && pack.hash().equals(command.payloadHash())
            && pack.captureHigh() == command.captureHigh() && pack.auditHigh() == command.auditHigh());
        var body = read(pack.hash());
        require(SubmissionJson.uuid(body, "recoveryPackageId").equals(pack.id())
            && SubmissionJson.number(body, "workRound") == origin.round()
            && SubmissionJson.number(body, "captureHighWatermark") == command.captureHigh());
        var validated = evidence.validate(recall, assignments.get(origin.assignment()), body);
        require(validated.auditHigh() == command.auditHigh() && validated.stock().scans().size() == pack.count()
            && validated.stock().weight() == pack.grams());
        var result = stops.acknowledge(recall.id(), command.acknowledgement(), command.epoch());
        require(result.path("actualCount").asText().equals(Long.toString(pack.count()))
            && result.path("actualWeightGrams").asText().equals(Long.toString(pack.grams())));
        return result;
    }

    private JsonNode read(String hash) {
        try { return SubmissionJson.parse(files.read(hash)); }
        catch (java.io.UncheckedIOException damaged) {
            throw new BusinessException(409, "HANDOVER_STOP_FILE_UNAVAILABLE", "原恢复包不可读取或摘要不符，请保留资料");
        }
    }

    private void require(boolean valid) {
        if (!valid) throw new BusinessException(409, "HANDOVER_STOP_CONTEXT_CHANGED", "恢复包与交接原来源不一致");
    }
}
