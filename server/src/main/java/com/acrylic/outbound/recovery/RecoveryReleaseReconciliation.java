package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Original-fact reconciliation, still requiring physical review and the external release protocol. */
public final class RecoveryReleaseReconciliation {
    private final JsonNode facts;
    private final String sha256;
    private RecoveryReleaseReconciliation(JsonNode facts) {
        this.facts=facts.deepCopy(); sha256=RecoveryControlFormat.sha256(RecoveryTemporaryEvidenceMaterial.encode(facts));
    }
    public static Prepared prepare(RecoveryReleaseInventory inventory,Request request) {
        require(!TransactionSynchronizationManager.isActualTransactionActive(),"RECOVERY_SCOPE_PREFLIGHT_OUTSIDE_TRANSACTION");
        var numbers=RecoveryReleaseNumberHistory.verify(request.authority(),inventory,request.history());
        return new Prepared(inventory,numbers,readOriginal(request),request);
    }
    public static RecoveryReleaseReconciliation inspect(DataSource database,RecoveryReleaseInventory inventory,Prepared prepared) {
        require(database!=null && inventory!=null && prepared!=null && TransactionSynchronizationManager.isActualTransactionActive()
            && TransactionSynchronizationManager.hasResource(database),"RECOVERY_SCOPE_TRANSACTION_REQUIRED");
        prepared.inventory.requireSame(inventory); var request=prepared.request; var numbers=prepared.numbers;
        prepared.requireOriginals();
        var original=readOriginal(request); require(original.equals(prepared.original),"RECOVERY_SCOPE_ORIGINAL_CHANGED");
        var jdbc=new JdbcTemplate(database); var actual=new RecoveryReleaseDatabaseRows(jdbc).read();
        var observedAt=jdbc.queryForObject("SELECT UTC_TIMESTAMP(3)",java.sql.Timestamp.class).toInstant();
        var receipts=new RecoveryReleaseFenceReceipts(actual,request.targetKeys(),inventory.facts());
        RecoveryReleaseScopeSemantics.verify(original.path("tables"),actual,receipts,new RecoveryReleaseScopeSemantics.Context(inventory.facts(),observedAt));
        var result=SubmissionJson.object().put("formatVersion",1).put("inventorySha256",inventory.sha256()).put("numberHistorySha256",numbers.sha256())
            .put("originalSnapshotSha256",request.history().end().independentSha256()).put("controlRecordSha256",text(inventory.facts().path("control"),"controlRecordSha256"))
            .put("warehouseId",text(inventory.facts(),"warehouseId")).put("scopeId",text(inventory.facts(),"scopeId"))
            .put("observedRowsSha256",RecoveryControlFormat.sha256(RecoveryTemporaryEvidenceMaterial.encode(actual)))
            .put("originalFactsPreserved",true).put("unknownScopesRemainIsolated",true).put("physicalState","UNCHECKED");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void beforeCommit(boolean readOnly) {
                require(actual.equals(new RecoveryReleaseDatabaseRows(jdbc).read()) && original.equals(readOriginal(request)),"RECOVERY_SCOPE_CHANGED");
                prepared.requireOriginals();
            }
        });
        return new RecoveryReleaseReconciliation(result);
    }
    private static JsonNode readOriginal(Request request) {
        var schema=request.history().archive().permissions().getFirst().expected().database().schema();
        return RecoveryReleaseNumberSnapshot.read(request.authority(),request.history().end(),schema);
    }
    public record Request(RecoveryClosedAuthority authority,RecoveryReleaseNumberHistory.Request history,RecoveryReceiptKeys targetKeys) { }
    public static final class Prepared {
        private final RecoveryReleaseInventory inventory;
        private final RecoveryReleaseNumberHistory numbers;
        private final JsonNode original;
        private final Request request;
        private Prepared(RecoveryReleaseInventory inventory,RecoveryReleaseNumberHistory numbers,JsonNode original,Request request) {
            this.inventory=inventory; this.numbers=numbers; this.original=original.deepCopy(); this.request=request;
        }
        void requireOriginals() {
            var history=request.history().archive().permissions().getFirst();
            var proof=RecoveryPermissionContinuity.verifyControlled(history); var expected=numbers.facts().path("history");
            require(text(expected,"beginSha256").equals(proof.beginSha256()) && text(expected,"endSha256").equals(proof.endSha256())
                && text(expected,"binlogSha256").equals(proof.binlogSha256()) && proof.historicalAuthorityEstablished(),"RECOVERY_SCOPE_HISTORY_CHANGED");
            RecoveryReleaseNumberSnapshot.read(request.authority(),request.history().begin(),history.expected().database().schema());
        }
        JsonNode original() { return original.deepCopy(); }
        Request request() { return request; }
        String numberHistorySha256() { return numbers.sha256(); }
        void requireApprovalContext(RecoveryReleaseInventory current) {
            var expected=inventory.facts(); var actual=current.facts();
            for(String field:List.of("control","source","sourceManifestSha256","sourceRowsSha256","sourceSetSha256",
                    "payloadSha256","warehouseId","scopeId","submissionId","deviceId","faultReceipt","globalOrderNumbers"))
                require(expected.path(field).equals(actual.path(field)),"RECOVERY_SCOPE_APPROVAL_CONTEXT_CHANGED");
        }
    }
    public JsonNode facts() { return facts.deepCopy(); }
    public String sha256() { return sha256; }
    public List<String> gaps() { return List.of("PHYSICAL_REVIEW_REQUIRED","SUPERVISOR_OPERATIONS_RELEASE_REQUIRED","EXTERNAL_PREPARE_DATABASE_COMMIT_REQUIRED"); }
    public boolean releaseAllowed() { return false; }
    public boolean ordinaryWritesAllowed() { return false; }
    public boolean localDeletionAllowed() { return false; }
}
