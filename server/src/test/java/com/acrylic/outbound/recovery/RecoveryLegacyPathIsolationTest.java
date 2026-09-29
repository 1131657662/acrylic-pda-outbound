package com.acrylic.outbound.recovery;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * S2 static whitelist: only the request-root data source may be primary, the application's main
 * sources may reference the legacy connection-level adapter only where it is defined, and the one
 * frozen test-only primary is a harness that never shares a context with the application entry.
 */
class RecoveryLegacyPathIsolationTest {
    /** The application entry file that owns the single primary data source. */
    private static final String APPLICATION_ENTRY = "RecoveryAdmissionConfiguration.java";
    /**
     * The fault-injection harness for a lost commit acknowledgement. It is deliberately the primary
     * entry of its own context, and that context never enables {@code outbound.recovery.admission},
     * so the application's primary data source is not present in it.
     */
    private static final Set<String> TEST_PRIMARY_WHITELIST = Set.of("RecoveryEntryLostCommitConfiguration.java");
    /** This scanner necessarily spells the annotation out; it is not a bean definition. */
    private static final String SCANNER = "RecoveryLegacyPathIsolationTest.java";

    @Test void onlyTheRequestRootDataSourceIsPrimary() throws Exception {
        var sites = new ArrayList<String>();
        for (Path file : javaSources("src/main/java")) {
            String text = Files.readString(file);
            int at = text.indexOf("@Primary");
            if (at < 0) continue;
            sites.add(file.getFileName().toString());
            assertThat(text.substring(at, Math.min(text.length(), at + 400)))
                .as("@Primary in %s must sit on the request-root data source", file.getFileName())
                .contains("RecoveryRequestAdmittedDataSource");
        }
        assertThat(sites).as("exactly one primary data source in the application").containsExactly(APPLICATION_ENTRY);
    }

    @Test void mainSourcesReachTheLegacyAdapterOnlyAtItsDefinition() throws Exception {
        var offenders = new ArrayList<String>();
        for (Path file : javaSources("src/main/java")) {
            if (file.getFileName().toString().equals("RecoveryAdmittedDataSource.java")) continue;
            if (withoutDocConstructs(Files.readString(file)).contains("RecoveryAdmittedDataSource"))
                offenders.add(file.toString());
        }
        assertThat(offenders).as("the request path must not reach the legacy connection-level adapter").isEmpty();
    }

    @Test void testOnlyPrimariesAreFrozenAndKeptOutOfTheApplicationEntry() throws Exception {
        for (Path file : javaSources("src/test/java")) {
            String name = file.getFileName().toString();
            if (name.equals(SCANNER)) continue;
            String text = Files.readString(file);
            if (!text.contains("@Primary")) continue;
            assertThat(TEST_PRIMARY_WHITELIST).as("%s is not a frozen test-only primary", name).contains(name);
            assertThat(withoutDocConstructs(text)).as("the frozen harness must be its own opt-in")
                .contains("outbound.recovery.test.lost-commit-entry")
                .doesNotContain("outbound.recovery.admission.enabled");
        }
    }

    private static List<Path> javaSources(String root) throws Exception {
        try (var files = Files.walk(Path.of(root))) {
            return files.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
    }

    /** Drops {@code {@link ...}}-style constructs so a documentation reference is not read as a use. */
    private static String withoutDocConstructs(String text) {
        return text.replaceAll("\\{@[a-z]+\\s+[^}]*\\}", " ");
    }
}
