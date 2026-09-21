package com.acrylic.outbound.inventory;

import com.acrylic.outbound.audit.AuditWriter;
import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.pda.PdaSessions;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.command.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Import(LedgerAuthorOperations.class)
@EnabledIfEnvironmentVariable(named="LEDGER_RUNTIME_VERIFY",matches="true")
@ContextConfiguration(initializers=LedgerRuntimeEnvironment.class)
class LedgerAuthorIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    @Autowired TemporaryCommandService commands;@Autowired CommandFacts facts;@Autowired PdaSessions sessions;
    @MockitoSpyBean CommandReceiptCodec codec;@MockitoSpyBean AuditWriter audit;
    LedgerAuthorFixture fixture;
    @BeforeEach void source() throws Exception {
        var web=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));
        jdbc.update("INSERT INTO user_role SELECT UUID_TO_BIN(?),id FROM role WHERE code='TEMPORARY_ORDER_CONFIRMER'",web.identity().user().id().toString());
        fixture=new LedgerAuthorFixture(new TemporaryFixture(web,jdbc));fixture.web();
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext();reset(codec,audit); }
    @ParameterizedTest @ValueSource(strings={"NORMAL_CONFIRM","SHORT_APPROVE"})
    void exactReplayAndBothWebPdaGetsPreserveFourPartCommit(String kind) throws Exception {
        var command=fixture.command(facts,kind);byte[] original=execute(command);assertThat(execute(command)).isEqualTo(original);
        var receipt=SubmissionJson.parse(original);assertThat(receipt.get("businessDecision").isNull()).isTrue();
        assertThat(receipt.get("status").asText()).isEqualTo("INTERNAL_TEST_ONLY");assertCounts(1);
        String suffix=command.get("commandId").asText();var web=fixture.temp.web;
        for(String base:List.of("/api/v1/temporary-command-receipts/","/api/v1/pda/temporary-command-receipts/")) {
            var request=get(base+suffix+"/original").param("warehouseId",web.identity().warehouse().toString());
            var result=base.contains("/pda/")?fixture.temp.pda.send(request,fixture.temp.tokens):web.send(request);
            assertThat(result.andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andReturn().getResponse().getContentAsByteArray()).isEqualTo(original);
            var detail=get(base+suffix).param("warehouseId",web.identity().warehouse().toString());
            var view=base.contains("/pda/")?fixture.temp.pda.data(detail,fixture.temp.tokens):web.data(detail);
            assertThat(view.get("originalCommandReceiptUtf8").asText().getBytes(StandardCharsets.UTF_8)).isEqualTo(original);
            assertThat(view.at("/currentEffect/evaluation").asText()).isEqualTo("UNKNOWN");
        }
        assertCounts(1);verify(codec,times(1)).sign(any());
    }
    @Test void replayPrecedesChangedExpectedTransportAndCurrentMode() {
        var command=fixture.command(facts,"NORMAL_CONFIRM");byte[] original=execute(command);
        jdbc.update("UPDATE server_state SET recovery_epoch=2,service_mode='RECOVERY_PENDING' WHERE id=1");
        try { assertThat(execute(command)).isEqualTo(original);assertCounts(1); }
        finally { jdbc.update("UPDATE server_state SET recovery_epoch=1,service_mode='NORMAL' WHERE id=1"); }
    }
    @Test void changedBytesConflictAndNewKeyWithStaleExpectedCannotWrite() {
        var command=fixture.command(facts,"NORMAL_CONFIRM");execute(command);
        assertCode(()->execute(command.deepCopy().put("reason","different")),"IDEMPOTENCY_CONTENT_MISMATCH");
        assertCode(()->execute(command.deepCopy().put("commandId",UUID.randomUUID().toString())),"COMMAND_CONTEXT_CHANGED");assertCounts(1);
    }
    @Test void signingFailureRollsBackReservationVersionEventAndAudit() {
        var command=fixture.command(facts,"NORMAL_CONFIRM");doThrow(new IllegalStateException("synthetic signer fault")).when(codec).sign(any());
        assertThatThrownBy(()->execute(command)).hasMessage("synthetic signer fault");assertCounts(0);
        assertThat(facts.read(UUID.fromString(fixture.submission)).text("decisionVersion")).isEqualTo("0");
        reset(codec);execute(command);assertCounts(1);
    }
    @Test void auditFailureRollsBackAlreadySignedReceiptAndEvent() {
        var command=fixture.command(facts,"NORMAL_CONFIRM");doThrow(new IllegalStateException("synthetic audit fault")).when(audit).append(any(),anyString());
        assertThatThrownBy(()->execute(command)).hasMessage("synthetic audit fault");assertCounts(0);
        assertThat(facts.read(UUID.fromString(fixture.submission)).text("decisionVersion")).isEqualTo("0");
        reset(audit);execute(command);assertCounts(1);
    }
    @Test void pdaChallengeReloginPreservesOriginalSessionBytes() throws Exception {
        var old=sessions.authenticate(fixture.temp.tokens.get("accessToken").asText());LedgerAuthorFixture.authenticate(old);
        var command=fixture.command(facts,"NORMAL_CONFIRM");byte[] original=execute(command);
        jdbc.update("UPDATE pda_session SET revoked_at=UTC_TIMESTAMP(3) WHERE id=UUID_TO_BIN(?)",old.sessionId().toString());
        var fresh=fixture.temp.pda.login(fixture.temp.device);LedgerAuthorFixture.authenticate(sessions.authenticate(fresh.get("accessToken").asText()));
        assertThat(execute(command)).isEqualTo(original);assertThat(SubmissionJson.parse(original).at("/actor/sessionId").asText()).isEqualTo(old.sessionId().toString());
        String path="/api/v1/pda/temporary-command-receipts/"+command.get("commandId").asText();
        var detail=fixture.temp.pda.data(get(path).param("warehouseId",fixture.temp.web.identity().warehouse().toString()),fresh);
        assertThat(detail.at("/currentEffect/reasons").toString()).contains("ACTOR_CONTEXT_CHANGED:sessionId");assertCounts(1);
    }
    @Test void missingPermissionAndCrossActorNeverReturnOriginalBytes() throws Exception {
        var command=fixture.command(facts,"NORMAL_CONFIRM");execute(command);
        var other=IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN");
        jdbc.update("INSERT INTO user_warehouse VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?))",other.user().id().toString(),fixture.temp.web.identity().warehouse().toString());
        var stranger=new PublicationDriver(mvc,json,new IdentityFixture(other.user(),fixture.temp.web.identity().warehouse()));
        stranger.send(get("/api/v1/temporary-command-receipts/"+command.get("commandId").asText()+"/original")).andExpect(status().isNotFound());
        jdbc.update("DELETE FROM user_role WHERE user_id=UUID_TO_BIN(?)",fixture.temp.web.identity().user().id().toString());
        fixture.temp.web.send(get("/api/v1/temporary-command-receipts/"+command.get("commandId").asText()+"/original")).andExpect(status().isNotFound());
    }
    @Test void corruptedOriginalBlobIsClosedWithoutRepairOrResign() throws Exception {
        var command=fixture.command(facts,"NORMAL_CONFIRM");execute(command);
        jdbc.update("UPDATE temporary_command_receipt SET original_response_bytes=CAST('{}' AS BINARY) WHERE submission_id=UUID_TO_BIN(?)",fixture.submission);
        fixture.temp.web.send(get("/api/v1/temporary-command-receipts/"+command.get("commandId").asText()+"/original"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COMMAND_RECEIPT_INTEGRITY_FAILURE"));
        verify(codec,times(1)).sign(any());assertCounts(1);
    }
    private byte[] execute(ObjectNode command) { return commands.execute(command.toString().getBytes(StandardCharsets.UTF_8),"ledger-author"); }
    private void assertCounts(int count) { for(String table:List.of("request","receipt","event","audit"))assertThat(fixture.count(table)).as(table).isEqualTo(count); }
    private void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable work,String code) {
        assertThatThrownBy(work).isInstanceOfSatisfying(BusinessException.class,error->assertThat(error.code()).isEqualTo(code));
    }
}
