package com.acrylic.outbound.inventory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ImportFiles {
    private final Path root;
    public ImportFiles(@Value("${outbound.storage}") String root) { this.root = Path.of(root).toAbsolutePath(); }

    public Stored store(byte[] bytes) throws IOException {
        String sha = sha256(bytes);
        Path directory = root.resolve("inventory-imports");
        Files.createDirectories(directory);
        Path target = directory.resolve(sha + ".txt");
        if (!Files.exists(target)) writeNew(target, bytes);
        if (!sha256(Files.readAllBytes(target)).equals(sha)) throw new IOException("Stored file checksum mismatch");
        return new Stored("inventory-imports/" + sha + ".txt", sha);
    }

    public void verify(String sha) throws IOException {
        if (!sha.matches("[a-f0-9]{64}")) throw new IOException("Invalid stored file checksum");
        Path file = root.resolve("inventory-imports").resolve(sha + ".txt");
        if (!Files.isRegularFile(file) || !sha256(Files.readAllBytes(file)).equals(sha))
            throw new com.acrylic.outbound.common.BusinessException(409, "IMPORT_FILE_UNAVAILABLE", "原文件缺失或校验不一致，不能发布");
    }

    private void writeNew(Path target, byte[] bytes) throws IOException {
        Path temporary = Files.createTempFile(target.getParent(), "upload-", ".tmp");
        try {
            try (var file = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) file.write(buffer);
                file.force(true);
            }
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE); }
            catch (IOException collision) {
                if (!Files.isRegularFile(target) || !sha256(Files.readAllBytes(target)).equals(sha256(bytes))) throw collision;
            }
            syncDirectory(target.getParent());
            syncDirectory(root);
        } finally { Files.deleteIfExists(temporary); }
    }

    private void syncDirectory(Path directory) throws IOException {
        // Windows local development cannot fsync directory handles through FileChannel.
        // Production Linux must succeed before the DB is allowed to reference this file.
        if (System.getProperty("os.name").startsWith("Windows")) return;
        try (var channel = FileChannel.open(directory, StandardOpenOption.READ)) { channel.force(true); }
    }

    public static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public record Stored(String key, String sha256) { }
}
