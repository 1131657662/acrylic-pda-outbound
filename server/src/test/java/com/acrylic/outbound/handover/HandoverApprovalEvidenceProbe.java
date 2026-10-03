package com.acrylic.outbound.handover;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;

/** Test bridge for the package-private consumer; this is not approval HTTP or supervisor authorization evidence. */
public final class HandoverApprovalEvidenceProbe {
    private HandoverApprovalEvidenceProbe() { }
    public static JsonNode consume(ApplicationContext app, UUID kase, long high) {
        var transaction = new TransactionTemplate(app.getBean(PlatformTransactionManager.class));
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return transaction.execute(status -> {
            var store = app.getBean(HandoverStore.class); var hint = store.caseFacts(kase);
            var locks = app.getBean(HandoverCommand.Locks.class);
            locks.scope(UUID.fromString(hint.path("scopeId").asText())); locks.kase(kase);
            var facts = store.caseFacts(kase);
            app.getBean(HandoverSourceGuard.class).lock(facts);
            var result = app.getBean(HandoverApprovalEvidence.class).verify(facts, high);
            assertThat(result.receipt().path("source").path("submissionId")).isEqualTo(facts.path("submissionId"));
            result.receipt().path("capabilities").forEach(flag -> assertThat(flag.asBoolean()).isFalse());
            return result.body();
        });
    }
}
