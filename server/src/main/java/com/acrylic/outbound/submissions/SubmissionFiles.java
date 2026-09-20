package com.acrylic.outbound.submissions;

import com.acrylic.outbound.inventory.ImportFiles;
import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SubmissionFiles {
    private final Path root;
    public SubmissionFiles(@Value("${outbound.storage}") String directory) { root=Path.of(directory).toAbsolutePath().resolve("submissions"); }
    public String store(byte[] bytes) {
        String hash=ImportFiles.sha256(bytes);
        try {
            Files.createDirectories(root);Path target=root.resolve(hash+".json");
            if(!Files.exists(target)) write(target,bytes);
            if(!ImportFiles.sha256(Files.readAllBytes(target)).equals(hash)) throw new IOException("Archive digest mismatch");
            return hash;
        } catch(IOException error) { throw new java.io.UncheckedIOException("Cannot persist submission evidence",error); }
    }
    public byte[] read(String hash) {
        SubmissionJson.require(hash.matches("[a-f0-9]{64}"),"正文文件摘要无效");
        try {
            byte[] bytes=Files.readAllBytes(root.resolve(hash+".json"));
            if(!ImportFiles.sha256(bytes).equals(hash)) throw new IOException("Archive digest mismatch");return bytes;
        } catch(IOException error) { throw new java.io.UncheckedIOException("Cannot read submission evidence",error); }
    }
    /** Reassert durability before issuing a separate long-term archive confirmation. */
    public byte[] confirm(String hash) {
        byte[] bytes=read(hash);
        try {
            try(var channel=FileChannel.open(root.resolve(hash+".json"),StandardOpenOption.WRITE)) { channel.force(true); }
            if(!System.getProperty("os.name").startsWith("Windows")) {
                try(var channel=FileChannel.open(root,StandardOpenOption.READ)) { channel.force(true); }
                try(var channel=FileChannel.open(root.getParent(),StandardOpenOption.READ)) { channel.force(true); }
            }
            return bytes;
        } catch(IOException error) { throw new UncheckedIOException("Cannot confirm archive durability",error); }
    }
    private void write(Path target,byte[] bytes) throws IOException {
        Path temporary=Files.createTempFile(root,"receiving-",".tmp");
        try {
            try(var channel=FileChannel.open(temporary,StandardOpenOption.WRITE)) {
                var buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining()) channel.write(buffer);channel.force(true);
            }
            try { Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE); }
            catch(IOException collision) { if(!Files.isRegularFile(target) || !ImportFiles.sha256(Files.readAllBytes(target)).equals(ImportFiles.sha256(bytes))) throw collision; }
            if(!System.getProperty("os.name").startsWith("Windows")) {
                try(var channel=FileChannel.open(root,StandardOpenOption.READ)) { channel.force(true); }
                try(var channel=FileChannel.open(root.getParent(),StandardOpenOption.READ)) { channel.force(true); }
            }
        } finally { Files.deleteIfExists(temporary); }
    }
}
