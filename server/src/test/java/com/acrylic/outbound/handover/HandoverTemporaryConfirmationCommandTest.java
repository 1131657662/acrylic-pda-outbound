package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.BusinessException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

class HandoverTemporaryConfirmationCommandTest {
    @TempDir Path directory;

    @Test void confirmationBindsCaseEpochAndPreservesCanonicalIntent() throws Exception {
        var f = new HandoverTemporaryConfirmationFixture(directory); var parsed = f.parsed(f.command);
        assertThat(parsed.bound().path("caseId").asText()).isEqualTo(f.kase.toString());
        assertThat(parsed.bound().path("transportRecoveryEpoch").asText()).isEqualTo("1");
        assertThat(HandoverCommand.sha(parsed.bytes())).isEqualTo(parsed.digest());
        var bytes = parsed.bytes(); bytes[0] = 0;
        var bound = (com.fasterxml.jackson.databind.node.ObjectNode) parsed.bound(); bound.put("caseId", "changed");
        assertThat(parsed.bound().path("caseId").asText()).isEqualTo(f.kase.toString());
        assertThat(HandoverCommand.sha(parsed.bytes())).isEqualTo(parsed.digest());
    }

    @ParameterizedTest @ValueSource(strings = {"physicalConfirmed", "expectedCaseVersion", "targetAuthorizationId", "checklistSha256", "commandId"})
    void missingRequiredBindingIsRejected(String field) throws Exception {
        var f = new HandoverTemporaryConfirmationFixture(directory); f.command.remove(field);
        refused(() -> f.parsed(f.command));
    }

    @ParameterizedTest @ValueSource(strings = {"false", "\"true\"", "null", "1"})
    void physicalConfirmationCannotBeCoerced(String value) throws Exception {
        var f = new HandoverTemporaryConfirmationFixture(directory); f.command.set("physicalConfirmed", parse(value));
        refused(() -> f.parsed(f.command));
    }

    @Test void unknownFieldsOverflowAndDuplicateJsonAreRejected() throws Exception {
        var f = new HandoverTemporaryConfirmationFixture(directory);
        refused(() -> f.parsed(f.command.deepCopy().put("mayResumeScanning", true)));
        refused(() -> f.parsed(f.command.deepCopy().put("commandVersion", 4294967297L)));
        refused(() -> f.parsed(f.command.deepCopy().put("expectedCaseVersion", "9223372036854775808")));
        var raw = canonical(f.command).replace("\"physicalConfirmed\":true", "\"physicalConfirmed\":true,\"physicalConfirmed\":false");
        refused(() -> HandoverTemporaryConfirmationCommand.parse(f.kase, "1", raw.getBytes(StandardCharsets.UTF_8)));
        refused(() -> HandoverTemporaryConfirmationCommand.parse(f.kase, "01", canonical(f.command).getBytes(StandardCharsets.UTF_8)));
    }

    private void refused(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(BusinessException.class,
            failure -> assertThat(failure.code()).isEqualTo("INVALID_TEMP_HANDOVER_CONFIRMATION"));
    }
}
