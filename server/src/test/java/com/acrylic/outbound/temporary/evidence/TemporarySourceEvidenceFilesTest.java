package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.inventory.ImportFiles;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
import static com.acrylic.outbound.temporary.evidence.SourceEvidenceQueries.*;

class TemporarySourceEvidenceFilesTest {
    @TempDir Path directory;
    private FileRecord stored(byte[] value) throws Exception {
        String hash=ImportFiles.sha256(value);Files.createDirectories(directory.resolve("submissions"));
        Files.write(directory.resolve("submissions/"+hash+".json"),value);
        return new FileRecord(hash,value.length,"submissions/"+hash+".json");
    }
    @Test void readsExactOriginalBytesAndDoesNotCreateMissingFiles() throws Exception {
        byte[] value={0,1,2,3};var record=stored(value);var files=new SourceEvidenceFiles(directory.toString());
        assertThat(files.read(record,262144)).isEqualTo(value);Files.delete(directory.resolve(record.privateKey()));
        assertThatThrownBy(()->files.read(record,262144)).isInstanceOf(BusinessException.class);
        assertThat(Files.exists(directory.resolve(record.privateKey()))).isFalse();
    }
    @Test void incorrectDigestSizeOrPrivatePathIsRefused() throws Exception {
        var record=stored(new byte[]{1,2,3});var files=new SourceEvidenceFiles(directory.toString());
        for(var invalid:List.of(new FileRecord(record.sha256(),2,record.privateKey()),new FileRecord(record.sha256(),3,"../secret"),
            new FileRecord(record.sha256(),33554433,record.privateKey())))
            assertThatThrownBy(()->files.read(invalid,33554432)).isInstanceOf(BusinessException.class);
        Files.write(directory.resolve(record.privateKey()),new byte[]{4,5,6});
        assertThatThrownBy(()->files.read(record,33554432)).isInstanceOf(BusinessException.class);
    }
    @Test void sparseChunkGeometryAllowsMissingBlocksWithoutClaimingAcceptance() {
        var upload=new Upload("a".repeat(64),262161,2);var tail=new Chunk(1,new FileRecord("b".repeat(64),17,"unused"));
        SourceEvidenceArtifacts.geometry(upload,List.of());SourceEvidenceArtifacts.geometry(upload,List.of(tail));
    }
    @Test void invalidChunkGeometryIsRejected() {
        var upload=new Upload("a".repeat(64),262161,2);var tail=new Chunk(1,new FileRecord("b".repeat(64),17,"unused"));
        for(var chunks:List.of(List.of(tail,tail),List.of(new Chunk(2,tail.file())),List.of(new Chunk(0,tail.file()))))
            assertThatThrownBy(()->SourceEvidenceArtifacts.geometry(upload,chunks)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->SourceEvidenceArtifacts.geometry(new Upload(upload.sha256(),upload.bytes(),1),List.of()))
            .isInstanceOf(BusinessException.class);
    }
}
