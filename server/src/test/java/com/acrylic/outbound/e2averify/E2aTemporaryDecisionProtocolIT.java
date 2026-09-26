package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.TemporaryDecisionVectors;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class E2aTemporaryDecisionProtocolIT {
    @Test void independentConsumerVerifiesRecordedDecisionsAndRealHttpOriginals() throws Exception {
        var profile=new E2aCrossSourceProfile();var jdbc=profile.connect();Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc,"34");E2aV34Seed.seed(jdbc,profile.attempt.resolve("storage"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submission",Integer.class)).isPositive();
        profile.migrate(jdbc,"37");E2aCrossSourceHttpSetup.signing(profile.attempt);
        try(var context=E2aCrossSourceHttpIT.start(profile,"--server.servlet.session.cookie.secure=false")) {
            new TemporaryDecisionVectors((WebApplicationContext)context).write(profile.attempt);
            consume(profile.attempt);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"Independent recorded decision consumer; isolated real transactions/HTTP; no product handler\n");
    }

    private void consume(Path attempt) throws Exception {
        var builder=new ProcessBuilder("C:/Python314/python.exe","-W","error::ResourceWarning","-m","unittest","discover",
            "-s","docs/parallel/R06_LEDGER_PROTOCOL","-p","test_decision*.py","-v");
        builder.directory(Path.of("D:/acrylic-pda-outbound").toFile());builder.environment().remove("E2A_CROSS_PASSWORD");
        builder.environment().put("R06_DECISION_VECTORS",attempt.resolve("decision-vectors.json").toString());
        builder.redirectErrorStream(true).redirectOutput(attempt.resolve("decision-consumer.log").toFile());
        var process=builder.start();assertThat(process.waitFor(60,TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).as("See decision-consumer.log").isZero();
    }
}
