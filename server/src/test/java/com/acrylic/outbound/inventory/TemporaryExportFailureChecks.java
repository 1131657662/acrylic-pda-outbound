package com.acrylic.outbound.inventory;

import com.acrylic.outbound.exports.*;
import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.*;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** Every fault is confined to the isolated synthetic fixture and restored before the next check. */
final class TemporaryExportFailureChecks {
    private final TemporaryExportFixture f;
    TemporaryExportFailureChecks(TemporaryExportFixture fixture) { f=fixture; }
    void verify() throws Exception {
        crossWarehouse();pending();missingOrCorruptPayload();corruptCommand();forgedSignature();missingDecision();changedPlan();cutoff();frozenReplay();
    }
    private void rejected() throws Exception {
        int before=f.jobs();assertThat(f.http.postError(f.path(),f.command("XLSX"),409).path("code").asText()).startsWith("EXPORT_SOURCE_");
        assertThat(f.jobs()).isEqualTo(before);
    }
    private void crossWarehouse() throws Exception {
        var other=new TemporaryConfirmationFixture(f.f.context,false);var http=new TemporaryCandidateHttp();http.login(other.operator().getUsername());
        String path="/orders/"+f.order+"/exports?warehouseId="+other.source.f.web.identity().warehouse();
        assertThat(http.postError(path,f.command("XLSX"),404).path("code").asText()).isEqualTo("ORDER_NOT_FOUND");
    }
    private void pending() throws Exception {
        f.f.jdbc.update("UPDATE submission SET status='REVIEW_REQUIRED' WHERE id=UUID_TO_BIN(?)",f.f.source.id());
        try { rejected(); } finally { f.f.jdbc.update("UPDATE submission SET status='SUCCEEDED' WHERE id=UUID_TO_BIN(?)",f.f.source.id()); }
    }
    private void missingOrCorruptPayload() throws Exception {
        Path payload=f.output.getParent().resolve("storage/submissions/"+SubmissionJson.sha(f.f.source.body)+".json");
        Path kept=payload.resolveSibling(payload.getFileName()+".fault-backup");byte[] original=Files.readAllBytes(payload);
        Files.move(payload,kept);
        try { rejected(); } finally { Files.move(kept,payload); }
        Files.writeString(payload,"{}");try { rejected(); } finally { Files.write(payload,original); }
    }
    private void corruptCommand() throws Exception {
        String id=f.decision.path("receiptId").asText();var jdbc=f.f.jdbc;
        byte[] bytes=jdbc.queryForObject("SELECT original_response_bytes FROM temporary_command_receipt WHERE id=UUID_TO_BIN(?)",byte[].class,id);
        jdbc.update("UPDATE temporary_command_receipt SET original_response_bytes=? WHERE id=UUID_TO_BIN(?)","{}".getBytes(),id);
        try { rejected(); } finally { jdbc.update("UPDATE temporary_command_receipt SET original_response_bytes=? WHERE id=UUID_TO_BIN(?)",bytes,id); }
    }
    private void changedPlan() throws Exception {
        var jdbc=f.f.jdbc;
        jdbc.update("UPDATE order_line SET planned_count=planned_count+1 WHERE revision_id IN (SELECT id FROM order_revision WHERE order_id=UUID_TO_BIN(?))",f.order);
        try { rejected(); } finally { jdbc.update("UPDATE order_line SET planned_count=planned_count-1 WHERE revision_id IN (SELECT id FROM order_revision WHERE order_id=UUID_TO_BIN(?))",f.order); }
    }
    private void forgedSignature() throws Exception {
        String id=f.decision.path("receiptId").asText();var jdbc=f.f.jdbc;
        var row=jdbc.queryForMap("SELECT original_response_bytes,original_response_sha256,signed_receipt FROM temporary_command_receipt WHERE id=UUID_TO_BIN(?)",id);
        var forged=(com.fasterxml.jackson.databind.node.ObjectNode)SubmissionJson.parse((byte[])row.get("original_response_bytes"));
        forged.put("signedReceipt","forged.signature.value");byte[] bytes=TemporaryConfirmationFixture.bytes(forged);
        String hash=com.acrylic.outbound.temporary.protocol.TemporaryProtocol.originalResponseSha256(bytes);
        jdbc.update("UPDATE temporary_command_receipt SET original_response_bytes=?,original_response_sha256=?,signed_receipt=? WHERE id=UUID_TO_BIN(?)",bytes,hash,"forged.signature.value",id);
        try { rejected(); } finally {
            jdbc.update("UPDATE temporary_command_receipt SET original_response_bytes=?,original_response_sha256=?,signed_receipt=? WHERE id=UUID_TO_BIN(?)",
                row.get("original_response_bytes"),row.get("original_response_sha256"),row.get("signed_receipt"),id);
        }
    }
    private void missingDecision() throws Exception {
        String id=f.decision.path("receiptId").asText();var jdbc=f.f.jdbc;
        var row=jdbc.queryForMap("SELECT * FROM temporary_decision_event WHERE receipt_id=UUID_TO_BIN(?)",id);
        jdbc.update("DELETE FROM temporary_decision_event WHERE receipt_id=UUID_TO_BIN(?)",id);
        try { rejected(); } finally {
            jdbc.update("INSERT INTO temporary_decision_event(id,scope_id,resulting_decision_version,receipt_id,decision_kind,context_bytes,recorded_at) VALUES(?,?,?,?,?,?,?)",
                row.get("id"),row.get("scope_id"),row.get("resulting_decision_version"),row.get("receipt_id"),row.get("decision_kind"),row.get("context_bytes"),row.get("recorded_at"));
        }
    }
    private void cutoff() {
        var scope=new ExportModels.Scope(f.f.operator().id(),UUID.fromString(f.warehouse),UUID.fromString(f.order));
        var cutoff=Instant.parse(f.decision.path("recordedAt").asText()).minusMillis(1);
        assertThatThrownBy(()->f.f.bean(ExportSnapshotReader.class).read(scope,ExportModels.Format.XLSX,cutoff)).isInstanceOf(BusinessException.class);
    }
    private void frozenReplay() throws Exception {
        String key=UUID.randomUUID().toString();var original=f.create("XLSX",key);var snapshot=f.snapshot(original);
        f.f.jdbc.update("UPDATE order_revision SET vehicle_no='后来车号' WHERE order_id=UUID_TO_BIN(?)",f.order);
        try {
            assertThat(f.create("XLSX",key).path("id")).isEqualTo(original.path("id"));
            f.generate(original,"replay-"+f.order+".xlsx");assertThat(f.snapshot(original)).isEqualTo(snapshot);rejected();
            var retry=f.http.post("/exports/"+original.path("id").asText()+"/retry?warehouseId="+f.warehouse,
                    SubmissionJson.object(),UUID.randomUUID().toString());
            assertThat(retry.path("retryOf")).isEqualTo(original.path("id"));
            f.generate(retry,"regenerated-"+f.order+".xlsx");assertThat(f.snapshot(retry)).isEqualTo(snapshot);
        } finally { f.f.jdbc.update("UPDATE order_revision SET vehicle_no=? WHERE order_id=UUID_TO_BIN(?)",snapshot.orders().getFirst().originalHeader().vehicle(),f.order); }
    }
}
