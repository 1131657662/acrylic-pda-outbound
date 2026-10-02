package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.command.*;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

class E2aPhysicalDispositionStoreIT {
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY", matches="true")
    @Test void realMysqlTransfersEveryReservationAndRejectsReplay() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34"); var seed = E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        profile.migrate(jdbc, "55");
        String warehouse = seed.get("warehouseId"), scope = seed.get("mappedScopeId");
        String submission = seed.get("mappedSubmissionId"), set = seed.get("mappedSetId");
        String verification = seed.get("mappedVerificationId"), bale = seed.get("reservedBaleId");
        // This is a store/transaction fixture, not proof of the trusted STOP/VERIFY HTTP chain.
        jdbc.update("UPDATE source_evidence_verification SET result='VERIFIED_COMPLETE_AT_STOP' WHERE id=UUID_TO_BIN(?)", verification);
        var transaction = new TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
        verifyLocks(jdbc, transaction, seed);
        var verified = transaction.execute(status -> new PhysicalDispositionVerification(jdbc).read(
            UUID.fromString(warehouse), UUID.fromString(scope), UUID.fromString(submission), 1,
            UUID.fromString(set), UUID.fromString(verification), jdbc.queryForObject(
                "SELECT set_sha256 FROM source_evidence_verification WHERE id=UUID_TO_BIN(?)", String.class, verification), 1, 1));
        assertThat(verified.activeBales()).containsExactly(UUID.fromString(bale));
        verifyStore(profile, jdbc, transaction, verified);
        Files.writeString(profile.attempt.resolve("test-completed.marker"), "Isolated MySQL source locks, independent manifest hash and duplicate rollback passed\n");
    }

    private void verifyLocks(org.springframework.jdbc.core.JdbcTemplate jdbc, TransactionTemplate transaction, Map<String,String> seed) {
        var hint = SubmissionJson.object().put("authorizationId", seed.get("authorizationId"))
            .put("creatorDeviceId", seed.get("deviceId")).put("clientOrderId", seed.get("mappedClientOrderId"))
            .put("scopeId", seed.get("mappedScopeId")).put("canonicalOrderId", seed.get("orderId"))
            .put("submissionId", seed.get("mappedSubmissionId"));
        var locks = new CommandLocks(null, null, null, null, jdbc);
        assertThatThrownBy(() -> transaction.execute(status -> { locks.source(new CommandContext(hint)); return null; }))
            .isInstanceOf(com.acrylic.outbound.common.BusinessException.class);
        assertThatCode(() -> transaction.execute(status -> { locks.sourceDisposition(new CommandContext(hint)); return null; }))
            .doesNotThrowAnyException();
    }

    private void verifyStore(E2aCrossSourceProfile profile, org.springframework.jdbc.core.JdbcTemplate jdbc,
        TransactionTemplate transaction, PhysicalDispositionVerification.Verified verified) throws Exception {
        var manifest = SubmissionJson.object().put("version", 1);
        manifest.putArray("items").addObject().put("baleId", verified.activeBales().getFirst().toString())
            .put("disposition", "PHYSICAL_DISPOSED");
        String manifestHash = SubmissionJson.sha(manifest);
        assertThat(manifestHash).isNotEqualTo(verified.setHash());
        var store = new PhysicalDispositionStore(jdbc);
        UUID disposition = transaction.execute(status -> store.apply(verified, manifestHash, 1, verified.activeBales(), Instant.now()));
        assertThat(jdbc.queryForObject("SELECT state FROM bale_commitment WHERE bale_id=?", String.class,
            Ids.bytes(verified.activeBales().getFirst()))).isEqualTo("QUARANTINED");
        assertThat(jdbc.queryForObject("SELECT manifest_sha256 FROM temporary_physical_disposition WHERE id=?",
            String.class, Ids.bytes(disposition))).isEqualTo(manifestHash);
        assertThatThrownBy(() -> transaction.execute(status -> store.apply(verified, manifestHash, 1, verified.activeBales(), Instant.now())))
            .isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM temporary_physical_disposition_item WHERE disposition_id=?",
            Integer.class, Ids.bytes(disposition))).isEqualTo(1);
        Files.writeString(profile.attempt.resolve("manifest.json"), SubmissionJson.canonical(manifest));
        Files.writeString(profile.attempt.resolve("manifest-hashes.json"), SubmissionJson.object()
            .put("manifestSha256", manifestHash).put("evidenceSetSha256", verified.setHash()).toString());
    }
}
