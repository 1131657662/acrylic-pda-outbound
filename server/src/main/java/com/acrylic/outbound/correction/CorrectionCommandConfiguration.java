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
    CorrectionCommand.Operation correctionCreate(CorrectionStore store, ObjectProvider<CorrectionAttachmentConsumer> attachments,
        CorrectionHeaderEvidence headers) {
        var create = new CorrectionOperations.Create(store, headers); var consumer = attachments.getIfAvailable();
        return consumer == null ? create : new CorrectionAttachmentCreate(create, consumer);
    }
    @Bean CorrectionCommand.Operation correctionApprove(CorrectionStore store, CorrectionAccounting accounting, CorrectionHeaderEvidence headers) {
        return new CorrectionApprove(store, accounting, headers);
    }
    @Bean CorrectionCommand.Operation correctionWithdraw(CorrectionStore store, CorrectionHeaderEvidence headers) {
        return new CorrectionOperations.Withdraw(store, headers);
    }
    @Bean CorrectionCommand.Operation correctionWithdrawConfirm(CorrectionStore store, CorrectionAccounting accounting,
        org.springframework.jdbc.core.JdbcTemplate jdbc, CorrectionHeaderEvidence headers) {
        return new CorrectionWithdrawConfirm(store, accounting, new CorrectionOccupancyRestoration(jdbc), headers);
    }
    @Bean CorrectionCommand.Operation reconciliationOpen(CorrectionStore store) { return new CorrectionReconciliations.Open(store); }
    @Bean CorrectionCommand.Operation reconciliationAdvance(CorrectionStore store) { return new CorrectionReconciliations.Advance(store); }
}
