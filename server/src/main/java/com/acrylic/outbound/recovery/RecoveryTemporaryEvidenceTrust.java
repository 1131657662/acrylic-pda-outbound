package com.acrylic.outbound.recovery;

import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Binds the separately retained registration root and controlled historical windows to one original source database. */
final class RecoveryTemporaryEvidenceTrust {
    private RecoveryTemporaryEvidenceTrust() { }

    static void verify(RecoveryPinnedSource source,RecoveryDeviceRegistration.Proof registration,
            RecoveryOriginalEvidence.Request evidence,RecoverySourcePermission.Proof permission,
            List<RecoveryPermissionContinuity.Request> history,RecoveryDeviceRegistrationCapture.Database database) {
        var id=source.identity(); var original=registration.expected();
        require(original.mysqlServerUuid().equals(database.mysqlServerUuid()) && original.databaseSchema().equals(database.schema())
            && original.sourceRecoveryEpoch().equals(database.sourceRecoveryEpoch()) && original.warehouseId().equals(id.warehouseId())
            && original.deviceId().equals(id.deviceId()) && Arrays.equals(registration.trustedKey().getEncoded(),evidence.trustedKey().getEncoded()),
            "RECOVERY_EVIDENCE_REGISTRATION_SOURCE");
        require(history.size()==permission.windows().size() && !history.isEmpty(),"RECOVERY_EVIDENCE_PERMISSION_WINDOWS");
        for(int index=0;index<history.size();index++) {
            var proof=RecoveryPermissionContinuity.verifyControlled(history.get(index)); var window=permission.windows().get(index);
            var expected=proof.identity(); var effective=proof.effective();
            require(proof.historicalAuthorityEstablished() && expected.database().equals(database)
                && expected.operatorId().equals(id.operatorId()) && expected.deviceId().equals(id.deviceId())
                && proof.from().equals(window.from()) && proof.through().equals(window.through())
                && !registration.observedAt().isAfter(proof.from()),"RECOVERY_EVIDENCE_PERMISSION_SOURCE");
            require(effective.path("userActive").booleanValue()==window.userActive() && text(effective,"deviceStatus").equals(window.deviceStatus())
                && text(effective,"userAuthVersion").equals(window.userAuthVersion()) && text(effective,"deviceVersion").equals(window.deviceVersion())
                && effective.path("deviceTemporaryOrderEnabled").booleanValue()==window.deviceTemporaryOrderEnabled()
                && uuid(effective,"warehouseId").equals(id.warehouseId()),"RECOVERY_EVIDENCE_PERMISSION_STATE");
            var permissions=new HashSet<String>(); effective.path("permissions").forEach(value -> permissions.add(value.textValue()));
            var warehouses=new HashSet<UUID>(); effective.path("warehouseIds").forEach(value -> warehouses.add(UUID.fromString(value.textValue())));
            require(permissions.equals(window.permissions()) && warehouses.equals(window.warehouses()),"RECOVERY_EVIDENCE_PERMISSION_STATE");
        }
    }
}
