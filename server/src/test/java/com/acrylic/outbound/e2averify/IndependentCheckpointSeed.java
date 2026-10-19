package com.acrylic.outbound.e2averify;

import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

/** Restore the retained native identity and original bytes into a newly allocated lab only. */
final class IndependentCheckpointSeed {
    private static final Path ROOT = E2aCrossSourceProfile.ROOT;
    private static final Path OLD = ROOT.resolve(".local/e2a-cross-20260925-1514");
    private static final Path DUMP = ROOT.resolve(".local/goal-20261013/e05-preserve-1514-e06-closure-completed01/database.sql");
    private static final String PIN = "f519bc290c900f9bcbec55bbd24605945b3441a8fb3f45e0f1438b0bc871ced4";
    private IndependentCheckpointSeed() { }

    static void initialize(E2aCrossSourceProfile profile, JdbcTemplate jdbc) throws Exception {
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        assertThat(jdbc.queryForList("SHOW TABLES")).isEmpty();
        assertThat(E2aPointInTimeCheckpoint.sha(DUMP)).isEqualTo(PIN);
        ensureClientConfiguration(profile);
        definers(profile, jdbc);
        new E2aPointInTimeCommands(profile).restore(profile.database, DUMP, profile.attempt.resolve("legacy-import.stdout"));
        assertThat(E2aPointInTimeCheckpoint.sha(DUMP)).isEqualTo(PIN);
        copyStorage(profile);
        for (String name : List.of("server-private.der", "server-public.der"))
            Files.copy(OLD.resolve("private/" + name), profile.attempt.resolve("private/" + name));
        E2aPointInTimeCommands.copyTree(profile.attempt.resolve("private-signing-source"),
            profile.attempt.resolve("private-signing"), profile.attempt);
        var before = new E2aMigrationSnapshot(jdbc, profile.attempt, profile.attempt.resolve("storage")).capture();
        profile.migrate(jdbc);
        new E2aMigrationSnapshot(jdbc, profile.attempt, profile.attempt.resolve("storage")).assertPreserved(before);
        Files.writeString(profile.attempt.resolve("legacy-before-upgrade.json"), before.toPrettyString(), StandardOpenOption.CREATE_NEW);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM independent_capture_credential", Integer.class)).isPositive();
    }

    private static void ensureClientConfiguration(E2aCrossSourceProfile profile) throws Exception {
        Path config = profile.attempt.resolve("private/client.cnf");
        String password = System.getenv("E2A_CROSS_PASSWORD");
        if (Files.exists(config)) {
            Map<String, String> values = new HashMap<>();
            for (String line : Files.readAllLines(config)) {
                int separator = line.indexOf('=');
                if (separator > 0) values.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
            }
            assertThat(values.get("user")).isEqualTo(profile.user);
            assertThat(values.get("password")).isEqualTo(password);
            assertThat(values.get("host")).isEqualTo("127.0.0.1");
            assertThat(values.get("port")).isEqualTo(Integer.toString(E2aTestPorts.database()));
            assertThat(values.get("protocol")).isEqualTo("tcp");
            return;
        }
        Files.writeString(config, "[client]\nuser=" + profile.user
            + "\npassword=" + password + "\nhost=127.0.0.1\nport="
            + E2aTestPorts.database() + "\nprotocol=tcp\n", StandardOpenOption.CREATE_NEW);
    }

    private static void definers(E2aCrossSourceProfile profile, JdbcTemplate jdbc) throws Exception {
        var matcher = Pattern.compile("DEFINER=`(e2a_cross_20260925_[0-9]+)`@`127\\.0\\.0\\.1`").matcher(Files.readString(DUMP, java.nio.charset.StandardCharsets.ISO_8859_1));
        var names = new TreeSet<String>(); while (matcher.find()) names.add(matcher.group(1));
        assertThat(names).isNotEmpty();
        for (String name : names) {
            jdbc.execute("CREATE USER '" + name + "'@'127.0.0.1' ACCOUNT LOCK");
            for (String suffix : List.of("", "_missing", "_restore"))
                jdbc.execute("GRANT ALL ON `" + profile.database + suffix + "`.* TO '" + name + "'@'127.0.0.1'");
        }
        Files.writeString(profile.attempt.resolve("legacy-definers.json"),
            com.acrylic.outbound.submissions.SubmissionJson.object().put("lockedAliases", String.join(",", names)).toPrettyString(),
            StandardOpenOption.CREATE_NEW);
    }

    private static void copyStorage(E2aCrossSourceProfile profile) throws Exception {
        try (var existing = Files.list(profile.attempt.resolve("storage"))) { assertThat(existing.toList()).isEmpty(); }
        try (var paths = Files.walk(OLD.resolve("storage"))) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                Path target = profile.attempt.resolve("storage").resolve(OLD.resolve("storage").relativize(path));
                Files.createDirectories(target.getParent()); Files.copy(path, target);
                assertThat(E2aPointInTimeCheckpoint.sha(target)).isEqualTo(E2aPointInTimeCheckpoint.sha(path));
            }
        }
        Path keys = profile.attempt.resolve("private-signing-source"); Files.createDirectory(keys);
        for (String name : List.of("server-private.der", "server-public.der")) Files.copy(OLD.resolve("private/" + name), keys.resolve(name));
    }
}
