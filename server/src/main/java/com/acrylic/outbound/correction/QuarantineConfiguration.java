package com.acrylic.outbound.correction;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(prefix="outbound.corrections.commands",name="enabled",havingValue="true")
class QuarantineConfiguration {
    @Bean CorrectionCommand.Operation quarantineDiscover(QuarantineContext context,ReconciliationContext guard,CorrectionStore store,
        ReconciliationRequestStore requests,JdbcTemplate jdbc) { return new QuarantineDiscover(context,guard,store,requests,jdbc); }
    @Bean CorrectionCommand.Operation quarantineSubmit(QuarantineContext context,ReconciliationContext guard,CorrectionStore store,
        ReconciliationRequestStore requests,ReconciliationAttachments attachments) { return new QuarantineSubmit(context,guard,store,requests,attachments); }
    @Bean CorrectionCommand.Operation quarantineApprove(QuarantineContext context,ReconciliationContext guard,CorrectionStore store,
        ReconciliationAttachments attachments,QuarantinePosting posting) { return new QuarantineApprove(context,guard,store,attachments,posting); }
}
