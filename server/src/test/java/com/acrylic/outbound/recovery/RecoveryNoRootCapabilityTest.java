package com.acrylic.outbound.recovery;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The no-root capability inventory of the design's row 22: the raw migration pool is reachable only
 * from the frozen capability set, and an admitted instance covers its startup database access with
 * the explicit initialization window rather than with a per-bean bypass.
 */
class RecoveryNoRootCapabilityTest {
    /** Files allowed to name the raw pool: its definition (and the primary source that wraps it). */
    private static final Set<String> RAW_POOL_ALLOWED = Set.of("RecoveryAdmissionConfiguration.java");

    @Test void theRawMigrationPoolIsReachableOnlyFromTheFrozenCapabilitySet() throws Exception {
        var offenders = new ArrayList<String>();
        for (Path file : javaSources("src/main/java")) {
            if (Files.readString(file).contains("MIGRATION_DATASOURCE")
                && !RAW_POOL_ALLOWED.contains(file.getFileName().toString())) {
                offenders.add(file.toString());
            }
        }
        assertThat(offenders).as("a new raw-pool reader must be added to the frozen list deliberately")
            .isEmpty();
    }

    @Test void theRawPoolIsTheFlywayOnlyPoolAndTheNoRootStartupWindowIsExplicit() throws Exception {
        int flywayQualifiers = 0;
        for (Path file : javaSources("src/main/java")) {
            flywayQualifiers += occurrences(Files.readString(file), "@FlywayDataSource");
        }
        assertThat(flywayQualifiers).as("exactly one pool serves migration and bootstrap").isEqualTo(1);
        assertThat(Files.readString(Path.of("src/main/java/com/acrylic/outbound/recovery/RecoveryStartupGuard.java")))
            .as("startup access is covered by an explicit window, not by an implicit bypass")
            .contains("CONTROL_SESSION_CLEANUP_UNCOVERED");
        assertThat(Path.of("src/main/java/com/acrylic/outbound/recovery/RecoveryInitializationWindow.java"))
            .exists();
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + needle.length())) count++;
        return count;
    }

    private static List<Path> javaSources(String root) throws Exception {
        try (var files = Files.walk(Path.of(root))) {
            return files.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
    }
}
