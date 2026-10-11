package com.acrylic.outbound.recovery;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

class RecoveryRecipientDownloadMaterialTest {
    @Test void rejectsChangedConfiguredFile() throws Exception {
        var file=file(); var material=new RecoveryRecipientDownloadMaterial(file,sha256(Files.readAllBytes(file)));
        assertThat(material.read(32)).isEqualTo(Files.readAllBytes(file)); Files.writeString(file,"changed");
        assertThatThrownBy(()->material.read(32)).hasMessage("RECOVERY_DOWNLOAD_INPUT_PIN_MISMATCH");
    }
    @Test void rejectsFileWithMultipleHardLinks() throws Exception {
        var file=file(); var material=new RecoveryRecipientDownloadMaterial(file,sha256(Files.readAllBytes(file)));
        Files.createLink(file.resolveSibling("alias.json"),file);
        assertThatThrownBy(()->material.read(32)).hasMessage("CONTROL_FILE_ALIAS");
    }
    @Test void rejectsAbsolutePathAlias() throws Exception {
        var file=file(); var path=file.getParent().resolve("..").resolve(file.getParent().getFileName()).resolve(file.getFileName());
        var material=new RecoveryRecipientDownloadMaterial(path,sha256(Files.readAllBytes(file)));
        assertThatThrownBy(()->material.read(32)).hasMessage("CONTROL_PATH_ALIAS");
    }
    @Test void rejectsRelativePath() {
        var material=new RecoveryRecipientDownloadMaterial(Path.of("input.json"),"a".repeat(64));
        assertThatThrownBy(()->material.read(32)).hasMessage("CONTROL_ABSOLUTE_PATH_REQUIRED");
    }
    @Test void rejectsOversizedFileBeforeDigest() throws Exception {
        var file=file(); var material=new RecoveryRecipientDownloadMaterial(file,sha256(Files.readAllBytes(file)));
        assertThatThrownBy(()->material.read(2)).hasMessage("RECOVERY_DOWNLOAD_INPUT_SIZE");
    }
    private Path file() throws Exception {
        var root=Files.createTempDirectory(Path.of(System.getProperty("java.io.tmpdir")),"download-input-");
        return Files.writeString(root.resolve("input.json"),"retained");
    }
}
