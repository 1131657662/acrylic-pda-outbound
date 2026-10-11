package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.require;
import static com.acrylic.outbound.recovery.RecoveryReleaseRows.*;

/** Exact PREPARE case columns; every other original column is left for the full preexisting comparison. */
final class RecoveryPreparedCaseEffect {
    static Map<String,String> before(Map<String,String> row,JsonNode receipt,JsonNode ticket) {
        var context=receipt.path("caseContext");
        uuid(row,"id",context.path("caseId").asText()); eq(row,"status","TARGET_PREPARED"); eq(row,"row_version","4");
        uuid(row,"target_device_id",ticket.path("targetDeviceId").asText()); uuid(row,"target_user_id",ticket.path("targetUserId").asText());
        require(Objects.equals(row.get("prepared_ticket_sha256"),hashBytes(SubmissionJson.sha(ticket))),"RECOVERY_PREPARATION_TICKET_CHANGED");
        for(String column:List.of("prepared_at","updated_at"))
            require(time(row,column).equals(Instant.parse(receipt.path("recordedAt").asText())),"RECOVERY_PREPARATION_CASE_TIME");
        for(String field:List.of("targetDeviceId","targetUserId","preparedTicketSha256","activationSha256"))
            require(context.path(field).asText().equals("null"),"RECOVERY_PREPARATION_PRIOR_TARGET");
        var before=new LinkedHashMap<>(row); before.put("status",encoded("APPROVED")); before.put("row_version",encoded("3"));
        for(String column:List.of("target_device_id","target_user_id","prepared_ticket_sha256","prepared_at")) before.put(column,null);
        return before;
    }
    private RecoveryPreparedCaseEffect() { }
}
