package com.acrylic.outbound.ledgerverify;

import com.acrylic.outbound.inventory.LedgerVerifySeed;
import java.nio.file.Files;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="LEDGER_VERIFY_RETAINED",matches="true")
class LedgerRetainedSeedIT extends LedgerVerifyFixture {
    @Test void firstCompleteGetsExplicitNewBaselineAndGenuineChallengeRelogin() throws Exception {
        var input=json.readTree(Files.readAllBytes(LedgerVerifyEnvironment.evidence("retained-source.json")));
        String id=input.path("submissionId").asText();var facts=new LedgerOriginalFacts(jdbc);var before=facts.capture(id);
        facts.save("retained-baseline-now.json",Map.of("establishedAt",java.time.Instant.now().toString(),"facts",before));
        var sessions=jdbc.queryForList("SELECT BIN_TO_UUID(id) FROM pda_session WHERE device_id=(SELECT freeze_device_id FROM submission WHERE id=UUID_TO_BIN(?))",String.class,id);
        var tokens=LedgerVerifySeed.readRetained(context,id);
        assertThat(sessions).doesNotContain(tokens.path("sessionId").asText());assertThat(facts.capture(id)).isEqualTo(before);
        facts.save("retained-after-relogin.json",Map.of("submissionId",id,"sessionId",tokens.path("sessionId").asText(),"facts",facts.capture(id)));
    }
}
