package com.acrylic.outbound.foundationmigration;

import com.acrylic.outbound.inventory.FoundationLegacySeed;
import com.acrylic.outbound.submissions.SubmissionArchiveProofs;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.*;

/** Separate inspection of the retained V34 database; never reruns a seed or the successful V34 DDL. */
@SpringBootTest(properties = "spring.flyway.enabled=false")
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "FOUNDATION_PRESERVED", matches = "true")
class FoundationPreservationIT {
    @Autowired ApplicationContext context;
    @Autowired SubmissionArchiveProofs proofs;
    @DynamicPropertySource static void guard(DynamicPropertyRegistry properties) {
        var jdbc = FoundationEnvironment.jdbc();
        assertThat(FoundationEnvironment.database()).isEqualTo(FoundationEnvironment.DATABASE);
        assertThat(jdbc.queryForObject("SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success=1", Integer.class)).isEqualTo(34);
    }

    @Test void retainedUpgradePreservesOldFactsAndServiceResponseDtos() throws Exception {
        var jdbc = FoundationEnvironment.jdbc();
        new LegacySnapshot(jdbc).assertPreserved("v33-original.json");
        new FoundationBackfill(jdbc).verify();
        new LegacyEvidencePreflight(jdbc, proofs).verify();
        assertThat(Flyway.configure().dataSource(jdbc.getDataSource()).target("34").load().migrate().migrationsExecuted).isZero();
        new FoundationLegacySeed(context).assertStoredResponseDtos();
        new LegacySnapshot(jdbc).save("v34-preservation-verified.json");
    }
}
