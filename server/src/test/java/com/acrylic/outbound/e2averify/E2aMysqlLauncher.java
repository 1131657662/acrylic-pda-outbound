package com.acrylic.outbound.e2averify;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

/** Opt-in launcher for only the assigned MySQL 19329 instance. */
public final class E2aMysqlLauncher {
    private static final Path MYSQLD = Path.of("C:/Program Files/MySQL/MySQL Server 8.0/bin/mysqld.exe");
    private static final Path MYSQLADMIN = Path.of("C:/Program Files/MySQL/MySQL Server 8.0/bin/mysqladmin.exe");
    private E2aMysqlLauncher() { }

    public static Process start(Map<String, String> env, Path workspace) throws IOException, InterruptedException {
        E2aMigrationEnvironment.requireExact(env, isPortFree(), workspace);
        requireNewDataDir(workspace);
        requireTool(MYSQLD);
        Path data = workspace.resolve(E2aMigrationEnvironment.DATA_DIR).toAbsolutePath().normalize();
        Files.createDirectories(data);
        List<String> command = List.of(MYSQLD.toString(), "--no-defaults", "--initialize-insecure",
            "--datadir=" + data, "--port=" + E2aMigrationEnvironment.PORT,
            "--bind-address=127.0.0.1", "--mysqlx=OFF", "--skip-name-resolve",
            "--skip-networking=false", "--skip-grant-tables=false");
        Path log = privateArtifact(workspace, "mysql-initialize-" + UUID.randomUUID() + ".log");
        Files.createDirectories(log.getParent());
        Process process = new ProcessBuilder(command).directory(workspace.toFile()).redirectErrorStream(true)
            .redirectOutput(log.toFile()).start();
        if (!process.waitFor(90, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("MySQL initialization timed out; see " + log);
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("MySQL initialization failed: " + process.exitValue() + "; see " + log);
        }
        return process;
    }

    public static Process startServer(Map<String, String> env, Path workspace) throws IOException {
        E2aMigrationEnvironment.requireExact(env, isPortFree(), workspace);
        requireInitializedDataDir(workspace);
        requireTool(MYSQLD);
        Path data = workspace.resolve(E2aMigrationEnvironment.DATA_DIR).toAbsolutePath().normalize();
        List<String> command = List.of(MYSQLD.toString(), "--no-defaults", "--datadir=" + data,
            "--port=" + E2aMigrationEnvironment.PORT, "--bind-address=127.0.0.1",
            "--mysqlx=OFF", "--skip-name-resolve", "--skip-networking=false", "--skip-grant-tables=false");
        Path log = privateArtifact(workspace, "mysql-server-" + UUID.randomUUID() + ".log");
        Process process = new ProcessBuilder(command).directory(workspace.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        verifyProcessDataDir(process, data);
        return process;
    }

    public static void shutdown(Process process, Path workspace) throws IOException, InterruptedException {
        if (process == null || !process.isAlive()) return;
        if (!workspace.toAbsolutePath().normalize().equals(Path.of("D:/acrylic-pda-worktrees/r06-e2a-migration").toAbsolutePath().normalize())) {
            throw new IllegalStateException("Unexpected shutdown workspace");
        }
        requireTool(MYSQLADMIN);
        String password = System.getenv("OUTBOUND_DB_PASSWORD");
        if (password == null || password.isBlank()) throw new IllegalStateException("Missing guarded DB password");
        Path defaults = privateArtifact(workspace, "mysqladmin-" + UUID.randomUUID() + ".cnf");
        Files.createDirectories(defaults.getParent());
        Files.writeString(defaults, "[client]\nuser=" + E2aMigrationEnvironment.USER + "\npassword=" + password + "\nhost=127.0.0.1\nport=" + E2aMigrationEnvironment.PORT + "\nprotocol=tcp\n");
        Process admin = new ProcessBuilder(MYSQLADMIN.toString(), "--no-defaults", "--defaults-file=" + defaults, "shutdown")
            .directory(workspace.toFile()).redirectErrorStream(true).start();
        if (!admin.waitFor(30, TimeUnit.SECONDS)) {
            admin.destroyForcibly();
            throw new IllegalStateException("mysqladmin shutdown timed out");
        }
        if (admin.exitValue() != 0) {
            throw new IllegalStateException("mysqladmin shutdown failed: " + admin.exitValue());
        }
        if (!process.waitFor(30, TimeUnit.SECONDS)) throw new IllegalStateException("MySQL process did not exit after authenticated shutdown");
        if (process.isAlive()) throw new IllegalStateException("MySQL process did not exit");
    }

    static boolean isPortFree() {
        try (var socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress("127.0.0.1", E2aMigrationEnvironment.PORT), 250);
            return false;
        } catch (java.io.IOException expected) { return true; }
    }

    private static void verifyProcessDataDir(Process process, Path data) {
        if (!process.isAlive() || !Files.exists(data.resolve("mysqld-auto.cnf"))) {
            throw new IllegalStateException("MySQL process/datadir identity check failed");
        }
        long pid = process.pid();
        try {
            Process probe = new ProcessBuilder("powershell", "-NoProfile", "-Command",
                "(Get-CimInstance Win32_Process -Filter 'ProcessId=" + pid + "').CommandLine")
                .redirectErrorStream(true).start();
            if (!probe.waitFor(5, TimeUnit.SECONDS)) throw new IllegalStateException("PID probe timed out");
            String commandLine = new String(probe.getInputStream().readAllBytes());
            if (!commandLine.contains(data.toString()) || !commandLine.contains("--port=" + E2aMigrationEnvironment.PORT)) {
                throw new IllegalStateException("MySQL PID/datadir/port mismatch");
            }
        } catch (IOException | InterruptedException error) {
            throw new IllegalStateException("PID identity probe failed", error);
        }
    }

    private static void requireNewDataDir(Path workspace) {
        Path data = workspace.resolve(E2aMigrationEnvironment.DATA_DIR).normalize();
        if (!data.startsWith(workspace.toAbsolutePath().normalize()) || Files.exists(data.resolve("ibdata1"))) {
            throw new IllegalStateException("Refusing old or outside E2a datadir");
        }
    }

    private static void requireInitializedDataDir(Path workspace) {
        Path data = workspace.resolve(E2aMigrationEnvironment.DATA_DIR).normalize();
        if (!data.startsWith(workspace.toAbsolutePath().normalize()) || !Files.exists(data.resolve("ibdata1"))) {
            throw new IllegalStateException("Refusing uninitialized or outside E2a datadir");
        }
    }

    private static void requireTool(Path tool) {
        if (!Files.isRegularFile(tool)) throw new IllegalStateException("Missing MySQL tool: " + tool);
    }

    private static Path privateArtifact(Path workspace, String name) throws IOException {
        Path root = workspace.resolve(".local/launcher-private").toAbsolutePath().normalize();
        if (!root.startsWith(workspace.toAbsolutePath().normalize())) throw new IllegalStateException("Private artifact escapes workspace");
        Files.createDirectories(root);
        Path file = root.resolve(name);
        if (Files.exists(file)) throw new IllegalStateException("Refusing artifact overwrite: " + file);
        return file;
    }
}
