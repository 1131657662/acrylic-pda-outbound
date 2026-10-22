package com.acrylic.outbound.recovery;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static com.acrylic.outbound.recovery.RecoveryAdmissionEvidence.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Missing physical or external identity must never be inferred from copied recovery rows. */
class RecoveryAdmissionIdentityTest {
    private static final UUID INSTALLATION = UUID.randomUUID();
    private static final Database DB = new Database(UUID.randomUUID(), "model_schema");
    private static final String HASH = "a".repeat(64);

    @Test void differentPhysicalServerIsDenied() {
        assertDenied(snapshot(new Database(UUID.randomUUID(), DB.schema()), DB, 1), intent(DB.schema(), 1),
            "DATABASE_SERVER_UUID");
    }

    @Test void copiedRowsInAnotherSchemaAreDenied() {
        assertDenied(snapshot(new Database(DB.serverUuid(), "copied_schema"), DB, 1), intent(DB.schema(), 1),
            "DATABASE_SCHEMA");
    }

    @Test void incompletePhysicalIdentityIsDenied() {
        for (Database missing : new Database[]{null, new Database(null, DB.schema()), new Database(DB.serverUuid(), "")})
            assertDenied(snapshot(missing, DB, 1), intent(DB.schema(), 1), "DATABASE_IDENTITY_MISSING");
    }

    @Test void externalSchemaAndBaselineMustMatchBinding() {
        assertDenied(snapshot(DB, DB, 1), intent("other", 1), "DURABLE_INTENT_SCHEMA");
        assertDenied(snapshot(DB, DB, 2), intent(DB.schema(), 1), "DURABLE_INTENT_BASELINE");
        assertDenied(snapshot(DB, DB, 0), intent(DB.schema(), 0), "DURABLE_INTENT_BASELINE");
    }

    @Test void legacyIntentWithoutIdentityStaysClosed() {
        var legacy = new RecoveryAdmissionVerifier.RecoveryIntentView() {
            public long revision() { return 4; }
            public String recordSha256() { return HASH; }
            public String intentSha256() { return HASH; }
        };
        assertDenied(snapshot(DB, DB, 1), legacy, "DURABLE_INTENT_SCHEMA_MISSING", "DURABLE_INTENT_BASELINE");
    }

    @Test void legacySupplierIsNotReadAsSeparateObservations() {
        var legacy = new RecoveryAdmissionEvidence() {
            public Binding binding() { throw new AssertionError("separate read forbidden"); }
            public Admission admission() { throw new AssertionError("separate read forbidden"); }
            public Projection projection() { throw new AssertionError("separate read forbidden"); }
            public State state() { throw new AssertionError("separate read forbidden"); }
        };
        assertThat(verifier(legacy, intent(DB.schema(), 1)).verify().gaps()).containsExactly("DATABASE_EVIDENCE_MISSING");
    }

    private static void assertDenied(Closed db, RecoveryAdmissionVerifier.RecoveryIntentView intent, String... gaps) {
        var evidence = new RecoveryAdmissionEvidence() {
            public Closed closed() { return db; }
            public Binding binding() { throw new AssertionError("separate read forbidden"); }
            public Admission admission() { throw new AssertionError("separate read forbidden"); }
            public Projection projection() { throw new AssertionError("separate read forbidden"); }
            public State state() { throw new AssertionError("separate read forbidden"); }
        };
        var result = verifier(evidence, intent).verify();
        assertThat(result.admitted()).isFalse();
        assertThat(result.gaps()).contains(gaps);
        assertThat(result.ordinaryWritesAllowed()).isFalse();
        assertThat(result.localDeletionAllowed()).isFalse();
    }

    private static RecoveryAdmissionVerifier verifier(RecoveryAdmissionEvidence evidence,
            RecoveryAdmissionVerifier.RecoveryIntentView intent) {
        var control = new RecoveryControlSnapshot(INSTALLATION, "4", UUID.randomUUID(), UUID.randomUUID(),
            "RELEASE_READY", "model", HASH, HASH, HASH, List.of("W1"), HASH,
            java.time.Instant.EPOCH, java.time.Instant.EPOCH);
        return new RecoveryAdmissionVerifier(evidence, () -> control, () -> intent);
    }

    private static Closed snapshot(Database actual, Database registered, long baseline) {
        return new Closed(actual, registered, new Binding(INSTALLATION, baseline), new Admission(4, HASH, HASH),
            new Projection(4, HASH, "RELEASE_READY", 2), new State("NORMAL", 2));
    }

    private static RecoveryAdmissionVerifier.RecoveryIntentView intent(String schema, long baseline) {
        return new RecoveryAdmissionVerifier.RecoveryIntentView() {
            public long revision() { return 4; }
            public String recordSha256() { return HASH; }
            public String intentSha256() { return HASH; }
            public String schema() { return schema; }
            public long baseline() { return baseline; }
        };
    }
}
