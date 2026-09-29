package com.acrylic.outbound.recovery;

import java.nio.file.Files;
import java.nio.file.Path;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Entry re-base, runnable on an isolated instance: the live, connection-loss and lost-acknowledgement
 * entries are driven through the same checks the C1/E2a harnesses use, now that the primary data
 * source is the request-root one. Every check runs inside an operation root the entry admits, and a
 * thread without a root is refused.
 */
class RecoveryAdmissionEntryRebaseIT {
    private static final int APP_PORT = Integer.getInteger("s3.http.port",
        Integer.parseInt(System.getenv().getOrDefault("S3_HTTP_PORT", "19458")));

    @Test void theEntryServesOneOperationRootAndRefusesAThreadWithoutOne() throws Exception {
        EffectRealEnvironment.requireEnvironment();
        migrate();
        Path evidence = Path.of(System.getenv("B11_EFFECT_EVIDENCE_DIR"));
        Path storage = Files.createDirectories(evidence.resolve("storage"));
        var raw = new JdbcTemplate(new DriverManagerDataSource(System.getenv("OUTBOUND_DB_URL"),
            System.getenv("OUTBOUND_DB_USER"), System.getenv("OUTBOUND_DB_PASSWORD")));
        var transcript = new StringBuilder();
        // The independent originals generator wants real restore roots: when the runner names its
        // isolated instance, the fixture lives beside that instance's datadir (its restore identity);
        // otherwise the evidence tree provides placeholder directories.
        String instance = System.getenv("S3_INSTANCE_DIR");
        Path fixtureBase = instance == null ? evidence : Path.of(instance);
        String stamp = Long.toString(System.currentTimeMillis());
        Path data = Files.createDirectories(fixtureBase.resolve("data"));
        Files.createDirectories(fixtureBase.resolve("storage"));
        transcript.append("fixtureBase=").append(fixtureBase).append(" restoreData=").append(data)
            .append(" instanceBound=").append(instance != null).append('\n');

        var live = new RecoveryAdmissionEntryFixture(fixtureBase.resolve("entry-live-" + stamp));
        try (var context = start(live.options(), storage)) {
            RecoveryAdmissionEntryChecks.verifyAdmittedEntry(context, raw, live, evidence);
            transcript.append("live: admitted entry served inside one root; thread without root refused\n");
        }
        var unknown = new RecoveryAdmissionEntryFixture(fixtureBase.resolve("entry-unknown-" + stamp));
        try (var context = start(unknown.options(), storage)) {
            RecoveryAdmissionEntryChecks.verifyUnknownCommitEntry(context, raw, evidence);
            transcript.append("unknown: connection lost during commit keeps the writer fence\n");
        }
        var lost = new RecoveryAdmissionEntryFixture(fixtureBase.resolve("entry-lost-" + stamp));
        // The faulted entry wiring is a test source, exactly like in the C1/E2a harnesses.
        try (var context = start(RecoveryEntryLostCommitConfiguration.class, lost.identityOptions(), storage,
                "--outbound.recovery.test.lost-commit-entry=true")) {
            RecoveryAdmissionEntryChecks.verifyLostCommitEntry(context, raw, evidence);
            transcript.append("lost: lost commit acknowledgement keeps the writer fence\n");
        }

        assertThat(raw.queryForObject("SELECT COUNT(*) FROM information_schema.INNODB_TRX", Integer.class)).isZero();
        Files.writeString(evidence.resolve("entry-rebase.txt"), transcript.toString());
        EffectRealEnvironment.record("S8|REAL|entry-rebase|" + transcript.toString().replace('\n', '|'));
    }

    private ConfigurableApplicationContext start(String[] options, Path storage, String... extra) {
        return start(null, options, storage, extra);
    }

    private ConfigurableApplicationContext start(Class<?> source, String[] options, Path storage, String... extra) {
        var arguments = new java.util.ArrayList<String>(java.util.List.of(
            "--server.address=127.0.0.1", "--server.port=" + APP_PORT,
            "--spring.flyway.enabled=false", "--spring.session.jdbc.cleanup-cron=-",
            "--outbound.worker.enabled=false", "--outbound.bootstrap.enabled=false",
            "--outbound.storage=" + storage));
        arguments.addAll(java.util.List.of(options));
        arguments.addAll(java.util.List.of(extra));
        var application = source == null
            ? new SpringApplication(com.acrylic.outbound.OutboundApplication.class)
            : new SpringApplication(com.acrylic.outbound.OutboundApplication.class, source);
        return application.run(arguments.toArray(String[]::new));
    }

    private static void migrate() {
        Flyway.configure().dataSource(System.getenv("OUTBOUND_DB_URL"), System.getenv("OUTBOUND_DB_USER"),
            System.getenv("OUTBOUND_DB_PASSWORD")).load().migrate();
    }
}
