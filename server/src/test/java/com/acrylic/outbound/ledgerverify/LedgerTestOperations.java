package com.acrylic.outbound.ledgerverify;

import com.acrylic.outbound.audit.AuditWriter;
import com.acrylic.outbound.temporary.command.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Never packaged with production. This participant creates only a labelled synthetic audit effect. */
@TestConfiguration(proxyBeanMethods=false)
class LedgerTestOperations {
    @Bean CommandOperation normalLedgerVerification(AuditWriter audit) { return new Synthetic("NORMAL_CONFIRM",audit); }
    @Bean CommandOperation shortLedgerVerification(AuditWriter audit) { return new Synthetic("SHORT_APPROVE",audit); }
    private record Synthetic(String commandType,AuditWriter audit) implements CommandOperation {
        @Override public void apply(CommandContext context) {
            audit.append(new AuditWriter.Event(context.id("originalOperatorId"),"LEDGER_VERIFY_EFFECT","LEDGER_VERIFY_EFFECT",
                context.id("scopeId"),"{\"internalTestOnly\":true}"),"ledger-verify-effect");
        }
    }
}
