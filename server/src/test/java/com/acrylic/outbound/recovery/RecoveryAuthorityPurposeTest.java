package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** S1: the single authority entry point declares its purpose and refuses an authority of the wrong kind. */
class RecoveryAuthorityPurposeTest {
    @Test void normalAdmissionLoadsThePlainControlAuthority() throws Exception {
        var fixture = RecoveryControlFixture.create();
        assertThat(RecoveryControlHistory.load(fixture.location(), fixture.checkpoint,
            RecoveryControlPurpose.NORMAL_ADMISSION)).isNotNull();
    }

    /**
     * The control identity has no {@code authorityKind}: its field set is exact, so a kind field is refused
     * as CONTROL_FIELDS. The kind that a purpose can validate lives in the fence/lease-root authority
     * ({@link RecoveryLeaseRoots}), not here — recorded as the S1 follow-up.
     */
    @Test void theControlIdentityRejectsAnAuthorityKindField() throws Exception {
        var fixture = RecoveryControlFixture.create();
        kind(fixture, "INITIAL_NORMAL_INSTALL");
        assertThatThrownBy(() -> RecoveryControlHistory.load(fixture.location(), fixture.checkpoint,
            RecoveryControlPurpose.NORMAL_ADMISSION)).hasMessage("CONTROL_FIELDS");
    }

    @Test void normalAdmissionRefusesAnAuthorityOfAnotherKind() throws Exception {
        var fixture = RecoveryControlFixture.create();
        kind(fixture, "CLOSED_AUTHORITY");
        assertThatThrownBy(() -> RecoveryControlHistory.load(fixture.location(), fixture.checkpoint,
            RecoveryControlPurpose.NORMAL_ADMISSION)).hasMessage("CONTROL_AUTHORITY_PURPOSE_FORBIDDEN");
    }

    @Test void closedProjectionAcceptsNothingToday() throws Exception {
        var fixture = RecoveryControlFixture.create();
        assertThatThrownBy(() -> RecoveryControlHistory.load(fixture.location(), fixture.checkpoint,
            RecoveryControlPurpose.CLOSED_PROJECTION)).hasMessage("CONTROL_AUTHORITY_PURPOSE_FORBIDDEN");
        kind(fixture, "INITIAL_NORMAL_INSTALL");
        assertThatThrownBy(() -> RecoveryControlHistory.load(fixture.location(), fixture.checkpoint,
            RecoveryControlPurpose.CLOSED_PROJECTION)).hasMessage("CONTROL_AUTHORITY_PURPOSE_FORBIDDEN");
    }

    /** No call site may omit the purpose: exactly one entry point exists, and it takes the purpose. */
    @Test void everyLoadCallSiteDeclaresAPurpose() throws Exception {
        var loads = Arrays.stream(RecoveryControlHistory.class.getDeclaredMethods())
            .filter(method -> method.getName().equals("load")).toList();
        assertThat(loads).as("single entry point").hasSize(1);
        assertThat(loads.getFirst().getParameterTypes()).containsExactly(
            RecoveryControlLocation.class, Path.class, RecoveryControlPurpose.class);
        var missing = new ArrayList<String>();
        try (var files = Files.walk(Path.of("src"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String text = Files.readString(file);
                int at = 0;
                while ((at = text.indexOf("RecoveryControlHistory.load(", at)) >= 0) {
                    if (!text.substring(at, Math.min(text.length(), at + 300)).contains("RecoveryControlPurpose."))
                        missing.add(file + " @" + at);
                    at += 27;
                }
            }
        }
        assertThat(missing).as("every load call site must name its purpose").isEmpty();
    }

    private static void kind(RecoveryControlFixture fixture, String value) throws Exception {
        var identity = (ObjectNode)RecoveryControlHistory.read(fixture.root.resolve("identity.json"));
        identity.put("authorityKind", value);
        RecoveryControlFixture.write(fixture.root.resolve("identity.json"), identity);
    }
}
