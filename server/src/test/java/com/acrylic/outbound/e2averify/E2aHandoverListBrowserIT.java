package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.handover.HandoverCasePageController;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY", matches="true")
@EnabledIfEnvironmentVariable(named="HANDOVER_LIST_VERIFY", matches="true")
class E2aHandoverListBrowserIT {
    @Test void discoversCasesAndReadsDetailsThroughTheBrowserWithoutBusinessWrites() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34"); E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        profile.migrate(jdbc, "52"); E2aCrossSourceHttpSetup.signing(profile.attempt);
        try (var app = E2aCrossSourceHttpIT.start(profile, "--server.servlet.session.cookie.secure=false")) {
            assertThat(app.getBeansOfType(HandoverCasePageController.class)).isEmpty();
        }
        try (var app = E2aCrossSourceHttpIT.start(profile, "--server.servlet.session.cookie.secure=false",
                "--outbound.handover.commands.enabled=true")) {
            var fixture = new HandoverListFixture(app, profile.attempt);
            new HandoverListChecks(fixture).verify(); fixture.browserFixture();
            var facts = new HandoverRollbackFacts(fixture.f, jdbc); var before = facts.snapshot();
            Files.writeString(profile.attempt.resolve("browser-before.json"), before.toPrettyString());
            browser(profile.attempt);
            var after = facts.snapshot(); Files.writeString(profile.attempt.resolve("browser-after.json"), after.toPrettyString());
            HandoverListBrowserFacts.unchanged(before, after, fixture.f.requester.id());
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"), "Real MySQL/HTTP pagination and browser read-only workflow completed");
    }

    private void browser(Path attempt) throws Exception {
        Path root = Path.of(System.getenv("E2A_CROSS_ROOT"));
        var builder = new ProcessBuilder("F:/Program Files/nodejs/node.exe", "node_modules/@playwright/test/cli.js",
            "test", "--config=handover.config.ts").directory(root.resolve("web").toFile());
        builder.environment().put("HANDOVER_BROWSER_FIXTURE", attempt.resolve("browser-fixture.json").toString());
        builder.environment().put("HANDOVER_BROWSER_OUTPUT", attempt.resolve("browser").toString());
        builder.environment().put("OUTBOUND_API_PROXY", "http://127.0.0.1:19434");
        builder.environment().put("OUTBOUND_WEB_URL", "http://127.0.0.1:5173");
        builder.redirectErrorStream(true).redirectOutput(attempt.resolve("browser.log").toFile());
        var process = builder.start();
        boolean finished = process.waitFor(5, TimeUnit.MINUTES);
        if (!finished) process.destroyForcibly();
        assertThat(finished).as("browser timeout; no retry").isTrue();
        assertThat(process.exitValue()).as("browser acceptance exit; retain all failures").isZero();
    }
}
