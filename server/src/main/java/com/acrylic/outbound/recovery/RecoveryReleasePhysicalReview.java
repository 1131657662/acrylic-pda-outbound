package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Signed supervisor physical review; operations approval and the release protocol remain mandatory. */
public final class RecoveryReleasePhysicalReview {
    private final JsonNode facts;
    private final String sha256;
    private RecoveryReleasePhysicalReview(JsonNode facts) {
        this.facts=facts.deepCopy(); sha256=RecoveryControlFormat.sha256(RecoveryTemporaryEvidenceMaterial.encode(facts));
    }
    public static RecoveryReleasePhysicalReview inspect(DataSource database,RecoveryReleaseInventory inventory,
            RecoveryReleaseReconciliation.Prepared prepared) {
        require(database!=null && inventory!=null && prepared!=null && TransactionSynchronizationManager.isActualTransactionActive()
            && TransactionSynchronizationManager.hasResource(database),"RECOVERY_SCOPE_TRANSACTION_REQUIRED");
        prepared.requireApprovalContext(inventory); prepared.requireOriginals();
        var request=prepared.request(); var jdbc=new JdbcTemplate(database);
        var actual=new RecoveryReleaseDatabaseRows(jdbc).read();
        var observedAt=jdbc.queryForObject("SELECT UTC_TIMESTAMP(3)",java.sql.Timestamp.class).toInstant();
        var approval=new RecoveryReleaseApprovalEffects(actual,request.targetKeys(),inventory.facts());
        var before=RecoveryReleaseApprovedRows.before(actual,approval,new RecoveryReleaseApprovedRows.Context(inventory.facts(),observedAt));
        var receipts=new RecoveryReleaseFenceReceipts(before,request.targetKeys(),inventory.facts());
        RecoveryReleaseScopeSemantics.verify(prepared.original().path("tables"),before,receipts,
            new RecoveryReleaseScopeSemantics.Context(inventory.facts(),observedAt));
        original(prepared);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void beforeCommit(boolean readOnly) {
                require(actual.equals(new RecoveryReleaseDatabaseRows(jdbc).read()),"RECOVERY_SCOPE_CHANGED");
                prepared.requireOriginals(); original(prepared);
            }
        });
        return proof(inventory,prepared,approval,actual);
    }
    private static RecoveryReleasePhysicalReview proof(RecoveryReleaseInventory inventory,
            RecoveryReleaseReconciliation.Prepared prepared,RecoveryReleaseApprovalEffects approval,JsonNode actual) {
        var request=prepared.request();
        var facts=SubmissionJson.object().put("formatVersion",1).put("inventorySha256",inventory.sha256())
            .put("numberHistorySha256",prepared.numberHistorySha256()).put("originalSnapshotSha256",request.history().end().independentSha256())
            .put("controlRecordSha256",text(inventory.facts().path("control"),"controlRecordSha256"))
            .put("warehouseId",text(inventory.facts(),"warehouseId")).put("scopeId",text(inventory.facts(),"scopeId"))
            .put("approvalReceiptSha256",java.util.HexFormat.of().formatHex(RecoveryReleaseRows.bytes(approval.row.get("response_sha256"))))
            .put("approvalCommandSha256",text(approval.receipt,"commandSha256"))
            .put("observedRowsSha256",RecoveryControlFormat.sha256(RecoveryTemporaryEvidenceMaterial.encode(actual)))
            .put("physicalState","MATCHED").put("originalFactsPreserved",true).put("unknownScopesRemainIsolated",true);
        return new RecoveryReleasePhysicalReview(facts);
    }
    private static void original(RecoveryReleaseReconciliation.Prepared prepared) {
        var request=prepared.request(); var schema=request.history().archive().permissions().getFirst().expected().database().schema();
        require(prepared.original().equals(RecoveryReleaseNumberSnapshot.read(request.authority(),request.history().end(),schema)),"RECOVERY_SCOPE_ORIGINAL_CHANGED");
    }
    public JsonNode facts() { return facts.deepCopy(); }
    public String sha256() { return sha256; }
    public List<String> gaps() { return List.of("OPERATIONS_RELEASE_REQUIRED","EXTERNAL_PREPARE_DATABASE_COMMIT_REQUIRED"); }
    public boolean releaseAllowed() { return false; }
    public boolean ordinaryWritesAllowed() { return false; }
    public boolean localDeletionAllowed() { return false; }
}
