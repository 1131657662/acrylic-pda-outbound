package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.inventory.ImportFiles;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SourceEvidenceIntakeFiles {
    private static final int PAGE_LIMIT=262144;
    private final Path root;

    public SourceEvidenceIntakeFiles(@Value("${outbound.storage}") String directory) {
        root=Path.of(directory).toAbsolutePath().normalize();
    }

    public Stored storePage(byte[] bytes) throws IOException {
        if(bytes==null || bytes.length==0 || bytes.length>PAGE_LIMIT)throw new IllegalArgumentException("PAGE_FILE_SIZE");
        String digest=ImportFiles.sha256(bytes);
        Path directory=root.resolve("source-evidence-intake");
        requireDirectory(root);
        Files.createDirectories(directory);
        requireDirectory(directory);
        Path target=directory.resolve(digest+".json");
        if(Files.exists(target,LinkOption.NOFOLLOW_LINKS)) {
            verify(target,bytes.length,digest);
        } else {
            create(target,bytes,digest);
        }
        syncDirectory(directory);
        verify(target,bytes.length,digest);
        return new Stored("source-evidence-intake/"+digest+".json",digest,bytes.length);
    }

    public void verify(Stored stored) throws IOException {
        readPage(stored);
    }

    public byte[] readPage(Stored stored) throws IOException {
        if(stored==null || stored.key()==null || stored.bytes()<1 || stored.bytes()>PAGE_LIMIT
            || stored.sha256()==null || !stored.sha256().matches("[0-9a-f]{64}")
            || !stored.key().equals("source-evidence-intake/"+stored.sha256()+".json"))
            throw new IOException("PAGE_FILE_IDENTITY");
        requireDirectory(root);
        Path directory=root.resolve("source-evidence-intake");
        requireDirectory(directory);
        return verify(directory.resolve(stored.sha256()+".json"),stored.bytes(),stored.sha256());
    }

    private void create(Path target,byte[] bytes,String digest) throws IOException {
        Path temporary=Files.createTempFile(target.getParent(),"page-",".tmp");
        try {
            try(var channel=FileChannel.open(temporary,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)) {
                var buffer=ByteBuffer.wrap(bytes);
                while(buffer.hasRemaining())channel.write(buffer);
                channel.force(true);
            }
            try { Files.createLink(target,temporary); }
            catch(java.nio.file.FileAlreadyExistsException collision) { verify(target,bytes.length,digest); }
        } finally { Files.deleteIfExists(temporary); }
    }

    private byte[] verify(Path target,int length,String digest) throws IOException {
        if(!Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS) || Files.size(target)!=length)
            throw new IOException("PAGE_FILE_CORRUPT");
        byte[] actual;
        try(var channel=FileChannel.open(target,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)) {
            var buffer=ByteBuffer.allocate(length+1);
            while(buffer.hasRemaining() && channel.read(buffer)>0) { }
            buffer.flip();actual=new byte[buffer.remaining()];buffer.get(actual);
        }
        if(actual.length!=length || !ImportFiles.sha256(actual).equals(digest))throw new IOException("PAGE_FILE_CORRUPT");
        return actual;
    }

    private void requireDirectory(Path directory) throws IOException {
        if(Files.isSymbolicLink(directory) || !Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("PAGE_FILE_DIRECTORY");
    }

    private void syncDirectory(Path directory) throws IOException {
        if(System.getProperty("os.name").startsWith("Windows"))return;
        try(var channel=FileChannel.open(directory,StandardOpenOption.READ)) { channel.force(true); }
        try(var channel=FileChannel.open(root,StandardOpenOption.READ)) { channel.force(true); }
    }

    public record Stored(String key,String sha256,int bytes) { }
}
