package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.BusinessException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

class HandoverRecoveredConfirmationCommandTest {
    @TempDir Path directory;
    @Test void equivalentJsonKeepsOriginalCanonicalIntent() {
        var input=command(); var first=HandoverRecoveredConfirmationCommand.parse(bytes(input));
        var replay=HandoverRecoveredConfirmationCommand.parse(input.toPrettyString().getBytes(StandardCharsets.UTF_8));
        assertThat(replay.bytes()).isEqualTo(first.bytes()); assertThat(replay.sha256()).isEqualTo(first.sha256());
        input.put("physicalConfirmed",false); assertThat(first.body().path("physicalConfirmed").booleanValue()).isTrue();
        byte[] copy=first.bytes(); copy[0]=0; assertThat(first.bytes()[0]).isEqualTo((byte)'{');
    }
    @ParameterizedTest @ValueSource(strings={"commandId","qualificationId","transportRecoveryEpoch","sourceRecoveryEpoch","manifestSha256","payloadSha256"})
    void malformedBindingsAreRejected(String field) {
        var input=command().put(field,"changed"); invalid(()->HandoverRecoveredConfirmationCommand.parse(bytes(input)));
    }
    @Test void confirmationMustBeExplicitAndPurposeSpecific() {
        invalid(()->HandoverRecoveredConfirmationCommand.parse(bytes(command().put("physicalConfirmed",false))));
        invalid(()->HandoverRecoveredConfirmationCommand.parse(bytes(command().put("physicalConfirmed","true"))));
        invalid(()->HandoverRecoveredConfirmationCommand.parse(bytes(command().put("commandType","PREPARE"))));
        invalid(()->HandoverRecoveredConfirmationCommand.parse(bytes(command().put("extra","ignored"))));
        invalid(()->HandoverRecoveredConfirmationCommand.parse(new byte[65537]));
    }
    @Test void recoveredSignatureCannotBeUsedAsOrdinaryConfirmation() throws Exception {
        var signatures=new HandoverStopSignature(HandoverTemporaryCredentialFixture.signer(directory,"recovered",""));
        var recovered=signatures.signRecoveredConfirmation(object().put("purpose","RECOVERED_TEMP_HANDOVER_CONFIRMATION"));
        signatures.verifyRecoveredConfirmation(recovered);
        assertThatThrownBy(()->signatures.verifyTemporaryConfirmation(recovered)).isInstanceOf(BusinessException.class);
        var ordinary=signatures.signTemporaryConfirmation(object().put("purpose","RECOVERED_TEMP_HANDOVER_CONFIRMATION"));
        assertThatThrownBy(()->signatures.verifyRecoveredConfirmation(ordinary)).isInstanceOf(BusinessException.class);
        recovered.put("purpose","TEMP_HANDOVER_CONFIRMATION");
        assertThatThrownBy(()->signatures.verifyRecoveredConfirmation(recovered)).isInstanceOf(BusinessException.class);
    }
    private static ObjectNode command() {
        var input=object().put("commandVersion",1).put("commandType","RECOVERED_TEMP_HANDOVER_CONFIRM")
            .put("commandId",UUID.randomUUID().toString()).put("caseId",UUID.randomUUID().toString())
            .put("qualificationId",UUID.randomUUID().toString()).put("transportRecoveryEpoch","2").put("sourceRecoveryEpoch","1")
            .put("expectedCaseVersion","4").put("physicalConfirmed",true);
        for(String field:List.of("qualificationSha256","recipientSessionSha256","manifestSha256","preparedTicketSha256","payloadSha256","checklistSha256"))
            input.put(field,"a".repeat(64));
        return input;
    }
    private static byte[] bytes(ObjectNode body) { return canonical(body).getBytes(StandardCharsets.UTF_8); }
    private static void invalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(BusinessException.class,error->assertThat(error.code()).isEqualTo("INVALID_RECOVERED_CONFIRMATION"));
    }
}
