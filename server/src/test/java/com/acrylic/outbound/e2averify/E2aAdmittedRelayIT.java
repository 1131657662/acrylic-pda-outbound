package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.RecoveryRelayInput;
import com.acrylic.outbound.recovery.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.*;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.ConfigurableApplicationContext;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class E2aAdmittedRelayIT {
    @Test void actualRelayStoresOriginalWithoutBusinessEffectsAndRetiresFileRoot() throws Exception {
        var profile=new E2aCrossSourceProfile();var raw=profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(raw,"34");E2aV34Seed.seed(raw,profile.attempt.resolve("storage"));
        profile.migrate(raw);E2aCrossSourceHttpSetup.signing(profile.attempt);
        RecoveryRelayInput input;
        try(var web=start(profile,null,false)) {
            input=RecoveryRelayInput.seed(web);var scenario=new RecoveryRelayScenario(profile,raw,input,web,"disabled");
            scenario.http.login(input.username());
            assertThat(scenario.upload(scenario.http,input.archive(),java.util.UUID.randomUUID().toString(),409)
                .path("code").asText()).isEqualTo("ORIGINAL_RELAY_DISABLED");
            var viewer=scenario.peer("viewer");viewer.login(input.viewer());
            assertThat(scenario.upload(viewer,input.archive(),java.util.UUID.randomUUID().toString(),409)
                .path("code").asText()).isEqualTo("ORIGINAL_RELAY_DISABLED");
        }
        var before=business(raw);
        try(var web=start(profile,null,true)) { new RecoveryRelayScenario(profile,raw,input,web,"off").positive(); }
        admitted(profile,raw,input);
        assertThat(business(raw)).isEqualTo(before);
        Files.writeString(profile.attempt.resolve("relay-business.json"),before.toPrettyString(),StandardOpenOption.CREATE_NEW);
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "feature default-off; actual off/P5 ZIP/read/download/replay/conflict/permissions/concurrent; held file root; tail SQL rollback; revoke; no submission or accounting; successor");
    }

    private void admitted(E2aCrossSourceProfile profile,org.springframework.jdbc.core.JdbcTemplate raw,
            RecoveryRelayInput input) throws Exception {
        var p5=new RecoveryRetirementApplicationFixture(profile.attempt);
        try(var web=start(profile,p5.options(),true)) {
            var f=new RecoveryRelayScenario(profile,raw,input,web,"p5");f.positive();f.trustRejections();
            new RecoveryRelayFaults(f).verify();
            await().atMost(Duration.ofSeconds(15)).untilAsserted(()->
                assertThat(web.getBean(RecoveryOperationRegistry.class).liveRoots()).isZero());
            f.probe("FREE","after-drain");
            Files.copy(profile.attempt.resolve("private/p5-retirement/p5/leases/index.json"),
                profile.attempt.resolve("private/p5-retirement/drained-index.json"));
        }
        p5.nextProcessStarts();
    }
    private static com.fasterxml.jackson.databind.JsonNode business(org.springframework.jdbc.core.JdbcTemplate raw) {
        var facts=SubmissionJson.object();
        for(String table:java.util.List.of("submission","dispatch_record","inventory_ledger"))
            facts.put(table,raw.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class));
        return facts;
    }
    private static ConfigurableApplicationContext start(E2aCrossSourceProfile profile,String[] admission,boolean relay) {
        var options=new java.util.ArrayList<String>(java.util.List.of("--server.servlet.session.cookie.secure=false",
            "--outbound.rescue.relay-enabled="+relay));
        if(admission!=null)options.addAll(java.util.List.of(admission));
        return E2aCrossSourceHttpIT.start(profile,options.toArray(String[]::new));
    }
}
