package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.audit.AuditWriter;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Explicit test-only fault: preserve all real writes, then abort after the audit INSERT. */
@TestConfiguration(proxyBeanMethods = false)
public class HandoverAuditFaultConfiguration {
    @Bean
    static HandoverAuditFaultPostProcessor handoverFaultAuditWriter(JdbcTemplate jdbc) {
        return new HandoverAuditFaultPostProcessor(new FaultAuditWriter(jdbc));
    }

    static final class HandoverAuditFaultPostProcessor implements BeanPostProcessor {
        private final FaultAuditWriter fault;

        HandoverAuditFaultPostProcessor(FaultAuditWriter fault) { this.fault = fault; }
        FaultAuditWriter writer() { return fault; }

        @Override public Object postProcessAfterInitialization(Object bean, String beanName) {
            if (bean instanceof AuditWriter && beanName.equals("auditWriter")) return fault;
            return bean;
        }
    }

    static final class FaultAuditWriter extends AuditWriter {
        private HandoverRollbackFacts facts;
        ObjectNode observed, raw;
        int hits;
        boolean transactionActive;

        FaultAuditWriter(JdbcTemplate jdbc) { super(jdbc); }
        void arm(HandoverRollbackFacts facts) { this.facts = facts; }
        void disarm() { this.facts = null; }

        @Override public void append(Event event, String requestId) {
            super.append(event, requestId);
            if (facts == null || !event.type().equals("HANDOVER_ACTIVATE")) return;
            hits++;
            transactionActive = TransactionSynchronizationManager.isActualTransactionActive();
            observed = facts.summary();
            raw = facts.snapshot();
            throw new InjectedAfterAudit();
        }
    }

    static final class InjectedAfterAudit extends RuntimeException {
        InjectedAfterAudit() { super("TEST_ONLY_AFTER_HANDOVER_AUDIT_INSERT"); }
    }
}
