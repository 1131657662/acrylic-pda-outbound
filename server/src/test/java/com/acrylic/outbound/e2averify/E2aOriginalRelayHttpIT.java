package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.OutboundApplication;
import com.acrylic.outbound.inventory.OriginalRelayHttpChecks;
import com.acrylic.outbound.inventory.CorrectionAccountingHttpChecks;
import com.acrylic.outbound.inventory.ExportNetCombinedChecks;
import java.nio.file.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named="O03_HTTP_VERIFY",matches="true")
class E2aOriginalRelayHttpIT {
    @Test void originalRelayConservesSubmissionAndAuditOverRealHttp() throws Exception {
        String run=System.getenv("O03_HTTP_RUN");assertThat(run).matches("[1-9][0-9]{3}");
        Path root=Path.of(System.getenv("E2A_CROSS_ROOT")).toRealPath();assertThat(root.resolve("server")).isEqualTo(Path.of("").toRealPath());
        Path attempt=root.resolve(".local/e2a-cross-20260925-"+run);Files.createFile(attempt.resolve("test-started.marker"));
        String database="acrylic_e2a_cross_20260925_"+run;
        var source=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:19331/"+database+"?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true",
            "e2a_cross_20260925_"+run,System.getenv("E2A_CROSS_PASSWORD"));var jdbc=new JdbcTemplate(source);
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).isEqualTo(database);
        assertThat(Path.of(jdbc.queryForObject("SELECT @@datadir",String.class)).toRealPath()).isEqualTo(attempt.resolve("data").toRealPath());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()",Integer.class)).isZero();
        Flyway.configure().dataSource(source).target("34").load().migrate();E2aV34Seed.seed(jdbc,attempt.resolve("storage"));
        Flyway.configure().dataSource(source).load().migrate();E2aCrossSourceHttpSetup.signing(attempt);
        try(var context=SpringApplication.run(OutboundApplication.class,options(attempt,source))) {
            OriginalRelayHttpChecks.verify((WebApplicationContext)context,attempt);
            ExportNetCombinedChecks.verify((WebApplicationContext)context,attempt);
            CorrectionAccountingHttpChecks.verify((WebApplicationContext)context,attempt);
        }
        Files.writeString(attempt.resolve("test-completed.marker"),"O03 real HTTP relay plus recalled source/export/correction accounting combination passed; synthetic registered originals");
    }
    private String[] options(Path attempt,DriverManagerDataSource source) {
        return new String[]{"--server.address=127.0.0.1","--server.port=19434","--spring.flyway.enabled=false",
            "--spring.datasource.url="+source.getUrl(),"--spring.datasource.username="+source.getUsername(),"--spring.datasource.password="+source.getPassword(),
            "--outbound.storage="+attempt.resolve("storage"),"--outbound.signing.private-key="+attempt.resolve("private/server-private.der"),
            "--outbound.signing.public-key="+attempt.resolve("private/server-public.der"),"--outbound.worker.enabled=false",
            "--outbound.bootstrap.enabled=false","--spring.session.jdbc.cleanup-cron=-","--server.servlet.session.cookie.secure=false",
            "--outbound.rescue.relay-enabled=true","--outbound.recall.fault-withdrawal-enabled=true",
            "--outbound.corrections.commands.enabled=true",
            "--outbound.exports.renderer-home="+root().resolve("report-rendering/build/install/acrylic-report-rendering"),
            "--outbound.exports.browsers="+root().resolve("report-rendering/build/browsers")};
    }
    private static Path root() { return Path.of(System.getenv("E2A_CROSS_ROOT")); }
}
