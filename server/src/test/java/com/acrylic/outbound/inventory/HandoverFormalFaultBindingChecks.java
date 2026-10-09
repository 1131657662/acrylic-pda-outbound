package com.acrylic.outbound.inventory;

import java.nio.file.Files;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** Actual cross-source insertion and destructive updates are rejected with all durable facts retained. */
final class HandoverFormalFaultBindingChecks {
    static void verify(HandoverFormalFaultSource f) throws Exception {
        var jdbc = f.source.jdbc; String formal = f.source.kase.toString();
        var before = f.facts.snapshot("binding-before");
        String temp = jdbc.queryForObject("SELECT BIN_TO_UUID(case_id) FROM handover_fault_fence LIMIT 1", String.class);
        assertThat(temp).isNotNull();
        assertThatThrownBy(() -> jdbc.update("""
            INSERT INTO handover_formal_fault_fence
            SELECT UUID_TO_BIN(?),warehouse_id,scope_id,submission_id,assignment_id,source_device_id,device_version,
              change_request_id,recovery_package_id,payload_sha256,capture_high_watermark,audit_through_seq,input_high_watermark,
              controlled_at,UUID_TO_BIN(?),command_sha256,recorded_by,recorded_at
            FROM handover_formal_fault_fence WHERE case_id=UUID_TO_BIN(?)
            """, temp, UUID.randomUUID().toString(), formal))
            .hasMessageContaining("Formal fault fence requires original archived source and revoked qualification");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM handover_formal_fault_fence WHERE case_id=UUID_TO_BIN(?)", formal))
            .hasMessageContaining("Formal fault fence is retained");
        assertThat(f.facts.snapshot("binding-after")).isEqualTo(before);
        Files.writeString(f.source.directory.resolve("formal-binding.marker"),
            "Actual TEMP-case insertion into FORMAL fence rejected by binding trigger; DELETE rejected; all durable tables unchanged.\n");
    }
}
