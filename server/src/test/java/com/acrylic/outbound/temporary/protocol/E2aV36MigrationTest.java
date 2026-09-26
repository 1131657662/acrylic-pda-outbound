package com.acrylic.outbound.temporary.protocol;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class E2aV36MigrationTest {
    private final String ddl=resource();

    @Test void stagedRecordsHaveSourceWideRecordAndObjectUniqueness() {
        assertThat(ddl).contains("PRIMARY KEY(scope_id,submission_id,work_round,item_kind,original_record_id)")
            .contains("UNIQUE KEY uq_evidence_stage_object(scope_id,submission_id,work_round,original_object_id)")
            .contains("UNIQUE KEY uq_evidence_stage_page_ordinal(page_object_id,item_ordinal)")
            .contains("KEY ix_evidence_stage_manifest(scope_id,submission_id,work_round,open_original_key,page_no,item_ordinal)");
    }

    @Test void referencesBothV34ObjectsAndV35OriginalOpen() {
        assertThat(ddl).contains("UNIQUE KEY uq_evidence_intake_stage_context(principal_id,route_scope,original_key,scope_id,submission_id,work_round,operation_kind)")
            .contains("FOREIGN KEY(principal_id,route_scope,open_original_key,scope_id,submission_id,work_round,open_operation_kind)")
            .contains("REFERENCES source_evidence_intake_receipt(principal_id,route_scope,original_key,scope_id,submission_id,work_round,operation_kind)")
            .contains("CHECK(open_operation_kind='OPEN')")
            .contains("FOREIGN KEY(page_object_id,scope_id,submission_id,work_round)")
            .contains("FOREIGN KEY(raw_object_id,scope_id,submission_id,work_round)")
            .contains("BEFORE UPDATE ON source_evidence_page_item_stage")
            .contains("BEFORE DELETE ON source_evidence_page_item_stage");
    }

    private static String resource() {
        String name="/db/migration/V36__source_evidence_page_item_stage.sql";
        try(var input=E2aV36MigrationTest.class.getResourceAsStream(name)) {
            if(input==null)throw new AssertionError("missing "+name);
            return new String(input.readAllBytes(),StandardCharsets.UTF_8);
        } catch(IOException failure) { throw new UncheckedIOException(failure); }
    }
}
