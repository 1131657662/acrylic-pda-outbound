package com.acrylic.outbound.recovery;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RecoveryReceivedPermissionLogTest {
    @ParameterizedTest @ValueSource(strings={"outbound_order","order_line","order_revision","order_number_counter","order_submission_slot",
        "order_sync_outbox","submitted_capture","submitted_event","temporary_decision_state","temporary_source_mapping","temporary_work_origin",
        "source_evidence_intake_receipt","source_evidence_page_item_stage","source_evidence_verification","pda_audit_checkpoint","pda_audit_event","barcode_sync_pending"})
    void verifiesLoggedBusinessRowsWithoutGrantingNormalWork(String table) {
        var fixture=new RecoveryControlledPermissionFixture(); fixture.logged.replaceTransaction(table);
        var proof=fixture.verify();
        assertThat(proof.historicalAuthorityEstablished()).isTrue(); assertThat(proof.committedTransactions()).isEqualTo(1);
        assertThat(proof.ordinaryWritesAllowed()).isFalse(); assertThat(proof.localDeletionAllowed()).isFalse();
    }
    @ParameterizedTest @ValueSource(strings={"app_user","device","role","permission","user_role","role_permission","user_warehouse","warehouse",
        "server_state","pda_session","device_pairing","temporary_preparation_ack","unknown"})
    void refusesAuthorityOrUnsupportedRowsInControlledHistory(String table) {
        var fixture=new RecoveryControlledPermissionFixture(); fixture.logged.replaceTransaction(table);
        assertThatThrownBy(fixture::verify).hasMessage("RECOVERY_BINLOG_AUTHORITY_OR_UNKNOWN_TABLE");
    }
}
