package com.acrylic.outbound.e2averify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class E2aSnapshotFilesTest {
    @Test
    void capturesFileBytesAndDigestInsideStorageRoot() throws Exception {
        Path workspace = Files.createTempDirectory("e2a-snapshot-workspace-");
        Path storage = workspace.resolve(".local/private-files");
        Files.createDirectories(storage);
        Path file = storage.resolve("sample-" + UUID.randomUUID() + ".txt");
        Files.writeString(file, "snapshot");

        var result = E2aSnapshotFiles.capture(storage, workspace);

        assertThat(result.path(file.getFileName().toString()).path("bytes").asInt()).isEqualTo(8);
        assertThat(result.path(file.getFileName().toString()).path("sha256").asText()).hasSize(64);
    }

    @Test
    void rejectsStorageSymlinkOutsideWorkspace() throws Exception {
        Path workspace = Files.createTempDirectory("e2a-snapshot-workspace-");
        Path outside = Files.createTempDirectory("e2a-snapshot-outside-");
        Path storage = workspace.resolve(".local/private-files");
        Files.createDirectories(storage);
        Path target = outside.resolve("outside.txt");
        Files.writeString(target, "outside");
        Path link = storage.resolve("linked.txt");
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | java.io.IOException error) {
            return;
        }

        assertThatThrownBy(() -> E2aSnapshotFiles.capture(storage, workspace))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Storage link");
    }
}
