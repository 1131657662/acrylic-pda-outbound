package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.recovery.RecoveryAdmissionEntryChecks;
import com.acrylic.outbound.recovery.RecoveryAdmissionEntryFixture;
import com.acrylic.outbound.recovery.RecoveryEntryLostCommitConfiguration;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The application entry on one isolated input, one control authority per terminal step: JdbcTemplate
 * and the JDBC transaction manager use the admitted writer, close competition and unknown commit
 * outcomes keep the writer fence, and deliberate bypass attempts fail. No HTTP request is driven
 * here and no capability is granted.
 */
@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class E2aRecoveryAdmissionEntryIT {
    @Test void applicationEntryAdmitsJdbcTemplateAndTransactionsWithoutReleasingTheFence() throws Exception {
        var profile=new E2aCrossSourceProfile();var jdbc=profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc,"34");
        E2aV34Seed.seed(jdbc,profile.attempt.resolve("storage"));
        profile.migrate(jdbc,"40");
        int submissions=jdbc.queryForObject("SELECT COUNT(*) FROM submission",Integer.class);
        long projectedEpoch=jdbc.queryForObject("SELECT recovery_epoch FROM server_state",Long.class);
        assertThat(submissions).isPositive();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recovery_normal_admission",Integer.class)).isZero();
        E2aCrossSourceHttpSetup.signing(profile.attempt);
        var live=new RecoveryAdmissionEntryFixture(profile.attempt.resolve("admission-entry"));
        try(var context=E2aCrossSourceHttpIT.start(profile,live.options())) {
            RecoveryAdmissionEntryChecks.verifyAdmittedEntry(context,jdbc,live,profile.attempt);
        }
        var lost=new RecoveryAdmissionEntryFixture(profile.attempt.resolve("admission-unknown-commit"));
        try(var context=E2aCrossSourceHttpIT.start(profile,lost.options())) {
            RecoveryAdmissionEntryChecks.verifyUnknownCommitEntry(context,jdbc,profile.attempt);
        }
        var faulted=new RecoveryAdmissionEntryFixture(profile.attempt.resolve("admission-lost-commit"));
        var faultedOptions=new java.util.ArrayList<String>(java.util.List.of(faulted.identityOptions()));
        faultedOptions.add("--outbound.recovery.test.lost-commit-entry=true");
        try(var context=E2aCrossSourceHttpIT.start(profile,new Class<?>[]{RecoveryEntryLostCommitConfiguration.class},
                faultedOptions.toArray(String[]::new))) {
            RecoveryAdmissionEntryChecks.verifyLostCommitEntry(context,jdbc,profile.attempt);
        }
        // The entry checks only touch their own probe table; the original facts stay as they were.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submission",Integer.class)).isEqualTo(submissions);
        assertThat(jdbc.queryForObject("SELECT recovery_epoch FROM server_state",Long.class)).isEqualTo(projectedEpoch);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recovery_normal_admission",Integer.class)).isZero();
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "V34 nonempty -> V40; production entry wiring admits JdbcTemplate/JdbcTransactionManager; "
                + "close race, held-lease closing intent, connection loss during commit and a lost commit "
                + "acknowledgement all keep the writer fence; unwrap bypass attempts fail; no HTTP request; "
                + "no NORMAL and no production release\n");
    }
}
