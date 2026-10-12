package com.acrylic.outbound.correction;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Business drivers require the existing explicit correction switch; all production defaults remain closed. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix="outbound.corrections.commands", name="enabled", havingValue="true")
class ReconciliationConfiguration {
    @Bean CorrectionCommand.Operation reconciliationDiscover(ReconciliationContext contexts, CorrectionStore store, ReconciliationRequestStore requests) {
        return new ReconciliationDiscover(contexts, store, requests);
    }
    @Bean CorrectionCommand.Operation reconciliationSubmit(ReconciliationContext contexts, CorrectionStore store,
        ReconciliationRequestStore requests, ReconciliationAttachments attachments) {
        return new ReconciliationSubmit(contexts, store, requests, attachments);
    }
    @Bean CorrectionCommand.Operation reconciliationApprove(ReconciliationContext contexts, CorrectionStore store,
        ReconciliationRequestStore requests, ReconciliationAttachments attachments, ReconciliationPosting posting) {
        return new ReconciliationApprove(contexts, store, requests, attachments, posting);
    }
}
