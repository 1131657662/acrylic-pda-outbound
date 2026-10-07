package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.OutboundApplication;
import com.acrylic.outbound.submissions.SubmissionFiles;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aCrossSourceHttpIT {
    @Test void nonemptyMysqlAndRealHttpKeepOtherSourceFactsOutOfReservations() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        requireHttpPortFree(); Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34");
        var seed = E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submission", Integer.class)).isGreaterThan(0);
        profile.migrate(jdbc, "36");
        var fixture = new E2aCrossSourceFixture(jdbc, new SubmissionFiles(profile.attempt.resolve("storage").toString()), seed);
        fixture.seed(); E2aCrossSourceChecks.verify(fixture);
        Files.writeString(profile.attempt.resolve("sql-checks-completed.marker"), "missing/forged/cross-warehouse rejected; no K writes\n");
        var setup = new E2aCrossSourceHttpSetup(fixture); E2aCrossSourceHttpSetup.signing(profile.attempt);
        var wire = new E2aCrossSourceWire(fixture, setup.items());
        Files.createDirectory(profile.attempt.resolve("http"));
        try (var context = start(profile)) {
            E2aCrossSourceHttpChecks.verify(new E2aCrossSourceHttp(wire, setup.token, profile.attempt.resolve("http")), setup);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "V34 nonempty -> V36; real HTTP OPEN/RAW/PAGE/STOP; incomplete synthetic audit prefix is rejected before VERIFY writes\n");
    }

    static ConfigurableApplicationContext start(E2aCrossSourceProfile profile,String... overrides) {
        return start(profile,new Class<?>[0],overrides);
    }

    /** Same real entry plus explicitly installed test configuration classes. */
    static ConfigurableApplicationContext start(E2aCrossSourceProfile profile,Class<?>[] sources,String... overrides) {
        var options=new java.util.LinkedHashMap<String,String>();
        java.util.stream.Stream.concat(java.util.stream.Stream.of(
            "--server.address=127.0.0.1", "--server.port=19434", "--spring.flyway.enabled=false",
            "--spring.datasource.url=" + profile.url, "--spring.datasource.username=" + profile.user,
            "--spring.datasource.password=" + System.getenv("E2A_CROSS_PASSWORD"),
            "--outbound.storage=" + profile.attempt.resolve("storage"),
            "--outbound.signing.private-key=" + profile.attempt.resolve("private/server-private.der"),
            "--outbound.signing.public-key=" + profile.attempt.resolve("private/server-public.der"),
            "--outbound.worker.enabled=false", "--outbound.bootstrap.enabled=false",
            // The admitted entry refuses to start while the framework session-expiry writer is
            // enabled: every start of this harness has to carry the disabled value (guard, S5).
            "--spring.session.jdbc.cleanup-cron=-"),java.util.Arrays.stream(overrides))
            .forEach(option->{int split=option.indexOf('=');if(split<3)throw new IllegalArgumentException("Test option requires a value");
                options.put(option.substring(0,split),option.substring(split+1));});
        var types=new java.util.ArrayList<Class<?>>();types.add(OutboundApplication.class);types.addAll(java.util.List.of(sources));
        return new SpringApplication(types.toArray(Class[]::new)).run(options.entrySet().stream()
            .map(entry->entry.getKey()+"="+entry.getValue()).toArray(String[]::new));
    }

    private static void requireHttpPortFree() throws Exception {
        try (var socket = new ServerSocket()) { socket.bind(new InetSocketAddress("127.0.0.1", 19434)); }
    }
}
