package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.temporary.command.PhysicalDispositionStore;
import com.acrylic.outbound.temporary.command.PhysicalDispositionVerification;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

class E2aPhysicalDispositionStoreIT {
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY", matches="true")
    @Test void realMysqlTransfersEveryReservationAndRejectsReplay() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34"); var seed = E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        profile.migrate(jdbc, "55");
        String warehouse=seed.get("warehouseId"), scope=seed.get("mappedScopeId"), submission=seed.get("mappedSubmissionId"), set=seed.get("mappedSetId"), verification=seed.get("mappedVerificationId"), bale=seed.get("reservedBaleId");
        jdbc.update("UPDATE source_evidence_verification SET result='VERIFIED_COMPLETE_AT_STOP' WHERE id=UUID_TO_BIN(?)", verification);
        var verified = new TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource())).execute(status ->
            new PhysicalDispositionVerification(jdbc).read(UUID.fromString(warehouse),UUID.fromString(scope),UUID.fromString(submission),1,
                UUID.fromString(set),UUID.fromString(verification),jdbc.queryForObject("SELECT set_sha256 FROM source_evidence_verification WHERE id=UUID_TO_BIN(?)",String.class,verification),1,1));
        assertThat(verified.activeBales()).containsExactly(UUID.fromString(bale));
        var store = new PhysicalDispositionStore(jdbc);
        UUID disposition = new TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource())).execute(status ->
            store.apply(verified,verified.setHash(),verified.verificationVersion(),verified.activeBales(),Instant.now()));
        assertThat(jdbc.queryForObject("SELECT state FROM bale_commitment WHERE bale_id=UUID_TO_BIN(?)",String.class,bale)).isEqualTo("QUARANTINED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM temporary_physical_disposition_item WHERE disposition_id=?",Integer.class,Ids.bytes(disposition))).isEqualTo(1);
        assertThatThrownBy(() -> new TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource())).execute(status ->
            store.apply(verified,verified.setHash(),verified.verificationVersion(),verified.activeBales(),Instant.now())))
            .isInstanceOf(RuntimeException.class);
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"V55 real MySQL disposition transfer and replay rejection\n");
    }
}
