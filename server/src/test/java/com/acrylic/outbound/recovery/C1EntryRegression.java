package com.acrylic.outbound.recovery;

import java.util.*;
import org.springframework.boot.test.context.TestConfiguration;

/** Reuses all original attempt105 connection-entry assertions against the main-tree isolated DB. */
final class C1EntryRegression {
    private C1EntryRegression() { }
    @TestConfiguration(proxyBeanMethods = false) static class Empty { }

    static void verify(C1MainProfile profile) throws Exception {
        var live = new RecoveryAdmissionEntryFixture(profile.attempt.resolve("entry-live"));
        try (var app = profile.start(Empty.class, live.options())) {
            RecoveryAdmissionEntryChecks.verifyAdmittedEntry(app, profile.jdbc, live, profile.attempt);
        }
        var unknown = new RecoveryAdmissionEntryFixture(profile.attempt.resolve("entry-unknown"));
        try (var app = profile.start(Empty.class, unknown.options())) {
            RecoveryAdmissionEntryChecks.verifyUnknownCommitEntry(app, profile.jdbc, profile.attempt);
        }
        var lost = new RecoveryAdmissionEntryFixture(profile.attempt.resolve("entry-lost"));
        var options = new ArrayList<>(List.of(lost.identityOptions()));
        options.add("--outbound.recovery.test.lost-commit-entry=true");
        try (var app = profile.start(RecoveryEntryLostCommitConfiguration.class, options.toArray(String[]::new))) {
            RecoveryAdmissionEntryChecks.verifyLostCommitEntry(app, profile.jdbc, profile.attempt);
        }
    }
}
