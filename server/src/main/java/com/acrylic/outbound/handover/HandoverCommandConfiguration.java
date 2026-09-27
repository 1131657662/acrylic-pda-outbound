package com.acrylic.outbound.handover;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Opt-in assembly for the R07 handover commands.
 *
 * <p>Production ships with no handover operation bean: every kind then answers
 * {@code HANDOVER_COMMAND_NOT_OPEN} and no handover case can be created, approved, prepared or activated.
 * Setting {@code outbound.handover.commands.enabled=true} is an explicit operator decision, not a test result.
 * Even when enabled, the signed receipt keeps all five capability flags false, so approving or activating a
 * handover never releases stock, resumes scanning or allows local deletion.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "outbound.handover.commands", name = "enabled", havingValue = "true")
public class HandoverCommandConfiguration {

    @Bean
    HandoverCommand.Operation handoverRequest(HandoverStore store) {
        return new HandoverOperations.Request(store);
    }

    @Bean
    HandoverCommand.Operation handoverApprove(HandoverStore store) {
        return new HandoverOperations.Approve(store);
    }
}
