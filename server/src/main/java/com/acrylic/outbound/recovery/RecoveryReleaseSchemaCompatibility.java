package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.require;
import static com.acrylic.outbound.recovery.RecoveryReleaseRows.*;

/** Exact additive migration effects; the independently pinned historical snapshot stays unchanged. */
final class RecoveryReleaseSchemaCompatibility {
    private static final Map<String,String> EMPTY_ADDITIONS=Map.of(
        "quarantine_origin","case_id disposition_id bale_id original_receipt_id pc_disposition_id pc_evidence_sha256 formal_disposition_id formal_evidence_sha256",
        "quarantine_release","case_id approval_receipt_id",
        "reconciliation_attachment_use","case_id attachment_id",
        "reconciliation_evidence","case_id submitted_receipt_id",
        "reconciliation_posting","case_id ledger_id approval_receipt_id",
        "reconciliation_request","case_id order_id opened_receipt_id",
        "returned_stock_release","case_id original_dispatch_id cycle_reopening_id approval_receipt_id");
    private static final String CAPTURE_COLUMNS="id submission_id order_id origin_device_id device_capture_seq order_scan_seq record_sha256";

    static JsonNode original(String name,JsonNode tables,JsonNode actual) {
        var before=tables.path(name);
        if(before.isMissingNode() && EMPTY_ADDITIONS.containsKey(name)) {
            require(actual.path("columns").equals(columns(EMPTY_ADDITIONS.get(name))),"RECOVERY_SCOPE_COLUMNS_CHANGED_"+name);
            require(decode(actual).isEmpty(),"RECOVERY_SCOPE_UNEXPECTED_ADDITION_"+name);
            return actual.deepCopy();
        }
        if(name.equals("submitted_capture") && !before.path("columns").equals(actual.path("columns"))
                && before.path("columns").equals(columns(CAPTURE_COLUMNS)))
            return captures(tables,actual);
        return before;
    }

    private static JsonNode captures(JsonNode tables,JsonNode actual) {
        var expectedColumns=columns(CAPTURE_COLUMNS+" operation_revision");
        require(actual.path("columns").equals(expectedColumns),"RECOVERY_SCOPE_COLUMNS_CHANGED_submitted_capture");
        var rows=decode(tables.path("submitted_capture"));
        for(var row:rows) {
            var submission=find(tables.path("submission"),row.get("submission_id"));
            require(submission.containsKey("assignment_id"),"RECOVERY_SCOPE_CAPTURE_ASSIGNMENT");
            String assignment=submission.get("assignment_id");
            String revision=assignment==null?encoded("1"):find(tables.path("order_assignment"),assignment).get("revision");
            require(revision!=null,"RECOVERY_SCOPE_CAPTURE_REVISION"); row.put("operation_revision",revision);
        }
        var expected=SubmissionJson.object(); expected.set("columns",expectedColumns); expected.set("rows",encode(expectedColumns,rows));
        require(decode(actual).equals(decode(expected)),"RECOVERY_SCOPE_ORIGINAL_CHANGED_submitted_capture");
        return expected;
    }

    private static Map<String,String> find(JsonNode table,String id) {
        require(id!=null,"RECOVERY_SCOPE_CAPTURE_REFERENCE");
        var rows=decode(table).stream().filter(row->id.equals(row.get("id"))).toList();
        require(rows.size()==1,"RECOVERY_SCOPE_CAPTURE_REFERENCE"); return rows.getFirst();
    }

    private static JsonNode columns(String names) {
        var columns=SubmissionJson.object().putArray("columns");
        for(String name:names.split(" ")) columns.add(name);
        return columns;
    }
    private RecoveryReleaseSchemaCompatibility() { }
}
