package com.acrylic.outbound.ledgerverify;

import com.acrylic.outbound.audit.AuditWriter;
import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.temporary.command.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LedgerFaultIT extends LedgerRuntimeFixture {
    @MockitoSpyBean CommandStore store;
    @MockitoSpyBean OfflineSigner signer;
    @MockitoSpyBean AuditWriter audit;
    @ParameterizedTest @ValueSource(strings={"request","version","receipt","event","audit","signature"})
    void eachActualBoundaryFailureRollsBackAllLedgerFacts(String stage) throws Exception {
        var seed=seed("complete");var command=new LedgerCommandData(jdbc).command(seed,"NORMAL_CONFIRM");String request="fault-"+command.path("commandId").asText();
        var facts=new LedgerDatabaseFacts(jdbc);var before=facts.counts(seed,request);var original=new LedgerOriginalFacts(jdbc).capture(seed.id());
        failAt(stage);
        try { assertThatThrownBy(()->execute(seed,command,request)).isInstanceOf(IllegalStateException.class).hasMessageContaining("LEDGER_VERIFY_FAULT"); }
        finally { reset(store,signer,audit); }
        assertThat(facts.counts(seed,request)).isEqualTo(before);assertThat(new LedgerOriginalFacts(jdbc).capture(seed.id())).isEqualTo(original);
        assertThat(count("SELECT COUNT(*) FROM operation_event WHERE entity_type='LEDGER_VERIFY_EFFECT' AND entity_id=(SELECT scope_id FROM submission WHERE id=UUID_TO_BIN(?))",seed.id())).isZero();
        new LedgerOriginalFacts(jdbc).save("fault-"+stage+"-"+command.path("commandId").asText()+".json",java.util.Map.of("stage",stage,"before",before,"after",facts.counts(seed,request)));
    }
    private void failAt(String stage) {
        org.mockito.stubbing.Answer<Object> after=call->{call.callRealMethod();throw new IllegalStateException("LEDGER_VERIFY_FAULT after "+stage);};
        switch(stage) {
            case "request" -> doAnswer(after).when(store).reserve(any(CommandWrite.class));
            case "version" -> doAnswer(after).when(store).advance(any(CommandWrite.class));
            case "receipt" -> doAnswer(after).when(store).receipt(any(CommandWrite.class),any(CommandReceiptCodec.Receipt.class));
            case "event" -> doAnswer(after).when(store).event(any(CommandWrite.class),any(CommandReceiptCodec.Receipt.class));
            case "audit" -> doAnswer(call->{call.callRealMethod();if(((AuditWriter.Event)call.getArgument(0)).entityType().equals("TEMPORARY_COMMAND"))throw new IllegalStateException("LEDGER_VERIFY_FAULT after audit");return null;}).when(audit).append(any(),anyString());
            case "signature" -> doThrow(new IllegalStateException("LEDGER_VERIFY_FAULT signature")).when(signer).sign(any());
            default -> throw new IllegalArgumentException(stage);
        }
    }
}
