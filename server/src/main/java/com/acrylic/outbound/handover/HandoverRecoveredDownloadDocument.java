package com.acrylic.outbound.handover;

import com.acrylic.outbound.recovery.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static com.acrylic.outbound.handover.HandoverCommand.corruptUnless;

/** Original signed milestones and payload retain their source epoch in the scoped recovery manifest. */
@Component
final class HandoverRecoveredDownloadDocument {
    private final HandoverStore cases;
    private final HandoverPdaEvidenceStore commands;
    private final HandoverReadReceipt receipts;
    private final HandoverChecklist checklist;
    HandoverRecoveredDownloadDocument(HandoverStore cases,HandoverPdaEvidenceStore commands,HandoverReadReceipt receipts,HandoverChecklist checklist) {
        this.cases=cases; this.commands=commands; this.receipts=receipts; this.checklist=checklist;
    }
    HandoverRecoveredTemporaryDownload.Result read(RecoveryRecipientSession session,RecoveryRecoveredTemporaryArchive archive) {
        var q=session.qualification().facts(); var caseId=uuid(q,"caseId"); var facts=cases.caseFacts(caseId);
        var approved=verified(caseId,"APPROVE"); var prepared=verified(caseId,"PREPARE"); var fault=verified(caseId,"FAULT_FENCE");
        byte[] payload=archive.payload(); var body=parse(payload); var lines=approved.command().path("checklist");
        bind(facts,q,archive,approved,fault);
        checklist.dispositions(body,lines); checklist.compareStored(cases.checklist(caseId),lines,true);
        var ticket=ticket(facts,session.qualification(),prepared);
        corruptUnless(HandoverCommand.sha(prepared.bytes()).equals(session.facts().path("preparedReceiptSha256").asText()));
        var manifest=object().put("caseId",caseId.toString()).put("status","TARGET_PREPARED").put("sourceKind","TEMP")
            .put("packageKind","TEMP_FROZEN_PAYLOAD").put("recoveryPackageSha256",HandoverCommand.sha(payload))
            .put("inputHighWatermark",text(facts,"inputHighWatermark")).put("preparedTicketSha256",text(facts,"preparedTicketSha256"))
            .put("stopKind","FAULT_FENCE").put("recipientSessionSha256",session.sha256());
        manifest.set("preparedTicket",ticket); manifest.set("checklist",lines.deepCopy());
        manifest.set("faultReceipt",parse(fault.bytes())); manifest.set("approvalReceipt",parse(approved.bytes()));
        manifest.set("preparationReceipt",parse(prepared.bytes())); manifest.set("capabilities",HandoverReceiptCodec.capabilities());
        return new HandoverRecoveredTemporaryDownload.Result(HandoverReceiptCodec.bytes(manifest),payload);
    }
    private HandoverReadReceipt.Verified verified(UUID caseId,String kind) {
        var verified=receipts.verify(commands.command(caseId,kind));
        corruptUnless(kind.equals(text(verified.command(),"commandType")) && caseId.toString().equals(text(verified.command(),"caseId"))); return verified;
    }
    private void bind(JsonNode facts,JsonNode q,RecoveryRecoveredTemporaryArchive archive,HandoverReadReceipt.Verified approved,HandoverReadReceipt.Verified fault) {
        corruptUnless(text(facts,"status").equals("TARGET_PREPARED") && text(facts,"sourceKind").equals("TEMP")
            && text(facts,"stopState").equals("FAULT_FENCED") && text(facts,"integrityState").equals("COMPLETE")
            && text(facts,"originalResultState").equals("ABORTED") && text(facts,"physicalState").equals("MATCHED"));
        for(String field:List.of("caseId","scopeId","warehouseId")) corruptUnless(facts.path(field).equals(q.path(field)));
        corruptUnless(text(facts,"targetDeviceId").equals(text(q.path("recipient"),"deviceId"))
            && text(facts,"targetUserId").equals(text(q.path("recipient"),"userId"))
            && text(facts,"submissionId").equals(archive.source().submissionId().toString())
            && text(facts,"inputHighWatermark").equals(Long.toString(archive.source().orderScanHighWatermark())));
        corruptUnless(HandoverCommand.sha(archive.payload()).equals(text(q.path("source"),"payloadSha256"))
            && text(parse(archive.payload()),"sourceRecoveryEpoch").equals(text(q.path("source"),"sourceRecoveryEpoch"))
            && !text(q.path("source"),"sourceRecoveryEpoch").equals(text(q.path("control"),"transportRecoveryEpoch")));
        corruptUnless(text(approved.receipt().path("businessDecision"),"status").equals("APPROVED")
            && approved.receipt().path("actor").path("principalId").equals(facts.path("approvedBy"))
            && text(fault.receipt().path("businessDecision"),"payloadSha256").equals(HandoverCommand.sha(archive.payload())));
        for(String field:List.of("inputHighWatermark","stopState","integrityState","originalResultState","physicalState"))
            corruptUnless(approved.command().path(field).equals(facts.path(field)));
    }
    private JsonNode ticket(JsonNode facts,RecoveryTemporaryQualification qualification,HandoverReadReceipt.Verified prepared) {
        var command=prepared.command(); var decision=prepared.receipt().path("businessDecision");
        corruptUnless(text(decision,"status").equals("TARGET_PREPARED"));
        for(String field:List.of("targetDeviceId","targetUserId")) corruptUnless(command.path(field).equals(facts.path(field)));
        corruptUnless(decision.path("temporaryQualification").equals(qualification.preparationDescriptor()));
        var ticket=object().put("caseId",text(facts,"caseId")).put("targetDeviceId",text(facts,"targetDeviceId"))
            .put("targetUserId",text(facts,"targetUserId")).put("inputHighWatermark",text(facts,"inputHighWatermark"))
            .put("canonicalOrderId",text(facts,"canonicalOrderId")).put("issuedAt",text(prepared.receipt(),"recordedAt"));
        ticket.set("temporaryQualification",qualification.preparationDescriptor());
        corruptUnless(sha(ticket).equals(text(facts,"preparedTicketSha256")) && decision.path("preparedTicketSha256").equals(facts.path("preparedTicketSha256")));
        return ticket;
    }
}
