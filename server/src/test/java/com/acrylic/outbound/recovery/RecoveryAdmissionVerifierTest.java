package com.acrylic.outbound.recovery;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** The read-only admission check must stay closed on every missing or contradictory side. */
class RecoveryAdmissionVerifierTest {
    private static final UUID INSTALLATION = UUID.randomUUID();
    private static final String HASH = "a".repeat(64);
    private static final String PREVIOUS = "b".repeat(64);
    private static final String INTENT = "c".repeat(64);

    @Test void consistentBothSidesAdmitsWithoutOpeningWrites() {
        var result = verifier(state("NORMAL", 2), binding(INSTALLATION), admission(4, HASH, INTENT),
            projection(4, HASH, "RELEASE_READY", 2), control("4", HASH, PREVIOUS), durable(4, HASH, INTENT)).verify();

        assertThat(result.admitted()).isTrue();
        assertThat(result.gaps()).isEmpty();
        assertThat(result.installationId()).isEqualTo(INSTALLATION.toString());
        assertThat(result.controlRevision()).isEqualTo("4");
        assertThat(result.numericEpoch()).isEqualTo(2);
        assertThat(result.ordinaryWritesAllowed()).isFalse();
        assertThat(result.localDeletionAllowed()).isFalse();
    }

    @Test void aDatabaseRollbackBelowTheDurableChainIsRefused() {
        // the authority head is revision 5 while the retained admission still claims the revision 4 hash
        var result = verifier(state("NORMAL", 2), binding(INSTALLATION), admission(4, "f".repeat(64), INTENT),
            projection(4, "f".repeat(64), "RELEASE_READY", 2), control("5", HASH, PREVIOUS), durable(5, HASH, INTENT)).verify();

        assertThat(result.admitted()).isFalse();
        assertThat(result.gaps()).contains("PROJECTION_REVISION", "ADMISSION_AUTHORITY");
    }

    @Test void anAuthorityRollbackBehindTheFrozenAdmissionIsRefused() {
        // the admission was frozen at revision 5; the authority no longer has that head
        var result = verifier(state("NORMAL", 2), binding(INSTALLATION), admission(5, HASH, INTENT),
            projection(5, HASH, "RELEASE_READY", 2), control("4", HASH, PREVIOUS), durable(4, HASH, INTENT)).verify();

        assertThat(result.admitted()).isFalse();
        assertThat(result.gaps()).contains("PROJECTION_REVISION");
    }

    @Test void aMissingDurableIntentOrTamperedHashIsRefused() {
        var missing = verifier(state("NORMAL", 2), binding(INSTALLATION), admission(4, HASH, INTENT),
            projection(4, HASH, "RELEASE_READY", 2), control("4", HASH, PREVIOUS), null).verify();
        assertThat(missing.admitted()).isFalse();
        assertThat(missing.gaps()).contains("DURABLE_INTENT_MISSING");

        var tampered = verifier(state("NORMAL", 2), binding(INSTALLATION), admission(4, HASH, INTENT),
            projection(4, HASH, "RELEASE_READY", 2), control("4", HASH, PREVIOUS), durable(4, "d".repeat(64), INTENT)).verify();
        assertThat(tampered.admitted()).isFalse();
        assertThat(tampered.gaps()).contains("DURABLE_INTENT_HASH");
    }

    @Test void unregisteredDatabaseOrChangedInstanceIsRefused() {
        var unregistered = verifier(state("NORMAL", 1), null, null, null, control("4", HASH, PREVIOUS), durable(4, HASH, INTENT)).verify();
        assertThat(unregistered.admitted()).isFalse();
        assertThat(unregistered.gaps()).contains("DATABASE_BINDING_MISSING", "NORMAL_ADMISSION_MISSING", "PROJECTION_MISSING");

        var other = verifier(state("NORMAL", 2), binding(UUID.randomUUID()), admission(4, HASH, INTENT),
            projection(4, HASH, "RELEASE_READY", 2), control("4", HASH, PREVIOUS), durable(4, HASH, INTENT)).verify();
        assertThat(other.admitted()).isFalse();
        assertThat(other.gaps()).contains("DATABASE_INSTALLATION");
    }

