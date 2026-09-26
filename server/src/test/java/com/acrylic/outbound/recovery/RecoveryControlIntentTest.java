package com.acrylic.outbound.recovery;

import java.nio.file.*;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecoveryControlIntentTest {
    @Test void pendingIntentPreventsNewConnectionsWhileExistingLeaseCanFinish() throws Exception {
        var f = RecoveryWriterLeaseTest.protocol(); var source = mock(DataSource.class);
        var transaction = new RecoveryLeasedTransaction(source,
            new RecoveryControlLocation(f.root, f.installation, List.of(f.restoreRoot)), f.checkpoint);
        try (var lease = acquire(f)) {
            f.intend(f.next("RECOVERING"));
            assertThat(f.read().revision()).isEqualTo("1");
            assertThatThrownBy(() -> transaction.execute(status -> { throw new AssertionError("Admission ran"); }))
                .hasMessage("CONTROL_CLOSING_INTENT");
            verifyNoInteractions(source);
        }
        assertThatThrownBy(() -> acquire(f)).hasMessage("CONTROL_CLOSING_INTENT");
    }

    @Test void completeOriginalIntentAdmitsOnlyAfterItsHistoryCommits() throws Exception {
        var f = RecoveryWriterLeaseTest.protocol(); var command = f.next("RECOVERING");
        f.intend(command); assertThatThrownBy(() -> acquire(f)).hasMessage("CONTROL_CLOSING_INTENT");
        f.append(command);
        try (var lease = acquire(f)) { assertThat(f.read().revision()).isEqualTo("2"); }
    }

    @Test void missingRetainedIntentIsNotSynthesized() throws Exception {
        var f = RecoveryWriterLeaseTest.protocol(); f.intend(f.next("RECOVERING"));
        Files.move(f.root.resolve("intents/000000000000000002.json"), f.root.getParent().resolve("retained-intent.json"));
        assertThatThrownBy(f::read).hasMessage("CONTROL_INTENT_HEAD_CONFLICT");
        assertThat(f.root.resolve("intents/000000000000000002.json")).doesNotExist();
    }

    @Test void intentWithoutPublishedHeaderCannotBeAdmitted() throws Exception {
        var f = RecoveryWriterLeaseTest.protocol(); byte[] header = Files.readAllBytes(f.root.resolve("admission.json"));
        f.intend(f.next("RECOVERING")); Files.write(f.root.resolve("admission.json"), header);
        assertThatThrownBy(f::read).hasMessage("CONTROL_INTENT_INCOMPLETE");
        assertThatThrownBy(() -> acquire(f)).hasMessage("CONTROL_INTENT_INCOMPLETE");
    }

    @Test void originalVersionOneFilesStayReadableWithoutNewWriterAdmission() throws Exception {
        var f = RecoveryWriterLeaseTest.protocol();
        for (String name : List.of("intents", "admission.json")) Files.move(f.root.resolve(name), f.root.getParent().resolve(name));
        var metadata = (com.fasterxml.jackson.databind.node.ObjectNode) RecoveryControlHistory.read(f.root.resolve("writers.json"));
        metadata.put("formatVersion", 1); RecoveryControlFixture.write(f.root.resolve("writers.json"), metadata);
        assertThat(f.read().revision()).isEqualTo("1");
        assertThatThrownBy(() -> acquire(f)).hasMessage("CONTROL_ADMISSION_PROTOCOL_REQUIRED");
        assertThat(f.root.resolve("admission.json")).doesNotExist();
    }

    private static RecoveryWriterLease acquire(RecoveryControlFixture f) throws Exception {
        return RecoveryControlLock.locked(f.root, () -> RecoveryWriterLease.acquire(f.root));
    }
}
