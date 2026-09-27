package com.acrylic.outbound.correction;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Opt-in assembly for the R07-3 correction commands (design §9.2).
 *
 * <p>Production ships with no correction operation bean: every kind then answers
 * {@code CORRECTION_COMMAND_NOT_OPEN} and no correction document can be created, approved or withdrawn. Setting
 * {@code outbound.corrections.commands.enabled=true} is an explicit operator decision, not a test result.
 *
 * <p>This batch registers only CREATE (design step 3 opening: document + lines with server-recomputed targets).
 * APPROVE, WITHDRAW and the effect/result/ledger/reconciliation steps come later; until then those kinds stay
 * {@code CORRECTION_COMMAND_NOT_OPEN} even with the switch on.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "outbound.corrections.commands", name = "enabled", havingValue = "true")
public class CorrectionCommandConfiguration {

    @Bean
    CorrectionCommand.Operation correctionCreate(CorrectionStore store) {
        return new CorrectionOperations.Create(store);
    }
}
