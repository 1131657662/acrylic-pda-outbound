package com.acrylic.outbound.handover;

import com.acrylic.outbound.recovery.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Arrays;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Actual range review after a signed supervisor fence; no release, registration or qualification. */
@Component
public final class HandoverRecoveredTemporaryReview {
    private final JdbcTemplate jdbc;
    private final HandoverReadReceipt receipts;
    private final HandoverFaultReplay faults;
    private final HandoverRecoveredTemporaryProjection projection;
    private final HandoverRecoveredReviewAccess access;

    HandoverRecoveredTemporaryReview(JdbcTemplate jdbc,HandoverReadReceipt receipts,
            HandoverFaultReplay faults,HandoverRecoveredTemporaryProjection projection,HandoverRecoveredReviewAccess access) {
        this.jdbc=jdbc; this.receipts=receipts; this.faults=faults; this.projection=projection;
        this.access=access;
    }

    public RecoveryReleaseInventory inspect(byte[] originalFault,HandoverRecoveredTemporaryBinding binding) {
        return inspectRecipient(originalFault,binding,null);
    }

    public RecoveryReleaseInventory inspectRecipient(byte[] originalFault,HandoverRecoveredTemporaryBinding binding,
            RecoveryRecipientAdmission admission) {
        Objects.requireNonNull(binding); var command=HandoverCommand.Input.parse(originalFault); binding.require(command);
        HandoverCommand.corruptUnless("FAULT_FENCE".equals(command.kind()));
        var before=admission==null?access.current(command):null;
        return binding.consumer().consumeAfterIdentityLocks(binding.request(),binding.nativeFacts(),
            new RecoveryReceivedTemporaryArchiveConsumer.IdentityConsumer<>() {
                @Override public void lockIdentity() {
                    requireDatabase();
                    if(admission==null) access.lock(command,before); else admission.lockIdentity(jdbc.getDataSource());
                }
                @Override public void requireOrderVersion(JsonNode original,JsonNode current) { projection.require(command,original,current); }
                @Override public RecoveryReleaseInventory apply(RecoveryRecoveredTemporaryArchive archive,
                        RecoveryTemporaryNativeHandoverFacts.Facts facts) {
                    binding.requireArchive(command,facts); var receipt=receipt(command);
                    binding.requireAnchor(command,receipt.path("caseContext"),archive);
                    var effects=admission==null?null:admission.readEffects(jdbc.getDataSource());
                    return new RecoveryReleaseInventoryCapture(jdbc.getDataSource()).capture(archive,receipt,effects);
                }
            });
    }

    private void requireDatabase() {
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.hasResource(jdbc.getDataSource()))
            throw new RecoveryControlException("RECOVERY_RELEASE_TRANSACTION_REQUIRED");
    }

    public RecoveryReleaseReconciliation reconcile(byte[] originalFault,HandoverRecoveredTemporaryBinding binding,
            RecoveryReleaseReconciliation.Request request) {
        Objects.requireNonNull(binding); var command=HandoverCommand.Input.parse(originalFault); binding.require(command);
        HandoverCommand.corruptUnless("FAULT_FENCE".equals(command.kind())); var before=access.current(command);
        var prepared=RecoveryReleaseReconciliation.prepare(inspect(originalFault,binding),request);
        return binding.consumer().consumeAfterIdentityLocks(binding.request(),binding.nativeFacts(),
            new RecoveryReceivedTemporaryArchiveConsumer.IdentityConsumer<>() {
                @Override public void lockIdentity() { requireDatabase(); access.lock(command,before); }
                @Override public void requireOrderVersion(JsonNode original,JsonNode current) { projection.require(command,original,current); }
                @Override public RecoveryReleaseReconciliation apply(RecoveryRecoveredTemporaryArchive archive,
                        RecoveryTemporaryNativeHandoverFacts.Facts facts) {
                    binding.requireArchive(command,facts); var receipt=receipt(command);
                    binding.requireAnchor(command,receipt.path("caseContext"),archive);
                    var inventory=new RecoveryReleaseInventoryCapture(jdbc.getDataSource()).capture(archive,receipt);
                    return RecoveryReleaseReconciliation.inspect(jdbc.getDataSource(),inventory,prepared);
                }
            });
    }

    JsonNode receipt(HandoverCommand.Input command) {
        var rows=jdbc.queryForList("SELECT * FROM handover_command_receipt WHERE original_key=? AND command_type='FAULT_FENCE' FOR UPDATE",
            com.acrylic.outbound.common.Ids.bytes(command.uuid("commandId")));
        HandoverCommand.corruptUnless(rows.size()==1
            && Arrays.equals((byte[])rows.getFirst().get("canonical_command_bytes"),command.bytes()));
        var verified=receipts.verify(rows.getFirst()); faults.verify(verified); return verified.receipt();
    }
}
