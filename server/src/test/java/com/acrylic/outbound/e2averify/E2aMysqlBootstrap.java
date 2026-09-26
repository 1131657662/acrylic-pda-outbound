package com.acrylic.outbound.e2averify;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.HexFormat;

final class E2aMysqlBootstrap {
    private E2aMysqlBootstrap() { }

    static String newPassword() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    static Path write(Path workspace, String password) throws IOException {
        if (password == null || !password.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid generated E2a password");
        }
        Path file = E2aMysqlLauncher.privateArtifact(workspace, "bootstrap-account.sql");
        Files.writeString(file, sql(password), StandardOpenOption.CREATE_NEW);
        return file;
    }

    static String sql(String password) {
        return "CREATE DATABASE `" + E2aMigrationEnvironment.DATABASE + "`;\n"
            + "CREATE USER '" + E2aMigrationEnvironment.USER + "'@'127.0.0.1' IDENTIFIED BY '" + password + "';\n"
            + "GRANT ALL ON `" + E2aMigrationEnvironment.DATABASE + "`.* TO '" + E2aMigrationEnvironment.USER + "'@'127.0.0.1';\n"
            + "GRANT SHUTDOWN ON *.* TO '" + E2aMigrationEnvironment.USER + "'@'127.0.0.1';\n";
    }
}
