package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Pinned original history must cover the actual loss window; coverage alone never releases business. */
public final class RecoveryReleaseHistoryCoverage {
    private final JsonNode facts;
    private final String sha256;

    private RecoveryReleaseHistoryCoverage(JsonNode facts) {
        this.facts=facts.deepCopy(); sha256=RecoveryControlFormat.sha256(RecoveryTemporaryEvidenceMaterial.encode(facts));
    }

    public static RecoveryReleaseHistoryCoverage verify(RecoveryClosedAuthority authority,RecoveryReleaseInventory inventory,
            RecoveryReceivedTemporaryArchiveConsumer.Request archive,RecoveryReceiptKeys keys) {
        require(authority!=null && inventory!=null && archive!=null && keys!=null,"RECOVERY_RELEASE_HISTORY_REQUIRED");
        var control=authority.read(); var observed=inventory.facts(); var source=authority.sourceIdentity();
        var original=new RecoveryReceivedTemporaryArchiveInputs(authority,keys).read(archive);
        require(archive.permissions().size()==1,"RECOVERY_RELEASE_HISTORY_SINGLE_WINDOW_REQUIRED");
        var history=archive.permissions().getFirst();
        require(text(observed,"sourceRowsSha256").equals(archive.rows().independentSha256())
            && text(observed,"payloadSha256").equals(SubmissionJson.sha(SubmissionJson.parse(original.source().original("payload")))),
            "RECOVERY_RELEASE_HISTORY_SOURCE");
        binding(control,observed,authority.sourcePinSha256()); identity(history,source);
        var proof=RecoveryPermissionContinuity.verifyControlled(history);
        require(text(proof.effective(),"warehouseId").equals(source.warehouseId().toString()),"RECOVERY_RELEASE_HISTORY_WAREHOUSE");
        requireCoverage(control,proof);
        var result=facts(control,observed,inventory.sha256(),proof);
        require(control.equals(authority.read()),"RECOVERY_RELEASE_HISTORY_CONTROL_CHANGED");
        return new RecoveryReleaseHistoryCoverage(result);
    }

    static void requireCoverage(RecoveryControlSnapshot control,RecoveryPermissionContinuity.Proof proof) {
        require(proof.historicalAuthorityEstablished() && !control.possibleLossFrom().isBefore(proof.from())
            && !control.possibleLossThrough().isAfter(proof.through()),"RECOVERY_RELEASE_HISTORY_GAP");
    }

    private static void binding(RecoveryControlSnapshot control,JsonNode inventory,String sourcePin) {
        var observed=inventory.path("control");
        require(text(observed,"installationId").equals(control.installationId().toString())
            && text(observed,"controlRevision").equals(control.revision())
            && text(observed,"controlRecordSha256").equals(control.recordSha256())
            && text(observed,"epochId").equals(control.epochId().toString()),"RECOVERY_RELEASE_HISTORY_CONTROL");
        require(text(inventory,"sourceManifestSha256").equals(sourcePin)
            && control.affectedWarehouseIds().contains(text(inventory,"warehouseId")),"RECOVERY_RELEASE_HISTORY_SOURCE");
    }

    private static void identity(RecoveryPermissionContinuity.Request request,RecoverySourceIdentity source) {
        var expected=request.expected();
        require(expected.operatorId().equals(source.operatorId()) && expected.deviceId().equals(source.deviceId())
            && expected.database().sourceRecoveryEpoch().equals(source.sourceRecoveryEpoch()),"RECOVERY_RELEASE_HISTORY_IDENTITY");
    }

    private static JsonNode facts(RecoveryControlSnapshot control,JsonNode inventory,String inventoryHash,
            RecoveryPermissionContinuity.Proof proof) {
        var database=proof.identity().database();
        var result=SubmissionJson.object().put("formatVersion",1).put("inventorySha256",inventoryHash)
            .put("controlRecordSha256",control.recordSha256()).put("sourceManifestSha256",text(inventory,"sourceManifestSha256"))
            .put("warehouseId",text(inventory,"warehouseId")).put("scopeId",text(inventory,"scopeId"))
            .put("sourceMysqlServerUuid",database.mysqlServerUuid().toString()).put("sourceDatabaseSchema",database.schema())
            .put("sourceRecoveryEpoch",database.sourceRecoveryEpoch()).put("possibleLossFrom",control.possibleLossFrom().toString())
            .put("possibleLossThrough",control.possibleLossThrough().toString()).put("historyFrom",proof.from().toString())
            .put("historyThrough",proof.through().toString()).put("beginSha256",proof.beginSha256()).put("endSha256",proof.endSha256())
            .put("binlogSha256",proof.binlogSha256()).put("committedTransactions",Long.toString(proof.committedTransactions()))
            .put("rowEvents",Long.toString(proof.rowEvents())).put("historicalAuthorityEstablished",proof.historicalAuthorityEstablished());
        result.set("targetControl",inventory.path("control").deepCopy()); return result;
    }

    public JsonNode facts() { return facts.deepCopy(); }
    public String sha256() { return sha256; }
    public boolean releaseAllowed() { return false; }
    public boolean ordinaryWritesAllowed() { return false; }
    public boolean localDeletionAllowed() { return false; }
}
