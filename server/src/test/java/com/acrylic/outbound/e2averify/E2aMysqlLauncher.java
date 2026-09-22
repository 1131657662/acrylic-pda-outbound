package com.acrylic.outbound.e2averify;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Opt-in launcher for only the assigned MySQL 19329 instance. */
public final class E2aMysqlLauncher {
    private static final Path MYSQLD = Path.of("C:/Program Files/MySQL/MySQL Server 8.0/bin/mysqld.exe");
    private static final Path MYSQLADMIN = Path.of("C:/Program Files/MySQL/MySQL Server 8.0/bin/mysqladmin.exe");
    private E2aMysqlLauncher() { }

    public static Process start(Path workspace) throws IOException {
        requireNewDataDir(workspace);
        Path data = workspace.resolve(E2aMigrationEnvironment.DATA_DIR).toAbsolutePath().normalize();
        Files.createDirectories(data);
        List<String> command = List.of(MYSQLD.toString(), "--datadir=" + data,
            "--port=" + E2aMigrationEnvironment.PORT, "--bind-address=127.0.0.1",
            "--mysqlx=OFF", "--skip-networking=false", "--skip-grant-tables=false");
        return new ProcessBuilder(command).directory(workspace.toFile()).redirectErrorStream(true).start();
    }

    public static void shutdown(Process process, Path workspace) throws IOException, InterruptedException {
        if (process == null || !process.isAlive()) return;
        new ProcessBuilder(MYSQLADMIN.toString(), "--host=127.0.0.1", "--port=" + E2aMigrationEnvironment.PORT,
            "--protocol=tcp", "--shutdown").directory(workspace.toFile()).inheritIO().start().waitFor();
        process.waitFor();
    }

    private static void requireNewDataDir(Path workspace) {
        Path data = workspace.resolve(E2aMigrationEnvironment.DATA_DIR).normalize();
        if (!data.startsWith(workspace.toAbsolutePath().normalize()) || Files.exists(data.resolve("ibdata1"))) {
            throw new IllegalStateException("Refusing old or outside E2a datadir");
        }
    }
}
