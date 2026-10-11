package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Closed preparation has an explicit new qualification field, never a legacy authorization alias. */
public final class RecoveryPreparationCommand {
    public static void require(JsonNode command,JsonNode qualification) {
        fields(command,"commandVersion commandType commandId warehouseId caseId transportRecoveryEpoch targetDeviceId targetUserId targetQualificationId expected");
        uuid(command,"commandId");
        RecoveryControlFormat.require(command.path("commandVersion").isInt() && command.path("commandVersion").intValue()==1
            && text(command,"commandType").equals("PREPARE"),"RECOVERY_PREPARATION_COMMAND");
        RecoveryControlFormat.require(text(command,"targetQualificationId").equals(text(qualification,"qualificationId")),"RECOVERY_PREPARATION_QUALIFICATION");
    }
    private RecoveryPreparationCommand() { }
}
