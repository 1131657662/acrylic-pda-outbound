package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.ServerState;
import com.acrylic.outbound.pda.PdaSessions;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.command.CommandContext;
import com.acrylic.outbound.temporary.command.CommandIdentity;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SourceEvidenceIntakeReadServiceTest {
    private static final UUID WAREHOUSE=UUID.randomUUID(), OPERATOR=UUID.randomUUID();
    private static final UUID DEVICE=UUID.randomUUID(), SUBMISSION=UUID.randomUUID();
    private static final UUID ORIGINAL_KEY=UUID.randomUUID(), SESSION=UUID.randomUUID();
    private final SourceEvidenceIntakeIdentity identity=mock(SourceEvidenceIntakeIdentity.class);
    private final SourceEvidenceIntakeFacts facts=mock(SourceEvidenceIntakeFacts.class);
    private final SourceEvidenceIntakeReadStore store=mock(SourceEvidenceIntakeReadStore.class);
    private final SourceEvidenceIntakeReadIntegrity integrity=mock(SourceEvidenceIntakeReadIntegrity.class);
    private final ServerState state=mock(ServerState.class);
    private final SourceEvidenceIntakeReadService service=new SourceEvidenceIntakeReadService(
        identity,facts,store,integrity,state);
    private final CommandIdentity.Actor actor=new CommandIdentity.Actor(OPERATOR,WAREHOUSE,"8",
        new PdaSessions.Principal(SESSION,OPERATOR,"operator",DEVICE,WAREHOUSE,"8","2"),
        List.of("pda:recover"),List.of(WAREHOUSE.toString()));
    private CommandContext source;

    @BeforeEach void setUp() {
        var current=SubmissionJson.object().put("warehouseId",WAREHOUSE.toString())
            .put("originalOperatorId",OPERATOR.toString()).put("creatorDeviceId",DEVICE.toString())
            .put("submissionId",SUBMISSION.toString()).put("currentSubmissionId",SUBMISSION.toString())
            .put("workRound","2").put("scopeRound","3");
        source=new CommandContext(current);
        when(identity.current(WAREHOUSE)).thenReturn(actor);
        when(facts.read(SUBMISSION)).thenReturn(source);
        when(store.find(OPERATOR,ORIGINAL_KEY)).thenReturn(Map.of());
        when(state.current()).thenReturn(new ServerState.State("4","NORMAL"));
    }

    @Test void readSeparatesOriginalReceiptFromCurrentConservativeEffect() {
        when(integrity.check(anyMap(),eq(source),eq(ORIGINAL_KEY))).thenReturn(result("VERIFIED"));

        var view=service.read(WAREHOUSE,SUBMISSION,2,ORIGINAL_KEY.toString());

        assertThat(view.originalResponseUtf8()).isEqualTo("{}");
        assertThat(view.currentEffect().path("applicable").booleanValue()).isFalse();
        assertThat(view.currentEffect().path("reasons").toString()).contains("SCOPE_ROUND_CHANGED", "SESSION_CHANGED");
        assertThat(view.currentEffect().path("capabilities").path("localDeletionAllowed").booleanValue()).isFalse();
    }

    @Test void completeStopVerificationDoesNotReportMissingStopProof() {
        when(integrity.check(anyMap(),eq(source),eq(ORIGINAL_KEY))).thenReturn(result("VERIFIED"));

        var effect=service.read(WAREHOUSE,SUBMISSION,2,ORIGINAL_KEY.toString()).currentEffect();

        assertThat(effect.path("evaluation").asText()).isEqualTo("VERIFIED_COMPLETE_AT_STOP");
        assertThat(effect.path("reasons").toString()).doesNotContain("NO_TRUSTED_COMPLETE_AT_STOP");
        assertThat(effect.path("reasons").toString()).contains("BUSINESS_DECISION_NOT_IMPLEMENTED");
    }

    @Test void unknownHistoricalKeyCannotUseRawTrustedEndpoint() {
        when(integrity.check(anyMap(),eq(source),eq(ORIGINAL_KEY))).thenReturn(result("UNVERIFIABLE_KEY"));

        assertThatThrownBy(() -> service.original(WAREHOUSE,SUBMISSION,2,ORIGINAL_KEY.toString()))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.code()).isEqualTo("E2A_RECEIPT_KEY_UNVERIFIABLE"));
    }

    @Test void historicalPartialOriginalDoesNotAcquireCompleteStopEffect() {
        var original=result("VERIFIED");
        var receipt=(com.fasterxml.jackson.databind.node.ObjectNode)original.receipt(); receipt.put("status","PARTIAL");
        when(integrity.check(anyMap(),eq(source),eq(ORIGINAL_KEY))).thenReturn(
            new SourceEvidenceIntakeReadIntegrity.Result(original.originalBytes(),original.sha256(),"VERIFIED",receipt));

        var effect=service.read(WAREHOUSE,SUBMISSION,2,ORIGINAL_KEY.toString()).currentEffect();

        assertThat(effect.path("evaluation").asText()).isEqualTo("PARTIAL");
        assertThat(effect.path("reasons").toString()).contains("NO_TRUSTED_COMPLETE_AT_STOP");
        assertThat(effect.path("applicable").booleanValue()).isFalse();
        assertThat(effect.path("capabilities")).isEqualTo(SourceEvidenceIntakeReceiptCodec.capabilities());
    }

    @Test void verifiedOriginalEndpointReturnsStoredBytes() {
        when(integrity.check(anyMap(),eq(source),eq(ORIGINAL_KEY))).thenReturn(result("VERIFIED"));

        assertThat(service.original(WAREHOUSE,SUBMISSION,2,ORIGINAL_KEY.toString()))
            .isEqualTo("{}".getBytes(StandardCharsets.UTF_8));
    }

    @Test void foreignDeviceCannotLookupReceipt() {
        source=new CommandContext(SubmissionJson.object().put("warehouseId",WAREHOUSE.toString())
            .put("originalOperatorId",OPERATOR.toString()).put("creatorDeviceId",UUID.randomUUID().toString())
            .put("workRound","2"));
        when(facts.read(SUBMISSION)).thenReturn(source);

        assertThatThrownBy(() -> service.read(WAREHOUSE,SUBMISSION,2,ORIGINAL_KEY.toString()))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.code()).isEqualTo("E2A_SOURCE_NOT_FOUND"));
        verify(store,never()).find(OPERATOR,ORIGINAL_KEY);
    }

    private static SourceEvidenceIntakeReadIntegrity.Result result(String trust) {
        var context=SubmissionJson.object().put("userAuthVersion","7").put("deviceVersion","1")
            .put("currentSessionId",UUID.randomUUID().toString());
        var receipt=SubmissionJson.object().put("operationKind","VERIFY")
            .put("status","VERIFIED_COMPLETE_AT_STOP").set("originalContext",context);
        return new SourceEvidenceIntakeReadIntegrity.Result("{}".getBytes(StandardCharsets.UTF_8),"digest",trust,receipt);
    }
}
