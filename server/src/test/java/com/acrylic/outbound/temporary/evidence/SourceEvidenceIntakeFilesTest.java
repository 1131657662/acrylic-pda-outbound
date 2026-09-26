package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.inventory.ImportFiles;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.FileSystemException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceEvidenceIntakeFilesTest {
    @TempDir Path root;

    @Test void storesImmutablePageAndVerifiesExactBytes() throws Exception {
        byte[] bytes="{\"pageNo\":1}".getBytes(StandardCharsets.UTF_8);
        var files=new SourceEvidenceIntakeFiles(root.toString());

        var stored=files.storePage(bytes);

        assertThat(stored.sha256()).isEqualTo(ImportFiles.sha256(bytes));
        assertThat(stored.bytes()).isEqualTo(bytes.length);
        assertThat(Files.readAllBytes(root.resolve(stored.key()))).containsExactly(bytes);
        assertThat(files.readPage(stored)).containsExactly(bytes);
        files.verify(stored);
        assertThat(files.storePage(bytes)).isEqualTo(stored);
    }

    @Test void corruptedTargetIsNeverOverwritten() throws Exception {
        byte[] bytes="{\"pageNo\":1}".getBytes(StandardCharsets.UTF_8);
        var files=new SourceEvidenceIntakeFiles(root.toString());
        var stored=files.storePage(bytes);
        Files.writeString(root.resolve(stored.key()),"corruption");

        assertThatThrownBy(() -> files.storePage(bytes)).isInstanceOf(java.io.IOException.class);
        assertThat(Files.readString(root.resolve(stored.key()))).isEqualTo("corruption");
    }

    @Test void missingFileIsNotAcceptedAsDurable() throws Exception {
        var files=new SourceEvidenceIntakeFiles(root.toString());
        var stored=files.storePage("{}".getBytes(StandardCharsets.UTF_8));
        Files.delete(root.resolve(stored.key()));

        assertThatThrownBy(() -> files.verify(stored)).isInstanceOf(java.io.IOException.class);
    }

    @Test void emptyOrOversizedPageCannotBeStored() {
        var files=new SourceEvidenceIntakeFiles(root.toString());

        assertThatThrownBy(() -> files.storePage(new byte[0])).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> files.storePage(new byte[262145])).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void forgedStorageKeyCannotBeVerified() throws Exception {
        var files=new SourceEvidenceIntakeFiles(root.toString());
        var stored=files.storePage("{}".getBytes(StandardCharsets.UTF_8));
        var forged=new SourceEvidenceIntakeFiles.Stored("../submissions/original.json",stored.sha256(),stored.bytes());

        assertThatThrownBy(() -> files.verify(forged)).isInstanceOf(java.io.IOException.class);
    }

    @Test void directoryInPlaceOfPageIsNeverAccepted() throws Exception {
        var files=new SourceEvidenceIntakeFiles(root.toString());
        byte[] bytes="{}".getBytes(StandardCharsets.UTF_8);
        var target=root.resolve("source-evidence-intake").resolve(ImportFiles.sha256(bytes)+".json");
        Files.createDirectories(target);

        assertThatThrownBy(() -> files.storePage(bytes)).isInstanceOf(java.io.IOException.class);
        assertThat(Files.isDirectory(target)).isTrue();
    }

    @Test void symbolicLinkTargetCannotBeAccepted() throws Exception {
        var files=new SourceEvidenceIntakeFiles(root.toString());
        byte[] bytes="{}".getBytes(StandardCharsets.UTF_8);
        var target=root.resolve("source-evidence-intake").resolve(ImportFiles.sha256(bytes)+".json");
        Files.createDirectories(target.getParent());
        Path other=Files.writeString(root.resolve("other.json"),"{}");
        try { Files.createSymbolicLink(target,other); }
        catch(FileSystemException unavailable) {
            assumeTrue(!System.getProperty("os.name").startsWith("Windows"),"Windows symlink privilege unavailable");
            throw unavailable;
        }

        assertThatThrownBy(() -> files.storePage(bytes)).isInstanceOf(java.io.IOException.class);
    }
}
