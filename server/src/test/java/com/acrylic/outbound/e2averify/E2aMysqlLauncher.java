package com.acrylic.outbound.e2averify;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import java.nio.file.LinkOption;

/** Opt-in launcher for only the assigned MySQL 19329 instance. */
public final class E2aMysqlLauncher {
    static final Path MYSQLD = Path.of("C:/Program Files/MySQL/MySQL Server 8.0/bin/mysqld.exe");
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
        Throwable failure = null;
        try {
            if (!process.waitFor(90, TimeUnit.SECONDS)) throw new IllegalStateException("MySQL initialization timed out; see " + log);
            if (process.exitValue() != 0) throw new IllegalStateException("MySQL initialization failed: " + process.exitValue() + "; see " + log);
            return process;
        } catch (Exception error) {
            failure = error;
            throw error;
        } finally {
            if (failure != null && process.isAlive()) cleanupInitialization(process, failure, TimeUnit.SECONDS.toNanos(10));
        }
    }

    static void cleanupInitialization(Process process, Throwable failure, long waitNanos) {
        boolean interrupted = Thread.interrupted() || failure instanceof InterruptedException;
        try {
            process.destroy();
            for (int phase = 0; phase < 2 && process.isAlive(); phase++) {
                long deadline = System.nanoTime() + waitNanos;
                while (process.isAlive()) {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) break;
                    try { process.waitFor(remaining, TimeUnit.NANOSECONDS); }
                    catch (InterruptedException error) { interrupted = true; failure.addSuppressed(error); }
                }
                if (phase == 0 && process.isAlive()) process.destroyForcibly();
            }
            if (process.isAlive()) failure.addSuppressed(new IllegalStateException("MySQL initialization process remained after forced cleanup"));
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    public static Process startServer(Map<String, String> env, Path workspace, Path initFile) throws IOException {
        E2aMigrationEnvironment.requireExact(env, isPortFree(), workspace);
        requireInitializedDataDir(workspace);
        requireTool(MYSQLD);
        Path expected = workspace.resolve(E2aMigrationEnvironment.PRIVATE_DIR).resolve("bootstrap-account.sql").toAbsolutePath().normalize();
        if (!initFile.toAbsolutePath().normalize().equals(expected) || !Files.isRegularFile(expected) || Files.isSymbolicLink(expected)) {
            throw new IllegalStateException("Refusing unexpected MySQL init file");
        }
        Path data = workspace.resolve(E2aMigrationEnvironment.DATA_DIR).toAbsolutePath().normalize();
        List<String> command = List.of(MYSQLD.toString(), "--no-defaults", "--datadir=" + data,
            "--port=" + E2aMigrationEnvironment.PORT, "--bind-address=127.0.0.1",
            "--mysqlx=OFF", "--skip-name-resolve", "--skip-networking=false", "--skip-grant-tables=false",
            "--log-bin-trust-function-creators=ON",
            "--init-file=" + expected.toString().replace('\\', '/'));
        Path log = privateArtifact(workspace, "mysql-server-" + UUID.randomUUID() + ".log");
        Process process = new ProcessBuilder(command).directory(workspace.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        return process;
    }

    public static void shutdown(Process process, Path workspace, String user, String password) throws IOException, InterruptedException {
        if (process == null) return;
        if (!workspace.toAbsolutePath().normalize().equals(E2aWorktree.ROOT.toAbsolutePath().normalize())) {
            throw new IllegalStateException("Unexpected shutdown workspace");
        }
        requireTool(MYSQLADMIN);
        Path defaults = privateArtifact(workspace, "mysqladmin-" + UUID.randomUUID() + ".cnf");
        String credentials = password == null ? "" : "password=" + password + "\n";
        Files.writeString(defaults, "[client]\nuser=" + user + "\n" + credentials
            + "host=127.0.0.1\nport=" + E2aMigrationEnvironment.PORT + "\nprotocol=tcp\n",
            java.nio.file.StandardOpenOption.CREATE_NEW);
        Path log = privateArtifact(workspace, "mysqladmin-shutdown-" + UUID.randomUUID() + ".log");
        Process admin = new ProcessBuilder(MYSQLADMIN.toString(), "--defaults-file=" + defaults, "shutdown")
            .directory(workspace.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if (!admin.waitFor(30, TimeUnit.SECONDS)) {
            admin.destroyForcibly();
            throw new IllegalStateException("mysqladmin shutdown timed out");
        }
        if (admin.exitValue() != 0) {
            throw new IllegalStateException("mysqladmin shutdown failed: " + admin.exitValue());
        }
        if (process.isAlive() && !process.waitFor(30, TimeUnit.SECONDS)) throw new IllegalStateException("MySQL process did not exit after authenticated shutdown");
        if (process.isAlive()) throw new IllegalStateException("MySQL process did not exit");
        if (!isPortFree()) throw new IllegalStateException("19329 listener remains after authenticated shutdown");
    }

    static boolean isPortFree() {
        try (var socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress("127.0.0.1", E2aMigrationEnvironment.PORT), 250);
            return false;
        } catch (java.io.IOException expected) { return true; }
    }

    private static void requireNewDataDir(Path workspace) {
        Path data = workspace.resolve(E2aMigrationEnvironment.DATA_DIR).normalize();
        if (!data.startsWith(workspace.toAbsolutePath().normalize()) || Files.exists(data, LinkOption.NOFOLLOW_LINKS)) {
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

    static Path privateArtifact(Path workspace, String name) throws IOException {
        Path root = workspace.resolve(E2aMigrationEnvironment.PRIVATE_DIR).toAbsolutePath().normalize();
        if (!root.startsWith(workspace.toAbsolutePath().normalize())) throw new IllegalStateException("Private artifact escapes workspace");
        rejectLink(root, workspace);
        Files.createDirectories(root);
        try {
            restrictToCurrentUser(root);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("Launcher-private ACL probe interrupted", error);
        }
        Path file = root.resolve(name);
        if (Files.exists(file)) throw new IllegalStateException("Refusing artifact overwrite: " + file);
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) throw new IllegalStateException("Refusing linked artifact: " + file);
        return file;
    }

    private static void rejectLink(Path path, Path workspace) throws IOException {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(path)) throw new IllegalStateException("Refusing linked private directory");
        Path parent = path.getParent();
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && !path.toRealPath().startsWith(workspace.toRealPath())) throw new IllegalStateException("Refusing redirected private directory");
        if (parent != null && !parent.equals(workspace) && !parent.equals(workspace.getRoot())) rejectLink(parent, workspace);
    }

    static void restrictToCurrentUser(Path root) throws IOException, InterruptedException {
        String literal = root.toString().replace("'", "''");
        String command = "$p='" + literal + "'; $u=[Security.Principal.WindowsIdentity]::GetCurrent().Name; "
            + "$a=Get-Acl -LiteralPath $p; if ($a.AreAccessRulesProtected -and $a.Owner -eq $u -and $a.Access.Count -eq 1 -and "
            + "$a.Access[0].IdentityReference.Value -eq $u -and $a.Access[0].AccessControlType -eq 'Allow' -and "
            + "$a.Access[0].FileSystemRights -eq [Security.AccessControl.FileSystemRights]::FullControl) { exit 0 }; "
            + "$a.SetAccessRuleProtection($true,$false); "
            + "$r=New-Object Security.AccessControl.FileSystemAccessRule($u,'FullControl','ContainerInherit,ObjectInherit','None','Allow'); "
            + "$a.SetAccessRule($r); Set-Acl -LiteralPath $p -AclObject $a";
        Process acl = powershell(command).redirectErrorStream(true).start();
        if (!acl.waitFor(5, TimeUnit.SECONDS) || acl.exitValue() != 0) throw new IllegalStateException("Cannot restrict launcher-private ACL");
    }

    static ProcessBuilder powershell(String command) {
        var builder = new ProcessBuilder("powershell", "-NoProfile", "-Command", command);
        builder.environment().keySet().removeIf(name -> name.equalsIgnoreCase("PSModulePath"));
        return builder;
    }

    static void awaitReady(Process process) throws IOException, InterruptedException {
        for (int i = 0; i < 60; i++) {
            if (!process.isAlive()) throw new IllegalStateException("MySQL server exited during startup");
            if (!isPortFree()) return;
            Thread.sleep(500);
        }
        throw new IllegalStateException("MySQL server did not become ready");
    }
}
