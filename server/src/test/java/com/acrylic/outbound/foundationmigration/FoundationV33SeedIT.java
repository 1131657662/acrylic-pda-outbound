package com.acrylic.outbound.foundationmigration;

import com.acrylic.outbound.inventory.FoundationLegacySeed;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {"spring.flyway.target=33", "spring.flyway.enabled=true"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "FOUNDATION_VERIFY", matches = "true")
class FoundationV33SeedIT {
    @Autowired ApplicationContext context;

    @DynamicPropertySource static void guard(DynamicPropertyRegistry properties) {
        FoundationEnvironment.require();
        var jdbc = FoundationEnvironment.jdbc();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()", Integer.class))
                .as("Seed is single-use and only runs on a new empty schema").isZero();
    }

    @Test void createsNonemptyV33HistoryAndFreezesOriginalValues() throws Exception {
        new FoundationLegacySeed(context).generate();
        new LegacySnapshot(FoundationEnvironment.jdbc()).save("v33-original.json");
    }
}
