package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

/** A separate test JVM fetches the original receipts, not reconstructed DTOs. */
public final class TemporaryExclusionRestartChecks {
    private TemporaryExclusionRestartChecks() { }
    public static void verify(Path attempt,JdbcTemplate jdbc) throws Exception {
        var http=new TemporaryExclusionHttp();
        var input=SubmissionJson.parse(Files.readAllBytes(attempt.resolve("exclusion-restart-input.json")));
        http.login(input.path("username").asText());
        byte[] original=Files.readAllBytes(attempt.resolve("exclusion-original.json"));
        var command=SubmissionJson.parse(Files.readAllBytes(attempt.resolve("exclusion-command.json")));
        long before=jdbc.queryForObject("SELECT COUNT(*) FROM temporary_command_receipt",Long.class);
        assertThat(http.original(input.path("originalPath").asText())).containsExactly(original);
        assertThat(http.post(command)).containsExactly(original);
        var selection=SubmissionJson.parse(Files.readAllBytes(attempt.resolve("selection-restart-input.json")));
        http.login(selection.path("username").asText());
        for(var item:selection.path("originals"))
            assertThat(http.original(item.path("path").asText())).containsExactly(Files.readAllBytes(attempt.resolve(item.path("file").asText())));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM temporary_command_receipt",Long.class)).isEqualTo(before);
    }
}
