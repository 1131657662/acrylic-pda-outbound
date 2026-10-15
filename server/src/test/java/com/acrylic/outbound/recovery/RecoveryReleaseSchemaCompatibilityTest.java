package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static com.acrylic.outbound.recovery.RecoveryReleaseRows.encoded;
import static org.assertj.core.api.Assertions.*;

class RecoveryReleaseSchemaCompatibilityTest {
    private static final String CAPTURE="id submission_id order_id origin_device_id device_capture_seq order_scan_seq record_sha256";

    @ParameterizedTest
    @CsvSource(delimiter='|',value={
        "quarantine_origin|case_id disposition_id bale_id original_receipt_id pc_disposition_id pc_evidence_sha256 formal_disposition_id formal_evidence_sha256",
        "quarantine_release|case_id approval_receipt_id",
        "reconciliation_attachment_use|case_id attachment_id",
        "reconciliation_evidence|case_id submitted_receipt_id",
        "reconciliation_posting|case_id ledger_id approval_receipt_id",
        "reconciliation_request|case_id order_id opened_receipt_id",
        "returned_stock_release|case_id original_dispatch_id cycle_reopening_id approval_receipt_id"})
    void acceptsOnlyEmptyKnownAdditions(String name,String columns) {
        var original=SubmissionJson.object(); var actual=table(columns);
        assertThat(RecoveryReleaseSchemaCompatibility.original(name,original,actual)).isEqualTo(actual);
        assertThat(original).isEmpty();
        var populated=actual.deepCopy(); var row=populated.withArray("rows").addArray();
        for(String ignored:columns.split(" ")) row.addNull();
        assertThatThrownBy(()->RecoveryReleaseSchemaCompatibility.original(name,original,populated))
            .hasMessage("RECOVERY_SCOPE_UNEXPECTED_ADDITION_"+name);
        var drifted=actual.deepCopy(); drifted.withArray("columns").add("unexpected");
        assertThatThrownBy(()->RecoveryReleaseSchemaCompatibility.original(name,original,drifted))
            .hasMessage("RECOVERY_SCOPE_COLUMNS_CHANGED_"+name);
    }

    @Test void preservesCurrentSchemaWithoutInventingAnOriginal() {
        var original=SubmissionJson.object(); var table=table("id value"); original.set("pc_work",table);
        assertThat(RecoveryReleaseSchemaCompatibility.original("pc_work",original,table)).isEqualTo(table);
        assertThat(RecoveryReleaseSchemaCompatibility.original("unknown",original,table).isMissingNode()).isTrue();
        original.putNull("quarantine_release");
        assertThat(RecoveryReleaseSchemaCompatibility.original("quarantine_release",original,table).isNull()).isTrue();
    }

    @Test void derivesUnassignedTemporaryCaptureRevisionWithoutChangingPinnedSnapshot() {
        var original=legacy(null); var unchanged=original.deepCopy(); var current=capture("1");
        assertThat(RecoveryReleaseSchemaCompatibility.original("submitted_capture",original,current)).isEqualTo(current);
        assertThat(original).isEqualTo(unchanged);
    }

    @Test void derivesAssignedRevisionFromOriginalAssignment() {
        var original=legacy("assignment");
        original.set("order_assignment",table("id revision",new String[]{"assignment","7"}));
        var current=capture("7");
        assertThat(RecoveryReleaseSchemaCompatibility.original("submitted_capture",original,current)).isEqualTo(current);
    }

    @Test void rejectsChangedAssignedRevision() {
        var original=legacy("assignment");
        original.set("order_assignment",table("id revision",new String[]{"assignment","7"}));
        assertThatThrownBy(()->RecoveryReleaseSchemaCompatibility.original("submitted_capture",original,capture("1")))
            .hasMessage("RECOVERY_SCOPE_ORIGINAL_CHANGED_submitted_capture");
    }

    @Test void rejectsChangedTemporaryDefault() {
        assertThatThrownBy(()->RecoveryReleaseSchemaCompatibility.original("submitted_capture",legacy(null),capture("2")))
            .hasMessage("RECOVERY_SCOPE_ORIGINAL_CHANGED_submitted_capture");
    }

    @Test void rejectsMissingOriginalAssignment() {
        assertThatThrownBy(()->RecoveryReleaseSchemaCompatibility.original("submitted_capture",legacy("missing"),capture("1")))
            .hasMessage("RECOVERY_SCOPE_CAPTURE_REFERENCE");
    }

    @Test void rejectsDuplicateOriginalSubmission() {
        var original=legacy(null); var rows=(ArrayNode)original.path("submission").path("rows"); rows.add(rows.get(0).deepCopy());
        assertThatThrownBy(()->RecoveryReleaseSchemaCompatibility.original("submitted_capture",original,capture("1")))
            .hasMessage("RECOVERY_SCOPE_CAPTURE_REFERENCE");
    }

    @Test void rejectsUnknownCaptureColumn() {
        var current=capture("1"); current.withArray("columns").add("other");
        assertThatThrownBy(()->RecoveryReleaseSchemaCompatibility.original("submitted_capture",legacy(null),current))
            .hasMessage("RECOVERY_SCOPE_COLUMNS_CHANGED_submitted_capture");
    }

    @Test void preservesLegacySameSchema() {
        var original=legacy(null); var legacy=original.path("submitted_capture");
        assertThat(RecoveryReleaseSchemaCompatibility.original("submitted_capture",original,legacy)).isEqualTo(legacy);
    }

    @Test void rejectsChangedOriginalCaptureBytes() {
        var current=capture("1"); ((ArrayNode)current.path("rows").get(0)).set(6,TextNode.valueOf(encoded("tampered")));
        assertThatThrownBy(()->RecoveryReleaseSchemaCompatibility.original("submitted_capture",legacy(null),current))
            .hasMessage("RECOVERY_SCOPE_ORIGINAL_CHANGED_submitted_capture");
    }

    private static ObjectNode legacy(String assignment) {
        var tables=SubmissionJson.object();
        tables.set("submitted_capture",table(CAPTURE,new String[]{"c","s","o","d","1","2","hash"}));
        tables.set("submission",table("id assignment_id",new String[]{"s",assignment}));
        tables.set("order_assignment",table("id revision")); return tables;
    }

    private static ObjectNode capture(String revision) {
        return table(CAPTURE+" operation_revision",new String[]{"c","s","o","d","1","2","hash",revision});
    }

    private static ObjectNode table(String names,String[]... values) {
        var table=SubmissionJson.object(); var columns=table.putArray("columns"); var rows=table.putArray("rows");
        for(String name:names.split(" ")) columns.add(name);
        for(var value:values) { var row=rows.addArray(); for(String cell:value) if(cell==null) row.addNull(); else row.add(encoded(cell)); }
        return table;
    }
}
