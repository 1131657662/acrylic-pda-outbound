package com.acrylic.outbound.recovery;

import java.util.*;

/** Fixed preparation, baseline, projection and work-child coverage for the same warehouse inventory. */
final class RecoveryReleaseAdditionalQueries {
    private static final String ORDERS="SELECT id FROM outbound_order WHERE warehouse_id=?";
    private static final String SCOPES="SELECT id FROM submission_scope WHERE warehouse_id=?";
    private static final String SUBMISSIONS="SELECT id FROM submission WHERE scope_id IN ("+SCOPES+")";
    private static final String CASES="SELECT id FROM handover_case WHERE warehouse_id=?";
    private static final String TICKETS="SELECT id FROM temporary_handover_activation_ticket WHERE scope_id IN ("+SCOPES+")";
    private static final String PC="SELECT id FROM pc_work WHERE warehouse_id=?";
    private static final String PC_CASES="SELECT id FROM pc_exception_case WHERE work_id IN ("+PC+")";
    private static final String PC_PLANS="SELECT id FROM pc_exception_plan WHERE case_id IN ("+PC_CASES+")";
    private static final String PC_SUBMISSIONS="SELECT id FROM pc_submission WHERE work_id IN ("+PC+")";
    private static final Set<String> GLOBAL=Set.of("operation_event","api_idempotency_request","product",
        "device_pairing","pairing_challenge","pairing_key_guard");
    private static final Set<String> WAREHOUSE=Set.of("barcode_sync_baseline","barcode_sync_event","barcode_sync_pending",
        "barcode_sync_projection","correction_attachment","delivery_mapping","handover_attachment","handover_attachment_use",
        "inventory_import","inventory_snapshot","local_draft_evidence_receipt","order_change_request","order_publication",
        "order_revision","pc_work","policy_version","source_evidence_intake_receipt","source_evidence_item","source_evidence_object",
        "source_evidence_page_item_stage","submission_review_reminder","sync_baseline","sync_order_projection",
        "temporary_command_receipt","temporary_physical_disposition");
    private static final Map<String,String> CHILDREN=Map.ofEntries(
        Map.entry("warehouse","id=?"),
        Map.entry("assignment_preparation","assignment_id IN (SELECT id FROM order_assignment WHERE warehouse_id=?)"),
        Map.entry("barcode_sync_baseline_row","baseline_id IN (SELECT id FROM barcode_sync_baseline WHERE warehouse_id=?)"),
        Map.entry("continuation_bale","continuation_id IN (SELECT id FROM order_continuation WHERE scope_id IN ("+SCOPES+"))"),
        Map.entry("correction_attachment_use","document_id IN (SELECT id FROM correction_document WHERE warehouse_id=?)"),
        Map.entry("device_challenge","device_id IN (SELECT id FROM device WHERE warehouse_id=?)"),
        Map.entry("device_command_receipt","device_id IN (SELECT id FROM device WHERE warehouse_id=?)"),
        Map.entry("handover_checklist","case_id IN ("+CASES+")"),
        Map.entry("inventory_import_row","import_id IN (SELECT id FROM inventory_import WHERE warehouse_id=?)"),
        Map.entry("inventory_snapshot_bale","snapshot_id IN (SELECT id FROM inventory_snapshot WHERE warehouse_id=?)"),
        Map.entry("local_draft_evidence_item","evidence_id IN (SELECT evidence_id FROM local_draft_evidence WHERE warehouse_id=?)"),
        Map.entry("order_command_receipt","order_id IN ("+ORDERS+")"),
        Map.entry("order_continuation","scope_id IN ("+SCOPES+")"),
        Map.entry("order_effective_line","order_id IN ("+ORDERS+")"),
        Map.entry("order_effective_scan","order_id IN ("+ORDERS+")"),
        Map.entry("order_line","revision_id IN (SELECT id FROM order_revision WHERE warehouse_id=?)"),
        Map.entry("order_plan_document_task","publication_id IN (SELECT id FROM order_publication WHERE warehouse_id=?)"),
        Map.entry("order_submission_slot","order_id IN ("+ORDERS+")"),
        Map.entry("pc_command_receipt","order_id IN ("+ORDERS+")"),
        Map.entry("pc_entry","work_id IN ("+PC+")"),
        Map.entry("pc_exception_case","work_id IN ("+PC+")"),
        Map.entry("pc_exception_plan","case_id IN ("+PC_CASES+")"),
        Map.entry("pc_exception_review","plan_id IN ("+PC_PLANS+")"),
        Map.entry("pc_disposition","plan_id IN ("+PC_PLANS+")"),
        Map.entry("pc_retained_entry","plan_id IN ("+PC_PLANS+")"),
        Map.entry("pc_submission","work_id IN ("+PC+")"),
        Map.entry("pc_submission_entry","submission_id IN ("+PC_SUBMISSIONS+")"),
        Map.entry("pc_review","submission_id IN ("+PC_SUBMISSIONS+")"),
        Map.entry("physical_disposition_evidence","order_id IN ("+ORDERS+")"),
        Map.entry("submission_chunk","submission_id IN ("+SUBMISSIONS+")"),
        Map.entry("submission_review","submission_id IN ("+SUBMISSIONS+")"),
        Map.entry("submission_upload","submission_id IN ("+SUBMISSIONS+")"),
        Map.entry("submitted_capture","submission_id IN ("+SUBMISSIONS+")"),
        Map.entry("submitted_event","submission_id IN ("+SUBMISSIONS+")"),
        Map.entry("sync_baseline_order","baseline_id IN (SELECT id FROM sync_baseline WHERE warehouse_id=?)"),
        Map.entry("temporary_decision_event","scope_id IN ("+SCOPES+")"),
        Map.entry("temporary_decision_state","scope_id IN ("+SCOPES+")"),
        Map.entry("temporary_handover_activation_owner","ticket_id IN ("+TICKETS+")"),
        Map.entry("temporary_handover_completion","ticket_id IN ("+TICKETS+")"),
        Map.entry("temporary_handover_confirmation","case_id IN ("+CASES+")"),
        Map.entry("temporary_handover_submission","scope_id IN ("+SCOPES+")"),
        Map.entry("temporary_physical_disposition_history","scope_id IN ("+SCOPES+")"),
        Map.entry("temporary_physical_disposition_item","disposition_id IN (SELECT id FROM temporary_physical_disposition WHERE warehouse_id=?)"),
        Map.entry("temporary_preparation_ack","authorization_id IN (SELECT id FROM temporary_authorization WHERE warehouse_id=?)"),
        Map.entry("temporary_preparation_part","authorization_id IN (SELECT id FROM temporary_authorization WHERE warehouse_id=?)"),
        Map.entry("temporary_source_continuation_bale","scope_id IN ("+SCOPES+")"),
        Map.entry("temporary_source_continuation_closure","scope_id IN ("+SCOPES+")"),
        Map.entry("temporary_source_continuation_submission","scope_id IN ("+SCOPES+")"));

    static List<String> tables() {
        return java.util.stream.Stream.of(GLOBAL,WAREHOUSE,CHILDREN.keySet()).flatMap(Collection::stream).sorted().toList();
    }
    static String filter(String table) {
        if(GLOBAL.contains(table)) return "";
        if(WAREHOUSE.contains(table)) return " WHERE warehouse_id=?";
        String predicate=CHILDREN.get(table);
        RecoveryControlFormat.require(predicate!=null,"RECOVERY_RELEASE_TABLE_UNKNOWN"); return " WHERE "+predicate;
    }
    static boolean digestOnly(String table) { return GLOBAL.contains(table); }
    static boolean confidential(String table,String column) { return table.equals("device_challenge") && column.equals("nonce"); }
    private RecoveryReleaseAdditionalQueries() { }
}
