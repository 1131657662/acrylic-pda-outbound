package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.command.*;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

class E2aPhysicalDispositionStoreIT {
    @EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY", matches="true")
    @Test void realMysqlReleasesEveryRejectedReservationAndRejectsReplay() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34"); var seed = E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        profile.migrate(jdbc, "56");
        String warehouse = seed.get("warehouseId"), scope = seed.get("mappedScopeId");
        String submission = seed.get("mappedSubmissionId"), set = seed.get("mappedSetId");
        String verification = seed.get("mappedVerificationId"), bale = seed.get("reservedBaleId");
        jdbc.update("UPDATE submission SET status='REJECTED',issues_json=? WHERE id=UUID_TO_BIN(?)",
            "{\"issues\":[{\"code\":\"SUPERVISOR_REJECTED\"}]}", submission);
        jdbc.update("UPDATE source_evidence_verification SET result='VERIFIED_COMPLETE_AT_STOP' WHERE id=UUID_TO_BIN(?)", verification);
        var transaction = new TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
        verifyLocks(jdbc, transaction, seed);
        var verified = transaction.execute(status -> new PhysicalDispositionVerification(jdbc).read(
            UUID.fromString(warehouse), UUID.fromString(scope), UUID.fromString(submission), 1,
            UUID.fromString(set), UUID.fromString(verification), jdbc.queryForObject(
                "SELECT set_sha256 FROM source_evidence_verification WHERE id=UUID_TO_BIN(?)", String.class, verification), 1, 1));
        assertThat(verified.activeBales()).containsExactly(UUID.fromString(bale));
        verifyStore(profile, jdbc, transaction, verified);
        Files.writeString(profile.attempt.resolve("test-completed.marker"), "V56 rejected source release and duplicate rollback passed\n");
    }

    private void verifyLocks(JdbcTemplate jdbc, TransactionTemplate transaction, Map<String,String> seed) {
        var hint = SubmissionJson.object().put("authorizationId", seed.get("authorizationId"))
            .put("creatorDeviceId", seed.get("deviceId")).put("clientOrderId", seed.get("mappedClientOrderId"))
            .put("scopeId", seed.get("mappedScopeId")).put("canonicalOrderId", seed.get("orderId"))
            .put("submissionId", seed.get("mappedSubmissionId"));
        var locks = new CommandLocks(null, null, null, null, jdbc);
        assertThatThrownBy(() -> transaction.execute(status -> { locks.source(new com.acrylic.outbound.temporary.command.CommandContext(hint)); return null; }))
            .isInstanceOf(com.acrylic.outbound.common.BusinessException.class);
        assertThatCode(() -> transaction.execute(status -> { locks.sourceDisposition(new com.acrylic.outbound.temporary.command.CommandContext(hint)); return null; }))
            .doesNotThrowAnyException();
    }

    private void verifyStore(E2aCrossSourceProfile profile, JdbcTemplate jdbc, TransactionTemplate transaction,
        PhysicalDispositionVerification.Verified verified) throws Exception {
        var manifest = SubmissionJson.object().put("version", 1);
        manifest.putArray("items").addObject().put("baleId", verified.activeBales().getFirst().toString()).put("disposition", "RELEASE");
        String manifestHash = SubmissionJson.sha(manifest);
        var store = new PhysicalDispositionStore(jdbc);
        UUID disposition = transaction.execute(status -> store.apply(verified, manifestHash, 1,
            List.of(new PhysicalDispositionManifest.Item(verified.activeBales().getFirst(), "RELEASE")), Instant.now()));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE bale_id=?", Integer.class,
            Ids.bytes(verified.activeBales().getFirst()))).isZero();
        assertThat(jdbc.queryForObject("SELECT action FROM temporary_physical_disposition_item WHERE disposition_id=?",
            String.class, Ids.bytes(disposition))).isEqualTo("RELEASE");
        assertThatThrownBy(() -> transaction.execute(status -> store.apply(verified, manifestHash, 1,
            List.of(new PhysicalDispositionManifest.Item(verified.activeBales().getFirst(), "RELEASE")), Instant.now())))
            .isInstanceOf(RuntimeException.class);
        Files.writeString(profile.attempt.resolve("manifest.json"), SubmissionJson.canonical(manifest));
        Files.writeString(profile.attempt.resolve("manifest-hashes.json"), SubmissionJson.object()
            .put("manifestSha256", manifestHash).put("evidenceSetSha256", verified.setHash()).toString());
    }
}
