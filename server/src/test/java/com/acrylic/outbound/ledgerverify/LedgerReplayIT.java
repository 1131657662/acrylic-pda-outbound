package com.acrylic.outbound.ledgerverify;

import com.acrylic.outbound.common.BusinessException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LedgerReplayIT extends LedgerRuntimeFixture {
    @ParameterizedTest @ValueSource(strings={"complete","abort-empty","abort-partial","abort-complete"})
    void ledgerLeavesOriginalSourceFactsUnchanged(String scenario) throws Exception {
        var seed=seed(scenario);var facts=new LedgerOriginalFacts(jdbc);var before=facts.capture(seed.id());
        var command=new LedgerCommandData(jdbc).command(seed,"NORMAL_CONFIRM");
        byte[] first=execute(seed,command);var stored=new LedgerDatabaseFacts(jdbc).stored(seed,command);
        assertThat(first).isEqualTo(stored.response());assertThat(execute(seed,command)).isEqualTo(first);
        var response=json.readTree(first);assertThat(response.path("status").asText()).isEqualTo("INTERNAL_TEST_ONLY");
        assertThat(response.path("businessDecision").isNull()).isTrue();response.path("capabilities").forEach(v->assertThat(v.asBoolean()).isFalse());
        seed.sendPda(get("/api/v1/pda/temporary-command-receipts/"+command.path("commandId").asText()+"/original")
            .param("warehouseId",seed.warehouse())).andExpect(status().isOk()).andExpect(content().bytes(first)).andExpect(header().string("Cache-Control","no-store"));
        assertThat(facts.capture(seed.id())).isEqualTo(before);
    }

    @Test void sameKeyChangedReasonCannotOverwriteOriginalBytes() throws Exception {
        var seed=seed("complete");var command=new LedgerCommandData(jdbc).command(seed,"NORMAL_CONFIRM");byte[] first=execute(seed,command);
        var changed=command.deepCopy().put("reason","异文");
        assertThatThrownBy(()->execute(seed,changed)).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.code()).isEqualTo("IDEMPOTENCY_CONTENT_MISMATCH"));
        assertThat(original(seed,command)).isEqualTo(first);
        var counts=new LedgerDatabaseFacts(jdbc).counts(seed,"verify-"+command.path("commandId").asText());
        assertThat(((Number)counts.get("requests")).longValue()).isEqualTo(1);assertThat(((Number)counts.get("events")).longValue()).isEqualTo(1);
    }

    @Test void identicalClientKeyForDifferentPrincipalsHasIndependentReceipts() throws Exception {
        var a=seed("complete");var b=seed("complete");var commands=new LedgerCommandData(jdbc);
        var ac=commands.command(a,"NORMAL_CONFIRM");var bc=commands.command(b,"NORMAL_CONFIRM").put("commandId",ac.path("commandId").asText());
        byte[] ar=execute(a,ac),br=execute(b,bc);
        assertThat(json.readTree(ar).path("receiptId")).isNotEqualTo(json.readTree(br).path("receiptId"));
        assertThat(original(a,ac)).isEqualTo(ar);assertThat(original(b,bc)).isEqualTo(br);
    }

    @Test void challengeReloginReadsOriginalSessionReceipt() throws Exception {
        var seed=seed("complete");var command=new LedgerCommandData(jdbc).command(seed,"NORMAL_CONFIRM");
        String oldSession=seed.tokens().path("sessionId").asText();byte[] first=execute(seed,command);
        seed.relogin();assertThat(seed.tokens().path("sessionId").asText()).isNotEqualTo(oldSession);
        assertThat(json.readTree(first).at("/actor/sessionId").asText()).isEqualTo(oldSession);
        assertThat(original(seed,command)).isEqualTo(first);assertThat(execute(seed,command)).isEqualTo(first);
    }

    @Test void revokedHistoricalSessionRemainsVerifiableAfterGenuineRelogin() throws Exception {
        var seed=seed("complete");var command=new LedgerCommandData(jdbc).command(seed,"NORMAL_CONFIRM");byte[] first=execute(seed,command);
        String old=seed.tokens().path("sessionId").asText();seed.identityMutation("session").call();seed.relogin();
        assertThat(jdbc.queryForObject("SELECT revoked_at IS NOT NULL FROM pda_session WHERE id=UUID_TO_BIN(?)",Boolean.class,old)).isTrue();
        assertThat(original(seed,command)).isEqualTo(first);assertThat(execute(seed,command)).isEqualTo(first);
    }

    @Test void revokedWritePermissionWithCurrentReadPermissionReplaysBeforeExpected() throws Exception {
        var seed=seed("complete");var command=new LedgerCommandData(jdbc).command(seed,"SHORT_APPROVE");byte[] first=execute(seed,command);
        var change=seed.userChange();change.putArray("roles").add("PDA_OPERATOR").add("TEMPORARY_ORDER_CONFIRMER");seed.changeUser(change);seed.relogin();
        assertThat(seed.tokens().path("userAuthVersion")).isNotEqualTo(command.at("/expected/userAuthVersion"));
        assertThat(execute(seed,command)).isEqualTo(first);assertThat(original(seed,command)).isEqualTo(first);
        var newKey=command.deepCopy().put("commandId",UUID.randomUUID().toString());
        assertThatThrownBy(()->execute(seed,newKey)).isInstanceOf(BusinessException.class);
    }
}
