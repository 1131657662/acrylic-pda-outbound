package com.acrylic.outbound.relay;

import com.acrylic.outbound.inventory.ImportFiles;
import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RelayFiles {
    private final Path root;
    public RelayFiles(@Value("${outbound.storage}") String path) { root=Path.of(path).toAbsolutePath().resolve("original-relays"); }
    public void store(byte[] bytes) {
        String hash=ImportFiles.sha256(bytes);
        try {
            Files.createDirectories(root);Path target=root.resolve(hash+".zip");
            if(!Files.exists(target))write(target,bytes);
            if(!ImportFiles.sha256(Files.readAllBytes(target)).equals(hash))throw new IOException("Original relay checksum mismatch");
        } catch(IOException error) { throw new UncheckedIOException("Cannot persist relay original",error); }
    }
    public byte[] read(String hash) {
        if(!hash.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid archive hash");
        try {
            Path path=root.resolve(hash+".zip");
            if(Files.size(path)>RelayArchive.MAX_BYTES)throw new IOException("Original archive too large");
            byte[] bytes=Files.readAllBytes(path);
            if(!ImportFiles.sha256(bytes).equals(hash))throw new IOException("Original relay checksum mismatch");return bytes;
        } catch(IOException error) { throw new UncheckedIOException("Cannot read relay original",error); }
    }
    private void write(Path target,byte[] bytes) throws IOException {
        Path temporary=Files.createTempFile(root,"relay-",".tmp");
        try {
            try(var channel=FileChannel.open(temporary,StandardOpenOption.WRITE)) {
                var buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);
            }
            try { Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE); }
            catch(IOException collision) { if(!Files.isRegularFile(target)||!ImportFiles.sha256(Files.readAllBytes(target)).equals(ImportFiles.sha256(bytes)))throw collision; }
            if(!System.getProperty("os.name").startsWith("Windows")) {
                try(var directory=FileChannel.open(root,StandardOpenOption.READ)) { directory.force(true); }
                try(var parent=FileChannel.open(root.getParent(),StandardOpenOption.READ)) { parent.force(true); }
            }
        } finally { Files.deleteIfExists(temporary); }
    }
}
