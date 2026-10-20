package com.acrylic.outbound.recovery;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** A real disposable JVM leaves a signed synthetic lease after its OS lifecycle lock closes. */
public final class RecoveryOrphanOwnerProcess {
    private RecoveryOrphanOwnerProcess() { }

    static UUID leaveOrphan(FenceTestSupply supply) throws Exception {
        var directory = Files.createTempDirectory(supply.base(), "orphan-owner-");
        var command = command(supply, directory);
        Files.writeString(directory.resolve("command.json"), RecoveryControlFixture.JSON.writeValueAsString(command));
        var process = new ProcessBuilder(command).redirectOutput(directory.resolve("stdout.txt").toFile())
            .redirectError(directory.resolve("stderr.txt").toFile()).start();
        try {
            if (!process.waitFor(20, TimeUnit.SECONDS)) throw new AssertionError("Orphan owner JVM timed out");
            int exit = process.exitValue();
            Files.writeString(directory.resolve("exit.txt"), "pid=" + process.pid() + "|exit=" + exit);
            if (exit != 0) throw new AssertionError("Orphan owner failed: " + Files.readString(directory.resolve("stderr.txt")));
            return UUID.fromString(Files.readString(directory.resolve("operation.txt")).strip());
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                if (!process.waitFor(10, TimeUnit.SECONDS)) throw new AssertionError("Orphan owner JVM still alive");
            }
        }
    }

    private static List<String> command(FenceTestSupply supply, Path directory) throws Exception {
        var paths = RecoveryControlFixture.JSON.readTree(Files.readAllBytes(Path.of("build/recovery-control-classpath.json")));
        var classpath = new ArrayList<String>(); paths.forEach(path -> classpath.add(path.asText()));
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        Path temporary = Files.createDirectory(directory.resolve("tmp"));
        return List.of(Path.of(System.getProperty("java.home"), "bin", executable).toString(), "-Xmx128m",
            "-Dfile.encoding=UTF-8", "-Djava.io.tmpdir=" + temporary, "-cp",
            String.join(java.io.File.pathSeparator, classpath), RecoveryOrphanOwnerProcess.class.getName(),
            supply.config().toString(), directory.resolve("operation.txt").toString());
    }

    public static void main(String[] args) throws Exception {
        var supply = FenceTestSupply.read(Path.of(args[0]));
        var authority = supply.authority(); authority.validateStartup();
        UUID operation = authority.registerOperation();
        Files.writeString(Path.of(args[1]), operation.toString(), StandardOpenOption.CREATE_NEW);
        // Deliberately exit with a still-published operation. The OS releases the owner lock.
        // Calling retireOperation would remove the lease and would not exercise orphan recovery.
    }
}
