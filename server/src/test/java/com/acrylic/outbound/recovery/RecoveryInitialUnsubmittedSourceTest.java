package com.acrylic.outbound.recovery;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RecoveryInitialUnsubmittedSourceTest {
    @Test void combinesAllOriginalsWithAbsentTerminalHash() throws Exception {
        var f = new RecoveryUnsubmittedOccupancyFixture(); var proof = reader(f).read(request(f));
        assertThat(proof.identity()).isEqualTo(f.initial.expected()); assertThat(proof.originalSha256()).hasSize(5);
        assertThat(proof.originalOccupancySha256()).isEqualTo(f.pin);
        assertThat(proof.originalSetSha256()).isEqualTo(f.evidence.stop.path("setHash").asText());
        assertThat(proof.auditEventCount()).isEqualTo(5); assertThat(proof.retainedRowCount()).isEqualTo(1);
        assertThat(proof.ordinaryWritesAllowed()).isFalse(); assertThat(proof.localDeletionAllowed()).isFalse();
    }

    @Test void originalActivationRemainsMandatory() throws Exception {
        var f = new RecoveryUnsubmittedOccupancyFixture(); f.initial.prep.origin.remove("activation"); f.initial.prep.write(); sync(f);
        assertThatThrownBy(() -> reader(f).read(request(f))).hasMessage("RECOVERY_TEMP_ACTIVATION_REQUIRED");
    }

    @Test void nativeEvidenceCannotReplacePermissionProvenance() throws Exception {
        var f = new RecoveryUnsubmittedOccupancyFixture(); f.initial.state.firstWindow().put("userActive", false); sync(f);
        assertThatThrownBy(() -> reader(f).read(request(f))).hasMessage("RECOVERY_SOURCE_PERMISSION_REVOKED");
    }

    @Test void emptyHistoricalWindowCannotBeInferredFromOccupancy() throws Exception {
        var f = new RecoveryUnsubmittedOccupancyFixture(); f.initial.state.permission.putArray("windows"); sync(f);
        assertThatThrownBy(() -> reader(f).read(request(f))).hasMessage("RECOVERY_SOURCE_PERMISSION_WINDOWS");
    }

    @Test void wrongOriginalE2aCannotBeCombined() throws Exception {
        var f = new RecoveryUnsubmittedOccupancyFixture(); var other = new RecoveryOriginalEvidenceFixture();
        var request = new RecoveryInitialUnsubmittedSource.Request(f.initial.request(), other.request(), f.request());
        assertThatThrownBy(() -> reader(f).read(request)).hasMessage("RECOVERY_INITIAL_NATIVE_IDENTITY");
    }

    @Test void anotherObservationCannotReplaceOriginalOccupancy() throws Exception {
        var f = new RecoveryUnsubmittedOccupancyFixture(); var other = new RecoveryUnsubmittedOccupancyFixture();
        var request = new RecoveryInitialUnsubmittedSource.Request(f.initial.request(), f.evidence.request(), other.request());
        assertThatThrownBy(() -> reader(f).read(request)).isInstanceOf(RecoveryControlException.class);
    }

    @ParameterizedTest @ValueSource(strings={"origin", "payload", "result", "permission", "occupancy"})
    void repeatedReadRechecksEveryOriginal(String kind) throws Exception {
        var f = new RecoveryUnsubmittedOccupancyFixture(); reader(f).read(request(f));
        var file = f.initial.state.authority.source.object(kind); byte[] raw = Files.readAllBytes(file); raw[0] ^= 1; Files.write(file, raw);
        assertThatThrownBy(() -> reader(f).read(request(f))).hasMessage("RECOVERY_SOURCE_OBJECT_MISMATCH");
    }

    @Test void submittedSourceCannotUseTheUnsubmittedCombination() throws Exception {
        var f = new RecoveryUnsubmittedOccupancyFixture(); f.initial.publish();
        f.observation.put("terminalReceipt", new String(f.initial.source().original("result"), StandardCharsets.UTF_8)); f.publish();
        assertThatThrownBy(() -> reader(f).read(request(f))).hasMessage("RECOVERY_UNSUBMITTED_TERMINAL");
    }

    @Test void formalSourceCannotUseTheTemporaryCombination() throws Exception {
        var f = new RecoveryUnsubmittedOccupancyFixture(); var formal = new RecoveryInitialFixture("FORMAL");
        var request = new RecoveryInitialUnsubmittedSource.Request(formal.request(), f.evidence.request(), f.request());
        assertThatThrownBy(() -> reader(f).read(request)).hasMessage("RECOVERY_INITIAL_UNSUBMITTED_SOURCE");
    }

    private static void sync(RecoveryUnsubmittedOccupancyFixture f) throws Exception {
        f.initial.publish(); f.nullHashTerminal();
        f.observation.put("terminalReceipt", new String(f.initial.source().original("result"), StandardCharsets.UTF_8));
        f.observation.set("occupancy", f.initial.state.occupancy.deepCopy()); f.publish();
    }
    private static RecoveryInitialUnsubmittedSource reader(RecoveryUnsubmittedOccupancyFixture f) {
        return new RecoveryInitialUnsubmittedSource(f.initial.state.authority.source.reader(), f.reader(), f.initial.keys());
    }
    private static RecoveryInitialUnsubmittedSource.Request request(RecoveryUnsubmittedOccupancyFixture f) {
        return new RecoveryInitialUnsubmittedSource.Request(f.initial.request(), f.evidence.request(), f.request());
    }
}
