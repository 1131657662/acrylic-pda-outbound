package com.acrylic.outbound.e2averify;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;

/** Protected MySQL CLI transport for the one-shot checkpoint test. */
final class E2aPointInTimeCommands {
    private static final Path BIN = Path.of("C:/Program Files/MySQL/MySQL Server 8.0/bin");
    private final E2aCrossSourceProfile profile;
    E2aPointInTimeCommands(E2aCrossSourceProfile profile) { this.profile = profile; }

    void dump(String schema, Path destination) throws Exception {
        var args = mysql("mysqldump");
        args.addAll(List.of("--single-transaction", "--skip-lock-tables", "--no-tablespaces", "--set-gtid-purged=OFF",
            "--hex-blob", "--routines", "--triggers", "--events", "--skip-add-drop-table", schema));
        run(args, destination, null);
        assertThat(Files.size(destination)).isPositive();
    }

    void restore(String schema, Path input, Path output) throws Exception {
        var args = mysql("mysql");
        args.addAll(List.of("--binary-mode", "--default-character-set=utf8mb4", schema));
        run(args, output, input);
    }

    void decode(String target, Path binlog, long begin, long end, Path output) throws Exception {
        assertThat(begin).isLessThanOrEqualTo(end);
        run(new ArrayList<>(List.of(BIN.resolve("mysqlbinlog.exe").toString(), "--no-defaults",
            "--verify-binlog-checksum", "--start-position=" + begin, "--stop-position=" + end,
            "--rewrite-db=" + profile.database + "->" + target, "--database=" + target,
            binlog.toString())), output, null);
    }

    static void copyTree(Path source, Path destination, Path workspace) throws Exception {
        var expected = E2aSnapshotFiles.capture(source, workspace);
        Files.createDirectory(destination);
        var names = expected.fieldNames();
        while (names.hasNext()) {
            String name = names.next(); Path target = destination.resolve(name).normalize();
            assertThat(target.startsWith(destination)).isTrue();
            Files.createDirectories(target.getParent()); Files.copy(source.resolve(name), target);
        }
        assertThat(E2aSnapshotFiles.capture(destination, workspace)).isEqualTo(expected);
    }

    private List<String> mysql(String executable) {
        return new ArrayList<>(List.of(BIN.resolve(executable + ".exe").toString(),
            "--defaults-file=" + profile.attempt.resolve("private/client.cnf"),
            "--protocol=TCP", "--host=127.0.0.1", "--port=19330"));
    }

    private void run(List<String> args, Path output, Path input) throws Exception {
        assertThat(Files.exists(output)).isFalse();
        var builder = new ProcessBuilder(args);
        builder.environment().remove("MYSQL_PWD");
        builder.environment().put("MYSQL_TEST_LOGIN_FILE", profile.attempt.resolve("private/no-login-file").toString());
        builder.redirectOutput(output.toFile()).redirectError(Path.of(output + ".stderr").toFile());
        if (input != null) builder.redirectInput(input.toFile());
        var process = builder.start();
        if (!process.waitFor(60, TimeUnit.SECONDS)) { process.destroyForcibly(); fail("Checkpoint CLI timeout: " + output); }
        assertThat(process.exitValue()).as("checkpoint CLI; see protected stderr for %s", output).isZero();
    }
}
