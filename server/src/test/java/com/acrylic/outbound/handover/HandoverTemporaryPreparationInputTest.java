package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.BusinessException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

class HandoverTemporaryPreparationInputTest {
    @Test void canonicalRecipientAuthorizationIsPreserved() {
        var id = UUID.randomUUID();
        assertThat(HandoverTemporaryEligibility.authorization(command().put("targetAuthorizationId", id.toString()))).isEqualTo(id);
    }

    @ParameterizedTest @ValueSource(strings = {"", "invalid", "1-1-1-1-1", "A1111111-1111-1111-1111-111111111111"})
    void malformedAuthorizationIsRejected(String id) {
        refused(command().put("targetAuthorizationId", id), "HANDOVER_TARGET_AUTHORIZATION_REQUIRED");
    }

    @Test void missingOrNontextualAuthorizationDoesNotBecomeAnOptionalTarget() {
        refused(command(), "HANDOVER_TARGET_AUTHORIZATION_REQUIRED");
        refused(command().put("targetAuthorizationId", 1), "HANDOVER_TARGET_AUTHORIZATION_REQUIRED");
        refused(command().putNull("targetAuthorizationId"), "HANDOVER_TARGET_AUTHORIZATION_REQUIRED");
    }

    @Test void unknownFieldsAndCoercedVersionAreRejected() {
        var body = command().put("targetAuthorizationId", UUID.randomUUID().toString());
        refused(body.deepCopy().put("mayResumeScanning", true), "INVALID_HANDOVER_COMMAND");
        refused(body.deepCopy().put("commandVersion", "1"), "INVALID_HANDOVER_COMMAND");
        refused(body.deepCopy().put("commandType", "ACTIVATE"), "INVALID_HANDOVER_COMMAND");
    }

    private ObjectNode command() { return object().put("commandVersion", 1).put("commandType", "PREPARE"); }

    private void refused(ObjectNode body, String code) {
        assertThatThrownBy(() -> HandoverTemporaryEligibility.authorization(body)).isInstanceOfSatisfying(BusinessException.class,
            error -> { assertThat(error.status()).isEqualTo(400); assertThat(error.code()).isEqualTo(code); });
    }
}
