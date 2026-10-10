package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RecoveryControlledPermissionTest {
    @Test void verifiesHistoricalWindowFromOriginalConstrainedProfileWithoutOrdinaryAdmission() {
        var f = new RecoveryControlledPermissionFixture(); var proof = f.verify();
        assertThat(proof.historicalAuthorityEstablished()).isTrue(); assertThat(proof.ordinaryWritesAllowed()).isFalse();
        assertThat(proof.localDeletionAllowed()).isFalse(); assertThat(proof.committedTransactions()).isEqualTo(1);
    }

    @Test void rejectsUncontrolledObservationInsteadOfPromotingIt() {
        var f = new RecoveryPermissionLogFixture();
        assertThatThrownBy(() -> RecoveryPermissionContinuity.verifyControlled(f.request())).hasMessage("CONTROL_FIELDS");
    }

    @Test void keepsUncontrolledEntryStrict() {
        var f = new RecoveryControlledPermissionFixture();
        assertThatThrownBy(() -> RecoveryPermissionContinuity.verify(f.logged.request())).hasMessage("CONTROL_FIELDS");
    }

    @Test void refusesActiveRoot() {
        var f = new RecoveryControlledPermissionFixture(); ((ObjectNode)f.profile.path("accounts").get(0)).put("locked", false);
        assertThatThrownBy(f::verify).hasMessage("RECOVERY_PERMISSION_WRITER_PRIVILEGED_ACCOUNT");
    }

    @Test void refusesSecondUnprivilegedAccountBecauseItsGrantsWereNotProven() {
        var f = new RecoveryControlledPermissionFixture(); ((ArrayNode)f.profile.path("accounts")).addObject()
            .put("user", "unknown").put("host", "localhost").put("locked", false);
        assertThatThrownBy(f::verify).hasMessage("RECOVERY_PERMISSION_WRITER_PRIVILEGED_ACCOUNT");
    }

    @ParameterizedTest @ValueSource(strings={"SUPER", "SESSION_VARIABLES_ADMIN", "SYSTEM_VARIABLES_ADMIN", "BINLOG_ADMIN", "GRANT OPTION", "FILE", "CREATE USER", "CREATE", "UPDATE"})
    void refusesExtraPrivilegesEvenWhenTheEffectiveUserStateIsUnchanged(String privilege) {
        var f = new RecoveryControlledPermissionFixture(); ((ArrayNode)f.profile.path("grants"))
            .add("GRANT " + privilege + " ON *.* TO `original_writer`@`127.0.0.1`");
        assertThatThrownBy(f::verify).hasMessage("RECOVERY_PERMISSION_WRITER_GRANTS");
    }

    @Test void refusesDatabaseGrantWithGrantOption() {
        var f = new RecoveryControlledPermissionFixture(); var grants = (ArrayNode)f.profile.path("grants");
        grants.set(1, TextNode.valueOf(grants.get(1).asText() + " WITH GRANT OPTION"));
        assertThatThrownBy(f::verify).hasMessage("RECOVERY_PERMISSION_WRITER_GRANTS");
    }

    @Test void refusesRoleInheritance() {
        var f = new RecoveryControlledPermissionFixture(); f.profile.put("roleEdges", "1");
        assertThatThrownBy(f::verify).hasMessage("RECOVERY_PERMISSION_WRITER_ROLES");
    }

    @Test void refusesMandatoryRoles() {
        var f = new RecoveryControlledPermissionFixture(); f.profile.put("mandatoryRoles", "privileged");
        assertThatThrownBy(f::verify).hasMessage("RECOVERY_PERMISSION_WRITER_ROLES");
    }

    @Test void refusesDifferentWriterProfileAtEnd() {
        var f = new RecoveryControlledPermissionFixture(); var end = (ObjectNode)f.logged.end.path("writerProfile");
        ((ArrayNode)end.path("accounts")).addObject().put("user", "locked_extra").put("host", "localhost").put("locked", true);
        assertThatThrownBy(() -> RecoveryPermissionContinuity.verifyControlled(f.logged.request())).hasMessage("RECOVERY_PERMISSION_WRITER_CHANGED");
    }

    @Test void refusesOtherLiveConnectionsEvenIfAllOtherAccountsAreLocked() {
        var f = new RecoveryControlledPermissionFixture(); f.profile.put("exclusiveCurrentSession", false);
        assertThatThrownBy(f::verify).hasMessage("RECOVERY_PERMISSION_WRITER_SESSIONS");
    }

    @Test void refusesEventScheduler() {
        var f = new RecoveryControlledPermissionFixture(); f.profile.put("eventScheduler", "ON");
        assertThatThrownBy(f::verify).hasMessage("RECOVERY_PERMISSION_WRITER_BACKGROUND");
    }

    @Test void refusesStaleSessionPrivilegesEvenWhenItsAccountGrantsWereRevoked() {
        var f = new RecoveryControlledPermissionFixture(); f.profile.put("sessionCannotDisableLogging", false);
        assertThatThrownBy(f::verify).hasMessage("RECOVERY_PERMISSION_WRITER_SESSION_PRIVILEGES");
    }

    @Test void refusesReplicationThreads() {
        var f = new RecoveryControlledPermissionFixture(); f.profile.put("replicationThreads", "1");
        assertThatThrownBy(f::verify).hasMessage("RECOVERY_PERMISSION_WRITER_BACKGROUND");
    }

    @Test void refusesLoggedPermissionRevocationInsideControlledWindow() {
        var f = new RecoveryControlledPermissionFixture(); f.logged.replaceTransaction("role_permission");
        assertThatThrownBy(f::verify).hasMessage("RECOVERY_BINLOG_AUTHORITY_OR_UNKNOWN_TABLE");
    }
}
