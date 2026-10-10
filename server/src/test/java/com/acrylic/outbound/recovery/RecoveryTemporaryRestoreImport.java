package com.acrylic.outbound.recovery;

import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Imports an immutable checkpoint into the authenticated new empty schema; never migrates or repairs the dump. */
final class RecoveryTemporaryRestoreImport {
    static void restore(JdbcTemplate jdbc,Path attempt,Path original) throws Exception {
        Path root = Path.of("../.local").toRealPath(); Path source = original.toRealPath();
        assertThat(source.getParent()).isEqualTo(root); assertThat(source).isNotEqualTo(attempt.toRealPath());
        assertThat(source.getFileName().toString()).matches("e2a-cross-20260925-[1-9][0-9]{0,3}");
        assertThat(Files.exists(source.resolve("test-completed.marker"))).isTrue();
        Path dump = source.resolve("restore-checkpoint.sql"); String pin = Files.readString(source.resolve("restore-checkpoint.sha256")).trim();
        assertThat(sha256(Files.readAllBytes(dump))).isEqualTo(pin);
        String schema = jdbc.queryForObject("SELECT DATABASE()",String.class);
        var process = new ProcessBuilder("C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe",
            "--defaults-file="+attempt.resolve("private/root-client.cnf"),"--default-character-set=utf8mb4",schema)
            .redirectInput(dump.toFile()).redirectOutput(attempt.resolve("restore-import.out").toFile())
            .redirectError(attempt.resolve("restore-import.err").toFile()).start();
        assertThat(process.waitFor(30,TimeUnit.SECONDS)).isTrue(); assertThat(process.exitValue()).isZero();
        assertThat(sha256(Files.readAllBytes(dump))).isEqualTo(pin);
        try(var files = Files.list(source.resolve("storage"))) {
            for(Path file:files.toList()) Files.copy(file,attempt.resolve("storage").resolve(file.getFileName()));
        }
        Files.writeString(attempt.resolve("restore-input-dump.sha256"),pin,StandardOpenOption.CREATE_NEW);
    }
}
