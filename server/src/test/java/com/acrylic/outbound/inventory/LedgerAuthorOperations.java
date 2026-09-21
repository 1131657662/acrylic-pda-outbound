package com.acrylic.outbound.inventory;

import com.acrylic.outbound.temporary.command.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class LedgerAuthorOperations {
    @Bean CommandOperation normal() { return new Synthetic("NORMAL_CONFIRM"); }
    @Bean CommandOperation shortApproval() { return new Synthetic("SHORT_APPROVE"); }
    private record Synthetic(String commandType) implements CommandOperation {
        @Override public void apply(CommandContext context) { /* No business decision, accounting or stock side effect. */ }
    }
}
