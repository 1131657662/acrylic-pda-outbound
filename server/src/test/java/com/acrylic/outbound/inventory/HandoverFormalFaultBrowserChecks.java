package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

/** Actual FORMAL product form with real supervisor HTTP; generated source and custody descriptions are fixtures. */
final class HandoverFormalFaultBrowserChecks {
    static byte[] execute(HandoverFormalFaultSource f) throws Exception {
        Path output = Files.createDirectory(f.source.directory.resolve("formal-fault-browser"));
        Path input = f.source.directory.resolve("formal-fault-browser-private.json");
        fixture(f, input);
        var builder = new ProcessBuilder("F:/Program Files/nodejs/node.exe", "node_modules/@playwright/test/cli.js",
            "test", "--config=handover-fault.config.ts");
        builder.directory(Path.of("D:/acrylic-pda-outbound/web").toFile());
        builder.environment().put("HANDOVER_FAULT_VERIFY", "true");
        builder.environment().put("HANDOVER_FAULT_FIXTURE", input.toString());
        builder.environment().put("HANDOVER_FAULT_OUTPUT", output.toString());
        builder.environment().put("OUTBOUND_API_PROXY", "http://127.0.0.1:19434");
        builder.environment().remove("E2A_CROSS_PASSWORD");
        builder.redirectErrorStream(true).redirectOutput(output.resolve("playwright.log").toFile());
        var process = builder.start();
        Files.writeString(output.resolve("process.pid"), Long.toString(process.pid()));
        assertThat(process.waitFor(4, TimeUnit.MINUTES)).as("Inspect retained FORMAL browser PID/log").isTrue();
        assertThat(process.exitValue()).as("See retained FORMAL browser log and trace").isZero();
        var actual = SubmissionJson.parse(Files.readAllBytes(output.resolve("browser-command.json")));
        for (String field : java.util.List.of("warehouseId", "caseId", "sourceDeviceId", "expected", "archivedEvidence", "physicalControl"))
            assertThat(actual.path(field)).isEqualTo(f.command.path(field));
        f.command.removeAll(); f.command.setAll((ObjectNode) actual);
        return Files.readAllBytes(output.resolve("browser-receipt.json"));
    }

    private static void fixture(HandoverFormalFaultSource f, Path input) throws Exception {
        var format = new java.time.format.DateTimeFormatterBuilder().appendInstant(3).toFormatter();
        ((ObjectNode) f.command.path("physicalControl")).put("controlledAt",
            format.format(java.time.Instant.parse(f.command.path("physicalControl").path("controlledAt").asText())));
        var value = SubmissionJson.object().put("username", f.workflow.web.username()).put("password", "Test-password-123");
        value.set("command", f.command);
        Files.writeString(input, SubmissionJson.canonical(value), StandardOpenOption.CREATE_NEW);
    }
}
