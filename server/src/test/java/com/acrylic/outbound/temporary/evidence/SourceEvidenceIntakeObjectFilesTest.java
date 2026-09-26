package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.inventory.ImportFiles;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceEvidenceIntakeObjectFilesTest {
    @TempDir Path root;

    @Test void storesAndVerifiesRawObjectWithoutChangingOriginalBytes() throws Exception {
        byte[] raw="\u0000original\r\nrecord".getBytes(StandardCharsets.UTF_8);
        var files=new SourceEvidenceIntakeObjectFiles(root.toString());

        var stored=files.store(new ByteArrayInputStream(raw),raw.length,ImportFiles.sha256(raw));

        assertThat(Files.readAllBytes(root.resolve(stored.key()))).containsExactly(raw);
        assertThat(files.store(new ByteArrayInputStream(raw),raw.length,stored.sha256())).isEqualTo(stored);
        files.verify(stored);
        assertThat(files.read(stored)).containsExactly(raw);
    }

    @Test void rejectsTruncatedOverlongOrMismatchedBytesWithoutPublishing() throws Exception {
        byte[] raw="raw".getBytes(StandardCharsets.UTF_8);
        var files=new SourceEvidenceIntakeObjectFiles(root.toString());
        String digest=ImportFiles.sha256(raw);

        assertThatThrownBy(() -> files.store(new ByteArrayInputStream(raw),raw.length+1,digest)).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> files.store(new ByteArrayInputStream(raw),raw.length-1,digest)).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> files.store(new ByteArrayInputStream(raw),raw.length,"0".repeat(64)))
            .isInstanceOf(IOException.class);
        try(var entries=Files.list(root.resolve("source-evidence-intake/objects"))) {
            assertThat(entries.count()).isZero();
        }
    }

    @Test void rejectsOversizedObjectBeforeReadingInput() {
        var files=new SourceEvidenceIntakeObjectFiles(root.toString());

        assertThatThrownBy(() -> files.store(new ByteArrayInputStream(new byte[0]),33554433,"0".repeat(64)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> files.store(new ByteArrayInputStream(new byte[0]),0,"0".repeat(64)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void refusesCorruptedTargetAndForgedStorageKey() throws Exception {
        byte[] raw="original".getBytes(StandardCharsets.UTF_8);
        var files=new SourceEvidenceIntakeObjectFiles(root.toString());
        var stored=files.store(new ByteArrayInputStream(raw),raw.length,ImportFiles.sha256(raw));
        Files.writeString(root.resolve(stored.key()),"changed!");

        assertThatThrownBy(() -> files.store(new ByteArrayInputStream(raw),raw.length,stored.sha256()))
            .isInstanceOf(IOException.class);
        assertThatThrownBy(() -> files.verify(stored)).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> files.read(stored)).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> files.verify(new SourceEvidenceIntakeObjectFiles.Stored(
            "../outside",stored.sha256(),stored.bytes()))).isInstanceOf(IOException.class);
        assertThat(Files.readString(root.resolve(stored.key()))).isEqualTo("changed!");
    }
}
