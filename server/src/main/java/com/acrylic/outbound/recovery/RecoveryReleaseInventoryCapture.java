package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Reads the actual closed database after the handover consumer has locked and verified its source. */
public final class RecoveryReleaseInventoryCapture {
    private final DataSource database;
    private final JdbcTemplate jdbc;

    public RecoveryReleaseInventoryCapture(DataSource database) {
        this.database=java.util.Objects.requireNonNull(database); jdbc=new JdbcTemplate(database);
    }

    public RecoveryReleaseInventory capture(RecoveryRecoveredTemporaryArchive archive,JsonNode faultReceipt) {
        require(TransactionSynchronizationManager.isActualTransactionActive()
            && TransactionSynchronizationManager.hasResource(database),"RECOVERY_RELEASE_TRANSACTION_REQUIRED");
        var control=control(); var facts=SubmissionJson.object().put("formatVersion",1);
        facts.set("control",control); facts.set("source",SubmissionJson.parse(archive.payload()).path("source"));
        var provenance=archive.provenance();
        require(text(control,"controlRecordSha256").equals(provenance.controlRecordSha256()),"RECOVERY_RELEASE_CONTROL_MISMATCH");
        facts.put("sourceManifestSha256",provenance.sourceManifestSha256()).put("sourceRowsSha256",provenance.rowsSha256())
            .put("sourceSetSha256",provenance.setSha256()).put("payloadSha256",provenance.payloadSha256())
            .put("warehouseId",archive.source().warehouseId().toString()).put("scopeId",archive.source().scopeId().toString())
            .put("submissionId",archive.source().submissionId().toString()).put("deviceId",archive.source().deviceId().toString());
        facts.set("faultReceipt",faultReceipt.deepCopy());
        var tables=new RecoveryReleaseInventoryRows(jdbc,archive.source().warehouseId()).read(); facts.set("tables",tables);
        var numbers=RecoveryReleaseNumberInventory.capture(jdbc); facts.set("globalOrderNumbers",numbers);
        TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
            @Override public void beforeCommit(boolean readOnly) {
                require(control.equals(control()) && tables.equals(new RecoveryReleaseInventoryRows(jdbc,archive.source().warehouseId()).read())
                    && numbers.equals(RecoveryReleaseNumberInventory.capture(jdbc)),
                    "RECOVERY_RELEASE_INVENTORY_CHANGED");
            }
        });
        return new RecoveryReleaseInventory(facts,List.of("DEVICE_SCOPE_CLOSURE_REQUIRED","SOURCE_CASE_CLOSURE_REQUIRED",
            "ADJUSTMENT_STOCK_AUDIT_CLOSURE_REQUIRED","GLOBAL_NUMBER_HISTORY_REQUIRED",
            "SUPERVISOR_OPERATIONS_RELEASE_REQUIRED","EXTERNAL_PREPARE_DATABASE_COMMIT_REQUIRED"));
    }

    private JsonNode control() {
        var rows=new RecoveryProjectionRows(jdbc); var state=rows.lockState(); var binding=rows.binding();
        require(binding!=null && state.mode().equals("RECOVERY_PENDING"),"RECOVERY_RELEASE_CLOSED_REQUIRED");
        var actual=rows.database();
        require(actual.serverUuid().equals(binding.serverUuid()) && actual.schema().equals(binding.schema()),"CONTROL_DATABASE_INSTANCE_MISMATCH");
        var head=RecoveryProjectionHistory.validate(rows,binding);
        require(head.numeric()==state.numeric(),"CONTROL_DATABASE_PROJECTION_MISMATCH");
        return SubmissionJson.object().put("installationId",binding.installation().toString())
            .put("mysqlServerUuid",actual.serverUuid()).put("databaseSchema",actual.schema())
            .put("controlRevision",Long.toString(head.revision())).put("controlRecordSha256",head.hash())
            .put("epochId",head.epoch().toString()).put("transportRecoveryEpoch",Long.toString(state.numeric()));
    }
}
