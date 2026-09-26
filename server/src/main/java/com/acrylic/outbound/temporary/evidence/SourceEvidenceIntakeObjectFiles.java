package com.acrylic.outbound.temporary.evidence;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SourceEvidenceIntakeObjectFiles {
    private static final int OBJECT_LIMIT=33554432;
    private final Path root;

    public SourceEvidenceIntakeObjectFiles(@Value("${outbound.storage}") String directory) {
        root=Path.of(directory).toAbsolutePath().normalize();
    }

    public Stored store(InputStream input,int length,String expectedSha256) throws IOException {
        if(input==null || length<1 || length>OBJECT_LIMIT || expectedSha256==null
            || !expectedSha256.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("OBJECT_INPUT");
        Path directory=objectDirectory();
        Path target=directory.resolve(expectedSha256+".bin");
        Path temporary=Files.createTempFile(directory,"object-",".tmp");
        try {
            write(input,length,expectedSha256,temporary);
            try { Files.createLink(target,temporary); }
            catch(java.nio.file.FileAlreadyExistsException collision) { verify(target,length,expectedSha256); }
        } finally { Files.deleteIfExists(temporary); }
        sync(directory);
        verify(target,length,expectedSha256);
        return new Stored("source-evidence-intake/objects/"+expectedSha256+".bin",expectedSha256,length);
    }

    public void verify(Stored stored) throws IOException {
        if(stored==null || stored.bytes()<1 || stored.bytes()>OBJECT_LIMIT || stored.sha256()==null
            || !stored.sha256().matches("[0-9a-f]{64}") || stored.key()==null
            || !stored.key().equals("source-evidence-intake/objects/"+stored.sha256()+".bin"))
            throw new IOException("OBJECT_FILE_IDENTITY");
        verify(objectDirectory().resolve(stored.sha256()+".bin"),stored.bytes(),stored.sha256());
    }

    public byte[] read(Stored stored) throws IOException {
        verify(stored);
        Path target=objectDirectory().resolve(stored.sha256()+".bin");
        try(var channel=FileChannel.open(target,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)) {
            if(channel.size()!=stored.bytes())throw new IOException("OBJECT_FILE_CORRUPT");
            var buffer=ByteBuffer.allocate(stored.bytes());
            while(buffer.hasRemaining()) {
                if(channel.read(buffer)<0)throw new IOException("OBJECT_FILE_CORRUPT");
            }
            if(channel.read(ByteBuffer.allocate(1))!=-1)throw new IOException("OBJECT_FILE_CORRUPT");
            byte[] bytes=buffer.array();
            if(!HexFormat.of().formatHex(sha256().digest(bytes)).equals(stored.sha256()))
                throw new IOException("OBJECT_FILE_CORRUPT");
            return bytes;
        }
    }

    private Path objectDirectory() throws IOException {
        requireDirectory(root);
        Path intake=root.resolve("source-evidence-intake");
        Files.createDirectories(intake);
        requireDirectory(intake);
        Path directory=intake.resolve("objects");
        Files.createDirectories(directory);
        requireDirectory(directory);
        return directory;
    }

    private void write(InputStream input,int length,String expected,Path temporary) throws IOException {
        MessageDigest digest=sha256();
        byte[] block=new byte[65536];
        int remaining=length;
        try(var channel=FileChannel.open(temporary,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)) {
            while(remaining>0) {
                int count=input.read(block,0,Math.min(block.length,remaining));
                if(count<1)throw new IOException("OBJECT_STREAM_TRUNCATED");
                digest.update(block,0,count);
                var buffer=ByteBuffer.wrap(block,0,count);
                while(buffer.hasRemaining())channel.write(buffer);
                remaining-=count;
            }
            if(input.read()!=-1)throw new IOException("OBJECT_STREAM_TOO_LONG");
            if(!HexFormat.of().formatHex(digest.digest()).equals(expected))throw new IOException("OBJECT_HASH_MISMATCH");
            channel.force(true);
        }
    }

    private void verify(Path target,int length,String expected) throws IOException {
        if(!Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS) || Files.size(target)!=length)
            throw new IOException("OBJECT_FILE_CORRUPT");
        MessageDigest digest=sha256();
        try(var channel=FileChannel.open(target,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)) {
            var buffer=ByteBuffer.allocate(65536);
            while(channel.read(buffer)!=-1) {
                buffer.flip();
                digest.update(buffer);
                buffer.clear();
            }
        }
        if(!HexFormat.of().formatHex(digest.digest()).equals(expected))throw new IOException("OBJECT_FILE_CORRUPT");
    }

    private void requireDirectory(Path directory) throws IOException {
        if(Files.isSymbolicLink(directory) || !Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("OBJECT_FILE_DIRECTORY");
    }

    private void sync(Path directory) throws IOException {
        if(System.getProperty("os.name").startsWith("Windows"))return;
        for(Path parent=directory;parent!=null && parent.startsWith(root);parent=parent.getParent()) {
            try(var channel=FileChannel.open(parent,StandardOpenOption.READ)) { channel.force(true); }
        }
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch(NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    public record Stored(String key,String sha256,int bytes) { }
}
