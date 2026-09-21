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

@SpringBootTest(properties = "spring.flyway.enabled=false")
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "FOUNDATION_UPGRADE", matches = "true")
class FoundationUpgradeIT {
    @Autowired ApplicationContext context;
    @Autowired SubmissionArchiveProofs proofs;

    @DynamicPropertySource static void guard(DynamicPropertyRegistry properties) {
        FoundationEnvironment.require();
        assertThat(FoundationEnvironment.database()).isEqualTo(FoundationEnvironment.DATABASE);
        var jdbc = FoundationEnvironment.jdbc();
        assertThat(jdbc.queryForObject("SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success=1", Integer.class)).isEqualTo(33);
    }

    @Test void upgradesRetainedHistoryWithoutRewritingOriginalFacts() throws Exception {
        var jdbc = FoundationEnvironment.jdbc();
        var history = jdbc.queryForList("SELECT * FROM flyway_schema_history WHERE CAST(version AS UNSIGNED)<=33 ORDER BY installed_rank");
        new LegacySnapshot(jdbc).assertValuesAndFiles("v33-original.json");
        new LegacySnapshot(jdbc).save("v33-before-upgrade.json");
        new LegacyEvidencePreflight(jdbc, proofs).verify();
        var flyway = Flyway.configure().dataSource(jdbc.getDataSource()).target("34").load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        var snapshot = new LegacySnapshot(jdbc);
        snapshot.assertPreserved("v33-original.json");
        new FoundationBackfill(jdbc).verify();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForList("SELECT * FROM flyway_schema_history WHERE CAST(version AS UNSIGNED)<=33 ORDER BY installed_rank")).isEqualTo(history);
        new LegacyEvidencePreflight(jdbc, proofs).verify();
        snapshot.save("v34-after-migration.json");
        new FoundationLegacySeed(context).assertStoredResponseDtos();
    }
}
