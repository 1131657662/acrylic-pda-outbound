package com.acrylic.outbound.reports;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SnapshotJsonTest {
    @TempDir Path directory;
    @Test void roundTripsAnImmutableFixedSnapshot() throws Exception {
        var expected = Samples.report(List.of(Samples.corrected())); var path = directory.resolve("snapshot.json");
        SnapshotJson.write(expected, path);
        assertEquals(expected, SnapshotJson.read(path));
    }
    @Test void rejectsDuplicateJsonFields() throws Exception {
        var path = directory.resolve("duplicate.json"); SnapshotJson.write(Samples.report(List.of()), path);
        String json = Files.readString(path).replace("\"dataVersion\" : \"fixture-v1\"", "\"dataVersion\":\"one\",\"dataVersion\":\"two\"");
        Files.writeString(path, json);
        assertThrows(java.io.IOException.class, () -> SnapshotJson.read(path));
    }
    @Test void rejectsUnknownJsonFields() throws Exception {
        var path = directory.resolve("unknown.json"); SnapshotJson.write(Samples.report(List.of()), path);
        Files.writeString(path, Files.readString(path).replace("\"metadata\"", "\"unknown\":true,\"metadata\""));
        assertThrows(java.io.IOException.class, () -> SnapshotJson.read(path));
    }
    @Test void cliRefusesToOverwriteExistingArtifact() throws Exception {
        var input = directory.resolve("in.json"); var output = directory.resolve("existing.xlsx");
        SnapshotJson.write(Samples.report(List.of()), input); Files.writeString(output, "existing");
        assertThrows(IllegalArgumentException.class, () -> ReportCli.main(new String[]{input.toString(), "xlsx", output.toString()}));
        assertEquals("existing", Files.readString(output));
    }
    @Test void rejectsJsonNumberForPreciseGramString() throws Exception {
        var path = directory.resolve("number.json"); SnapshotJson.write(Samples.report(List.of(Samples.order(1))), path);
        Files.writeString(path, Files.readString(path).replace("\"weightGrams\" : \"300501\"", "\"weightGrams\":300501"));
        assertThrows(java.io.IOException.class, () -> SnapshotJson.read(path));
    }
    @Test void cliDoesNotPublishOnInconsistentSnapshot() throws Exception {
        var path = directory.resolve("inconsistent.json"); SnapshotJson.write(Samples.report(List.of(Samples.order(1))), path);
        Files.writeString(path, Files.readString(path).replace("\"resultVersion\" : 1", "\"resultVersion\":2"));
        var output = directory.resolve("result.xlsx");
        assertThrows(IllegalArgumentException.class, () -> ReportCli.main(new String[]{path.toString(), "xlsx", output.toString()}));
        assertFalse(Files.exists(output));
        try (var files = Files.list(directory)) { assertTrue(files.noneMatch(p -> p.toString().endsWith(".part"))); }
    }
}
