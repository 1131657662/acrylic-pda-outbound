package com.acrylic.outbound.ledgerverify;

import com.acrylic.outbound.inventory.LedgerVerifySeed;
import java.nio.file.Files;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest(properties={"spring.flyway.enabled=true","spring.flyway.target=34"}) @AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="LEDGER_VERIFY_SEED",matches="true")
class LedgerSeedIT {
    @Autowired ApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @DynamicPropertySource static void guard(DynamicPropertyRegistry properties) {
        var jdbc=LedgerVerifyEnvironment.jdbc();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()",Integer.class))
            .as("A seed attempt requires its new, empty, registered schema").isZero();
        if(Files.exists(LedgerVerifyEnvironment.evidence("seed-started.json")))throw new IllegalStateException("Seed is single use; retain its previous evidence");
    }

    @Test void createsOriginalTemporaryFactsWithRetainedDeviceKeys() throws Exception {
        var snapshots=new LedgerOriginalFacts(jdbc);snapshots.save("seed-started.json",Map.of("startedAt",java.time.Instant.now().toString()));
        assertThat(jdbc.queryForObject("SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success=1",Integer.class)).isEqualTo(34);
        var manifest=new TreeMap<String,Object>();
        for(String scenario:List.of("complete","abort-empty","abort-partial","abort-complete")) {
            var seed=new LedgerVerifySeed(context,scenario.equals("abort-partial")?400:0);
            if(scenario.equals("complete") || scenario.equals("abort-complete"))seed.submit();
            if(scenario.equals("abort-partial"))seed.partialUpload();
            if(!scenario.equals("complete"))seed.abort();
            var original=snapshots.capture(seed.id());String oldSession=seed.tokens().path("sessionId").asText();
            seed.relogin();seed.pda(get(seed.base()));
            assertThat(seed.tokens().path("sessionId").asText()).isNotEqualTo(oldSession);
            assertThat(snapshots.capture(seed.id())).isEqualTo(original);
            snapshots.save("seed-"+scenario+".json",original);
            manifest.put(scenario,Map.of("submissionId",seed.id(),"warehouseId",seed.warehouse(),"userId",seed.user(),"deviceId",seed.device(),
                "body",seed.body,"tokens",seed.tokens(),"originalSessionId",oldSession));
        }
        snapshots.save("seed-manifest.json",manifest);
    }
}
