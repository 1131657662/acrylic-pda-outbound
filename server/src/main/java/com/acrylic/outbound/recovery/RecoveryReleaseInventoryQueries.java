package com.acrylic.outbound.recovery;

import java.util.Map;
import java.util.Set;

/** Fixed warehouse joins only; global numbering and job facts are exposed as digests where needed. */
final class RecoveryReleaseInventoryQueries {
    private static final String SCOPES="SELECT id FROM submission_scope WHERE warehouse_id=?";
    private static final String ORDERS="SELECT id FROM outbound_order WHERE warehouse_id=?";
    private static final String DEVICES="SELECT id FROM device WHERE warehouse_id=?";
    private static final String CASES="SELECT id FROM handover_case WHERE warehouse_id=?";
    private static final String BALES="SELECT id FROM bale WHERE warehouse_id=?";
    private static final String SUBMISSIONS="SELECT id FROM submission WHERE scope_id IN ("+SCOPES+")";
    private static final String DOCUMENTS="SELECT id FROM correction_document WHERE warehouse_id=?";
    private static final Set<String> WAREHOUSE=Set.of("device","temporary_authorization","order_assignment",
        "submission_scope","source_evidence_set","handover_case","handover_fault_fence","handover_formal_fault_fence",
        "temporary_source_continuation","correction_document","bale","inventory_discrepancy","inventory_reconciliation",
        "pda_audit_event","local_draft_evidence","outbound_order","order_sync_outbox","sync_stream","barcode_sync_stream");
    private static final Map<String,String> FILTERS=Map.ofEntries(
        Map.entry("pda_session","device_id IN ("+DEVICES+")"),
        Map.entry("offline_authorization","assignment_id IN (SELECT id FROM order_assignment WHERE warehouse_id=?)"),
        Map.entry("submission","scope_id IN ("+SCOPES+")"),
        Map.entry("submission_payload","submission_id IN ("+SUBMISSIONS+")"),
        Map.entry("submission_archive","submission_id IN ("+SUBMISSIONS+")"),
        Map.entry("temporary_work_origin","creator_device_id IN ("+DEVICES+")"),
        Map.entry("temporary_source_mapping","warehouse_id=?"),
        Map.entry("source_evidence_verification","scope_id IN ("+SCOPES+")"),
        Map.entry("handover_evidence","case_id IN ("+CASES+")"),
        Map.entry("handover_command_receipt","case_id IN ("+CASES+")"),
        Map.entry("handover_continuation","case_id IN ("+CASES+")"),
        Map.entry("temporary_handover_activation_ticket","case_id IN ("+CASES+")"),
        Map.entry("temporary_handover_activation","ticket_id IN (SELECT id FROM temporary_handover_activation_ticket WHERE scope_id IN ("+SCOPES+"))"),
        Map.entry("temporary_source_continuation_activation","scope_id IN ("+SCOPES+")"),
        Map.entry("correction_line","document_id IN ("+DOCUMENTS+")"),
        Map.entry("correction_effect","order_id IN ("+ORDERS+")"),
        Map.entry("correction_ledger","bale_id IN ("+BALES+")"),
        Map.entry("correction_command_receipt","document_id IN ("+DOCUMENTS+")"),
        Map.entry("correction_occupancy_restoration","withdrawal_document_id IN ("+DOCUMENTS+")"),
        Map.entry("cycle_reopening","original_dispatch_id IN (SELECT id FROM dispatch_record WHERE order_id IN ("+ORDERS+"))"),
        Map.entry("bale_commitment","bale_id IN ("+BALES+")"),
        Map.entry("inventory_position","bale_id IN ("+BALES+")"),
        Map.entry("inventory_ledger","bale_id IN ("+BALES+")"),
        Map.entry("dispatch_record","order_id IN ("+ORDERS+")"),
        Map.entry("order_result_version","order_id IN ("+ORDERS+")"),
        Map.entry("order_current_result","order_id IN ("+ORDERS+")"),
        Map.entry("pda_audit_checkpoint","origin_device_id IN ("+DEVICES+")"),
        Map.entry("pda_audit_archive","origin_device_id IN ("+DEVICES+")"),
        Map.entry("local_draft_evidence_complete","evidence_id IN (SELECT evidence_id FROM local_draft_evidence WHERE warehouse_id=?)"));
    private static final Set<String> GLOBAL=Set.of("order_number_counter","background_job","recovery_package");

    static String filter(String table) {
        if(GLOBAL.contains(table)) return "";
        if(WAREHOUSE.contains(table)) return " WHERE warehouse_id=?";
        String predicate=FILTERS.get(table);
        return predicate==null?RecoveryReleaseAdditionalQueries.filter(table):" WHERE "+predicate;
    }

    static boolean digestOnly(String table) {
        return Set.of("background_job","recovery_package").contains(table) || RecoveryReleaseAdditionalQueries.digestOnly(table);
    }
    static boolean confidential(String table,String column) {
        return table.equals("pda_session") && Set.of("access_hash","refresh_hash").contains(column)
            || Set.of("temporary_authorization","offline_authorization").contains(table) && column.equals("signed_token")
            || RecoveryReleaseAdditionalQueries.confidential(table,column);
    }
    private RecoveryReleaseInventoryQueries() { }
}
