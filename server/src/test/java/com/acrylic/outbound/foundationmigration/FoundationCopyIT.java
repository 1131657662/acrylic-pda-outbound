package com.acrylic.outbound.foundationmigration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "FOUNDATION_CASE", matches = "dirty|dirty2|missing|signature|ddl|lock")
class FoundationCopyIT {
    @Test void restoresOnlyNewSyntheticSnapshotWithAllConstraintsEnabled() throws Exception {
        var jdbc = FoundationEnvironment.jdbc();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()", Integer.class)).isZero();
        Flyway.configure().dataSource(jdbc.getDataSource()).target("33").load().migrate();
        new LegacyCopy(jdbc).restore();
        new LegacySnapshot(jdbc).assertValuesAndFiles("v33-original.json");
        new LegacySnapshot(jdbc).save("v33-copy-" + System.getenv("FOUNDATION_CASE") + ".json");
    }
}