    @Test void closedPhaseOrWrongModeIsRefused() {
        var openButRecovering = verifier(state("RECOVERY_PENDING", 2), binding(INSTALLATION), admission(4, HASH, INTENT),
            projection(4, HASH, "RELEASE_READY", 2), control("4", HASH, PREVIOUS), durable(4, HASH, INTENT)).verify();
        assertThat(openButRecovering.admitted()).isFalse();
        assertThat(openButRecovering.gaps()).contains("SERVER_MODE");

        var notTerminal = verifier(state("NORMAL", 2), binding(INSTALLATION), admission(4, HASH, INTENT),
            projection(4, HASH, "VERIFYING", 2), control("4", HASH, PREVIOUS), durable(4, HASH, INTENT)).verify();
        assertThat(notTerminal.admitted()).isFalse();
        assertThat(notTerminal.gaps()).contains("PROJECTION_PHASE");
    }

    @Test void mismatchedIntentRecordOrNumericIsRefused() {
        var intent = verifier(state("NORMAL", 2), binding(INSTALLATION), admission(4, HASH, "e".repeat(64)),
            projection(4, HASH, "RELEASE_READY", 2), control("4", HASH, PREVIOUS), durable(4, HASH, INTENT)).verify();
        assertThat(intent.admitted()).isFalse();
        assertThat(intent.gaps()).contains("ADMISSION_INTENT");

        var numeric = verifier(state("NORMAL", 3), binding(INSTALLATION), admission(4, HASH, INTENT),
            projection(4, HASH, "RELEASE_READY", 2), control("4", HASH, PREVIOUS), durable(4, HASH, INTENT)).verify();
        assertThat(numeric.admitted()).isFalse();
        assertThat(numeric.gaps()).contains("SERVER_NUMERIC");
    }

    @Test void anUnreadableAuthorityReportsUnavailableInsteadOfAdmitting() {
        var failing = new RecoveryAdmissionVerifier(evidence(state("NORMAL", 2), binding(INSTALLATION), admission(4, HASH, INTENT),
            projection(4, HASH, "RELEASE_READY", 2)), () -> { throw new RecoveryControlException("CONTROL_AUTHORITY_UNAVAILABLE"); },
            () -> durable(4, HASH, INTENT));

        var result = failing.verify();

        assertThat(result.admitted()).isFalse();
        assertThat(result.gaps()).containsExactly("AUTHORITY_UNAVAILABLE");
        assertThat(result.installationId()).isNull();
    }

    private static RecoveryAdmissionVerifier verifier(RecoveryAdmissionEvidence.State state,
            RecoveryAdmissionEvidence.Binding binding, RecoveryAdmissionEvidence.Admission admission,
            RecoveryAdmissionEvidence.Projection projection, RecoveryControlSnapshot control,
            RecoveryAdmissionVerifier.RecoveryIntentView durable) {
        return new RecoveryAdmissionVerifier(evidence(state, binding, admission, projection), () -> control, () -> durable);
    }

    private static RecoveryAdmissionEvidence evidence(RecoveryAdmissionEvidence.State state,
            RecoveryAdmissionEvidence.Binding binding, RecoveryAdmissionEvidence.Admission admission,
            RecoveryAdmissionEvidence.Projection projection) {
        return new RecoveryAdmissionEvidence() {
            @Override public Binding binding() { return binding; }
            @Override public Admission admission() { return admission; }
            @Override public Projection projection() { return projection; }
            @Override public State state() { return state; }
        };
    }

    private static RecoveryControlSnapshot control(String revision, String hash, String previous) {
        return new RecoveryControlSnapshot(INSTALLATION, revision, UUID.randomUUID(), UUID.randomUUID(), "RELEASE_READY",
            "snapshot-1", HASH, hash, previous, List.of("W1"), HASH, java.time.Instant.EPOCH, java.time.Instant.EPOCH);
    }

    private static RecoveryAdmissionVerifier.RecoveryIntentView durable(long revision, String hash, String intent) {
        return new RecoveryAdmissionVerifier.RecoveryIntentView() {
            @Override public long revision() { return revision; }
            @Override public String recordSha256() { return hash; }
            @Override public String intentSha256() { return intent; }
        };
    }

    private static RecoveryAdmissionEvidence.Binding binding(UUID installation) {
        return new RecoveryAdmissionEvidence.Binding(installation, 1);
    }

    private static RecoveryAdmissionEvidence.Admission admission(long revision, String hash, String intent) {
        return new RecoveryAdmissionEvidence.Admission(revision, hash, intent);
    }

    private static RecoveryAdmissionEvidence.Projection projection(long revision, String hash, String phase, long numeric) {
        return new RecoveryAdmissionEvidence.Projection(revision, hash, phase, numeric);
    }

    private static RecoveryAdmissionEvidence.State state(String mode, long numeric) {
        return new RecoveryAdmissionEvidence.State(mode, numeric);
    }
}
