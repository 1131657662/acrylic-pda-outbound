package com.acrylic.outbound.correction;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Explicitly enabled operations; the production defaults keep the entire command boundary closed. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "outbound.corrections.commands", name = "enabled", havingValue = "true")
public class CorrectionCommandConfiguration {
    @Bean
    CorrectionCommand.Operation correctionCreate(CorrectionStore store, ObjectProvider<CorrectionAttachmentConsumer> attachments) {
        var create = new CorrectionOperations.Create(store); var consumer = attachments.getIfAvailable();
        return consumer == null ? create : new CorrectionAttachmentCreate(create, consumer);
    }
    @Bean CorrectionCommand.Operation correctionApprove(CorrectionStore store, CorrectionAccounting accounting) {
        return new CorrectionApprove(store, accounting);
    }
    @Bean CorrectionCommand.Operation correctionWithdraw(CorrectionStore store) { return new CorrectionOperations.Withdraw(store); }
    @Bean CorrectionCommand.Operation correctionWithdrawConfirm(CorrectionStore store, CorrectionAccounting accounting,
        org.springframework.jdbc.core.JdbcTemplate jdbc) {
        return new CorrectionWithdrawConfirm(store, accounting, new CorrectionOccupancyRestoration(jdbc));
    }
    @Bean CorrectionCommand.Operation reconciliationOpen(CorrectionStore store) { return new CorrectionReconciliations.Open(store); }
    @Bean CorrectionCommand.Operation reconciliationAdvance(CorrectionStore store) { return new CorrectionReconciliations.Advance(store); }
}
