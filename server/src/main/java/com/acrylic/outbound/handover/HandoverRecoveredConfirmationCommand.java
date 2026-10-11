package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.recovery.RecoveryRecipientSession;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Explicit physical confirmation of the original bytes under the independently scoped receiving identity. */
record HandoverRecoveredConfirmationCommand(JsonNode body,byte[] bytes,String sha256) {
    static final int MAX_BYTES=65536;
    HandoverRecoveredConfirmationCommand { body=body.deepCopy(); bytes=bytes.clone(); }
    @Override public JsonNode body() { return body.deepCopy(); }
    @Override public byte[] bytes() { return bytes.clone(); }
    static HandoverRecoveredConfirmationCommand parse(byte[] raw) {
        try {
            if(raw==null || raw.length==0 || raw.length>MAX_BYTES) throw invalid();
            var body=com.acrylic.outbound.submissions.SubmissionJson.parse(raw); var fields=new HashSet<String>();
            body.fieldNames().forEachRemaining(fields::add);
            if(!body.isObject() || !fields.equals(Set.of("commandVersion","commandType","commandId","caseId","qualificationId",
                "transportRecoveryEpoch","sourceRecoveryEpoch","expectedCaseVersion","qualificationSha256","recipientSessionSha256",
                "manifestSha256","preparedTicketSha256","payloadSha256","checklistSha256","physicalConfirmed"))) throw invalid();
            format(body);
            byte[] canonical=canonical(body).getBytes(StandardCharsets.UTF_8);
            return new HandoverRecoveredConfirmationCommand(body,canonical,HandoverCommand.sha(canonical));
        } catch(RuntimeException malformed) { throw invalid(); }
    }
    private static void format(JsonNode body) {
        if(!body.path("commandVersion").isInt() || body.path("commandVersion").intValue()!=1
            || !"RECOVERED_TEMP_HANDOVER_CONFIRM".equals(text(body,"commandType"))
            || !body.path("physicalConfirmed").isBoolean() || !body.path("physicalConfirmed").booleanValue()) throw invalid();
        for(String field:List.of("commandId","caseId","qualificationId")) uuid(body,field);
        for(String field:List.of("transportRecoveryEpoch","sourceRecoveryEpoch","expectedCaseVersion"))
            if(!text(body,field).matches("[1-9][0-9]{0,17}")) throw invalid();
        for(String field:List.of("qualificationSha256","recipientSessionSha256","manifestSha256","preparedTicketSha256","payloadSha256","checklistSha256"))
            if(!text(body,field).matches("[a-f0-9]{64}")) throw invalid();
    }
    void bind(RecoveryRecipientSession session,HandoverRecoveredTemporaryDownload.Result document) {
        var q=session.qualification().facts(); var manifest=com.acrylic.outbound.submissions.SubmissionJson.parse(document.manifest());
        require(body.path("caseId").equals(q.path("caseId")) && body.path("qualificationId").equals(q.path("qualificationId"))
            && text(body,"expectedCaseVersion").equals("4") && body.path("transportRecoveryEpoch").equals(q.path("control").path("transportRecoveryEpoch"))
            && body.path("sourceRecoveryEpoch").equals(q.path("source").path("sourceRecoveryEpoch"))
            && !body.path("sourceRecoveryEpoch").equals(body.path("transportRecoveryEpoch")));
        require(text(body,"qualificationSha256").equals(session.qualification().sha256()) && text(body,"recipientSessionSha256").equals(session.sha256())
            && text(body,"manifestSha256").equals(HandoverCommand.sha(document.manifest()))
            && text(body,"payloadSha256").equals(HandoverCommand.sha(document.recoveryPackage()))
            && body.path("preparedTicketSha256").equals(manifest.path("preparedTicketSha256"))
            && text(body,"checklistSha256").equals(sha(manifest.path("checklist"))));
    }
    private static void require(boolean valid) {
        if(!valid) throw new BusinessException(409,"RECOVERED_CONFIRMATION_CHANGED","原恢复资料、接收资格或会话已变化，请保留原意图并核对");
    }
    private static BusinessException invalid() {
        return new BusinessException(400,"INVALID_RECOVERED_CONFIRMATION","请明确确认原恢复资料与实物，并保留原请求");
    }
}
