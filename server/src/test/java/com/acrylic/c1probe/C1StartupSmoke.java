package com.acrylic.c1probe;

import java.nio.file.*;
import org.flywaydb.core.Flyway;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import static org.assertj.core.api.Assertions.*;

/** Standalone full-application startup check; never runs the C1 registration IT. */
public final class C1StartupSmoke {
    private static final Path ROOT = Path.of("D:/acrylic-pda-outbound");
    private static final Path BASE = ROOT.resolve(".local/r09-c1-smoke-20260928-1");
    private static final String SCHEMA = "acrylic_r09_c1_smoke_20260928_1";

    public static void main(String[] args) throws Exception {
        var jdbc = guardedDatabase();
        Files.createFile(BASE.resolve("smoke-started.marker"));
        Flyway.configure().dataSource(jdbc.getDataSource()).load().migrate();
        Files.createDirectory(BASE.resolve("initial-authority"));
        Files.write(BASE.resolve("initial-authority/coordinator.lock"), new byte[]{'0'}, StandardOpenOption.CREATE_NEW);
        try (var context = new SpringApplication(com.acrylic.outbound.OutboundApplication.class, C1HttpProbe.class)
                .run("--server.address=127.0.0.1", "--server.port=19437", "--spring.flyway.enabled=false",
                    "--outbound.worker.enabled=false", "--outbound.bootstrap.enabled=false", "--c1.test.base=" + BASE)) {
            assertThat(context.isActive()).isTrue();
            assertThat(((ServletWebServerApplicationContext) context).getWebServer().getPort()).isEqualTo(19437);
            assertThat(context.getBeansOfType(C1HttpController.class)).hasSize(1);
            var mappings = context.getBean(RequestMappingHandlerMapping.class).getHandlerMethods();
            assertThat(mappings.values().stream().filter(m -> m.getBeanType() == C1HttpController.class)).hasSize(2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recovery_initial_normal", Integer.class)).isZero();
            Files.writeString(BASE.resolve("smoke-started-ok.txt"), "Full Boot context active; one C1 controller; two mappings; zero registration rows.\n");
            System.out.println("C1_SMOKE_STARTED_OK");
        }
        Files.writeString(BASE.resolve("smoke-completed.marker"), "Context started and closed normally; no C1 HTTP/registration executed.\n");
        System.out.println("C1_SMOKE_COMPLETED_OK");
    }

    private static JdbcTemplate guardedDatabase() throws Exception {
        assertThat(Path.of("").toRealPath()).isEqualTo(ROOT.resolve("server").toRealPath());
        assertThat(BASE.toRealPath()).startsWith(ROOT.toRealPath());
        assertThat(BASE.resolve("smoke-started.marker")).doesNotExist();
        String url = "jdbc:mysql://127.0.0.1:19330/" + SCHEMA;
        assertThat(System.getenv("OUTBOUND_DB_URL")).isEqualTo(url);
        assertThat(System.getenv("OUTBOUND_DB_USER")).isEqualTo("r09_c1_smoke_1");
        assertThat(System.getenv("OUTBOUND_DB_PASSWORD")).matches("[0-9a-f]{64}");
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(url,
            System.getenv("OUTBOUND_DB_USER"), System.getenv("OUTBOUND_DB_PASSWORD")));
        assertThat(jdbc.queryForObject("SELECT @@datadir", String.class).replace('\\', '/'))
            .isEqualToIgnoringCase(BASE.resolve("data").toString().replace('\\', '/') + "/");
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo(SCHEMA);
        assertThat(jdbc.queryForObject("SELECT CURRENT_USER()", String.class)).isEqualTo("r09_c1_smoke_1@127.0.0.1");
        assertThat(jdbc.queryForObject("SELECT @@port", Integer.class)).isEqualTo(19330);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()", Integer.class)).isZero();
        return jdbc;
    }
}
