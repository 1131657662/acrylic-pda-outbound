package com.acrylic.outbound.recovery;

import java.util.*;

/** Fixed native restoration inventory. SQL identifiers never come from a supplied manifest. */
enum RecoveryTemporaryEvidenceTable {
    OBJECT("source_evidence_object", "id warehouse_id scope_id submission_id work_round kind original_object_id bytes_sha256 byte_count file_key origin_device_id uploaded_by received_at",
        "id warehouse_id scope_id submission_id original_object_id origin_device_id uploaded_by", "work_round byte_count", "received_at", "id"),
    SET("source_evidence_set", "id warehouse_id scope_id submission_id work_round stop_object_id manifest_bytes set_sha256 capture_high_watermark audit_through_seq item_count received_at order_scan_high_watermark",
        "id warehouse_id scope_id submission_id stop_object_id manifest_bytes", "work_round capture_high_watermark audit_through_seq item_count order_scan_high_watermark", "received_at", "id"),
    ITEM("source_evidence_item", "set_id warehouse_id scope_id submission_id work_round item_kind original_record_id object_id original_device_seq record_sha256 bale_id original_cycle capture_id record_status",
        "set_id warehouse_id scope_id submission_id original_record_id object_id bale_id capture_id", "work_round original_device_seq original_cycle", "", "set_id item_kind original_record_id"),
    RESERVATION("bale_commitment", "bale_id order_id submission_id dispatch_cycle state created_at recovery_package_id continuation_id disposition_evidence_id pc_work_id pc_disposition_id source_scope_id source_evidence_set_id source_warehouse_id disposition_id temporary_continuation_id temporary_handover_activation_id",
        "bale_id order_id submission_id recovery_package_id continuation_id disposition_evidence_id pc_work_id pc_disposition_id source_scope_id source_evidence_set_id source_warehouse_id disposition_id temporary_continuation_id temporary_handover_activation_id",
        "dispatch_cycle", "created_at", "bale_id");

    final String table;
    final List<String> columns, primary;
    final Set<String> binary, numbers, times;

    RecoveryTemporaryEvidenceTable(String table, String columns, String binary, String numbers, String times, String primary) {
        this.table=table; this.columns=List.of(columns.split(" ")); this.primary=List.of(primary.split(" "));
        this.binary=words(binary); this.numbers=words(numbers); this.times=words(times);
    }
    private static Set<String> words(String value) { return value.isEmpty()?Set.of():Set.of(value.split(" ")); }
    String select() { return "SELECT "+String.join(",",columns)+" FROM "+table; }
    String primaryFilter() { return primary.stream().map(column -> column+"=?").collect(java.util.stream.Collectors.joining(" AND ")); }
    String insert() { return "INSERT INTO "+table+"("+String.join(",",columns)+") VALUES("+String.join(",",Collections.nCopies(columns.size(),"?"))+")"; }
}
