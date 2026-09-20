package com.acrylic.outbound.exports;

import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.reports.*;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ExportFiles {
    private static final long MAX_BYTES=64L*1024*1024;
    private final Path root;
    public ExportFiles(@Value("${outbound.storage}") String storage) {
        root=Path.of(storage).toAbsolutePath().normalize().resolve("exports");
    }
    public String store(ReportSnapshot snapshot) throws IOException {
        Path directory=root.resolve("snapshots");Files.createDirectories(directory);
        Path temporary=Files.createTempFile(directory,"snapshot-",".tmp");
        try {
            SnapshotJson.write(snapshot,temporary);force(temporary);
            String hash=digest(temporary);Path target=snapshot(hash);
            try { Files.move(temporary,target); }
            catch(FileAlreadyExistsException collision) { verify(target,hash); }
            force(target);
            return hash;
        } finally { Files.deleteIfExists(temporary); }
    }
    public Path snapshot(String hash) {
        if(hash==null || !hash.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid snapshot hash");
        return root.resolve("snapshots").resolve(hash+".json");
    }
    public Path attempt(UUID job,UUID token) { return root.resolve("attempts").resolve(job.toString()).resolve(token.toString()); }
    public Path artifact(UUID job,UUID token,ExportModels.Format format) { return attempt(job,token).resolve("report."+format.extension); }
    public String digest(Path file) throws IOException { return ImportFiles.sha256(boundedRead(file)); }
    public byte[] verified(Path file,String hash,long size) throws IOException {
        byte[] bytes=boundedRead(file);
        if(bytes.length!=size || !ImportFiles.sha256(bytes).equals(hash)) throw new IOException("Export integrity mismatch");
        return bytes;
    }
    public void verify(Path file,String hash) throws IOException {
        if(!digest(file).equals(hash)) throw new IOException("Export integrity mismatch");
    }
    public void force(Path file) throws IOException {
        try(var channel=FileChannel.open(file,StandardOpenOption.WRITE)) { channel.force(true); }
        if(!System.getProperty("os.name").startsWith("Windows"))
            try(var channel=FileChannel.open(file.getParent(),StandardOpenOption.READ)) { channel.force(true); }
    }
    private byte[] boundedRead(Path path) throws IOException {
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS) || Files.size(path)>MAX_BYTES) throw new IOException("Export unavailable");
        try(var input=Files.newInputStream(path)) {
            byte[] bytes=input.readNBytes((int)MAX_BYTES+1);
            if(bytes.length>MAX_BYTES) throw new IOException("Export too large");return bytes;
        }
    }
}
