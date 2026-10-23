package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;

/** Test-only explicit target; each child independently checks its actual dedicated server. */
final class E2aSplitCopyTarget {
    private static final String SQL = "SELECT @@server_uuid AS uuid,DATABASE() AS schemaName,@@port AS port,"
        + "@@datadir AS datadir,CURRENT_USER() AS userName,@@log_bin AS logBin";
    private E2aSplitCopyTarget() { }

    static void prepare(Path home, E2aArchiveRestoreTarget target) throws Exception {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(target.url, target.user, target.password));
        target.requireIdentity(jdbc);
        var fact = identity(jdbc.queryForMap(SQL));
        Files.writeString(home.resolve("cross-instance-target.json"), fact.toPrettyString(), StandardOpenOption.CREATE_NEW);
    }

    static void configure(ArrayList<String> options, E2aArchiveRestoreTarget target) {
        var replacements = Map.of("--spring.datasource.url=", target.url,
            "--spring.datasource.username=", target.user, "--spring.datasource.password=", target.password);
        options.replaceAll(option -> replacements.entrySet().stream()
            .filter(entry -> option.startsWith(entry.getKey())).findFirst()
            .map(entry -> entry.getKey() + entry.getValue()).orElse(option));
    }

    static void require(Path home, ArrayList<String> options, String role) throws Exception {
        assertThat(System.getenv("E2A_COPY_VERIFY")).isEqualTo("true");
        String run = System.getenv("E2A_COPY_RUN");
        assertThat(run).matches("[1-9][0-9]{0,3}").isNotEqualTo(System.getenv("E2A_CROSS_RUN"));
        int port = 22000 + Integer.parseInt(run);
        assertThat(System.getenv("E2A_COPY_PORT")).isEqualTo(Integer.toString(port));
        String user = "e2a_cross_20260925_" + run, database = "acrylic_e2a_cross_20260925_" + run + "_restore";
        String url = "jdbc:mysql://127.0.0.1:" + port + "/" + database
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        String password = System.getenv("E2A_COPY_PASSWORD");
        assertThat(password).matches("[0-9a-f]{64}");
        assertThat(options).contains("--spring.datasource.url=" + url, "--spring.datasource.username=" + user,
            "--spring.datasource.password=" + password, "--outbound.storage=" + home.getParent().getParent().resolve("restored/storage"));
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        var actual = identity(jdbc.queryForMap(SQL));
        assertThat(actual).isEqualTo(SubmissionJson.parse(Files.readString(home.resolve("cross-instance-target.json"))));
        assertThat(actual.path("schemaName").asText()).isEqualTo(database);
        assertThat(actual.path("userName").asText()).isEqualTo(user + "@127.0.0.1");
        assertThat(actual.path("port").asInt()).isEqualTo(port);
        assertThat(actual.path("logBin").asInt()).isEqualTo(1);
        Path attempt = E2aWorktree.ROOT.resolve(".local/e2a-cross-20260925-" + run);
        assertThat(Path.of(actual.path("datadir").asText()).toRealPath()).isEqualTo(attempt.resolve("data").toRealPath());
        var receipt = SubmissionJson.object().put("pid", ProcessHandle.current().pid()).put("role", role);
        receipt.set("database", actual);
        Files.writeString(home.resolve(role + "-database-identity.json"), receipt.toPrettyString(), StandardOpenOption.CREATE_NEW);
    }

    static com.fasterxml.jackson.databind.JsonNode identity(Map<String, ?> row) throws Exception {
        return SubmissionJson.parse(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(row));
    }
}
