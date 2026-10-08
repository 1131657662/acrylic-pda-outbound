package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

/** Fresh source + actual Web product form, not a replay-only UI fixture. */
final class HandoverFaultBrowserChecks {
    static byte[] execute(HandoverTemporaryFaultFenceChecks f, ObjectNode command) throws Exception {
        Path workspace = Path.of("D:/acrylic-pda-outbound");
        Path output = Files.createDirectory(f.directory.resolve("browser"));
        Path input = f.directory.resolve("browser-private-input.json");
        fixture(f, command, input);
        var builder = new ProcessBuilder("F:/Program Files/nodejs/node.exe", "node_modules/@playwright/test/cli.js",
            "test", "--config=handover-fault.config.ts");
        builder.directory(workspace.resolve("web").toFile());
        builder.environment().put("HANDOVER_FAULT_VERIFY", "true");
        builder.environment().put("HANDOVER_FAULT_FIXTURE", input.toString());
        builder.environment().put("HANDOVER_FAULT_OUTPUT", output.toString());
        builder.environment().put("OUTBOUND_API_PROXY", "http://127.0.0.1:19434");
        builder.environment().remove("E2A_CROSS_PASSWORD");
        builder.redirectErrorStream(true).redirectOutput(output.resolve("playwright.log").toFile());
        var process = builder.start();
        Files.writeString(output.resolve("process.pid"), Long.toString(process.pid()));
        assertThat(process.waitFor(4, TimeUnit.MINUTES)).as("Inspect retained browser PID/log before further work").isTrue();
        assertThat(process.exitValue()).as("See actual browser log and trace").isZero();
        var actual = SubmissionJson.parse(Files.readAllBytes(output.resolve("browser-command.json")));
        for (String field : java.util.List.of("warehouseId", "caseId", "sourceDeviceId", "expected", "archivedEvidence", "physicalControl"))
            assertThat(actual.path(field)).isEqualTo(command.path(field));
        command.removeAll(); command.setAll((ObjectNode) actual);
        return Files.readAllBytes(output.resolve("browser-receipt.json"));
    }

    private static void fixture(HandoverTemporaryFaultFenceChecks f, ObjectNode command, Path input) throws Exception {
        var format = new java.time.format.DateTimeFormatterBuilder().appendInstant(3).toFormatter();
        ((ObjectNode) command.path("physicalControl")).put("controlledAt",
            format.format(java.time.Instant.parse(command.path("physicalControl").path("controlledAt").asText())));
        var value = SubmissionJson.object().put("username", f.faultReviewer.username()).put("password", "Test-password-123");
        value.set("command", command);
        var attachmentFixture = f.directory.resolve("attachment-browser-fixture.json");
        if (Files.exists(attachmentFixture)) {
            value.set("attachmentFixture", SubmissionJson.parse(Files.readAllBytes(attachmentFixture)));
            value.put("attachmentDirectory", f.directory.toString());
        }
        Files.writeString(input, SubmissionJson.canonical(value), StandardOpenOption.CREATE_NEW);
    }
}
