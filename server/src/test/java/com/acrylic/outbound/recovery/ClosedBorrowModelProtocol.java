package com.acrylic.outbound.recovery;

import java.nio.file.Files;
import java.util.Locale;

/** Adds only a synthetic writer protocol to the retained MODEL closed-chain fixture. */
final class ClosedBorrowModelProtocol {
    static void install(RecoveryControlFixture fixture) throws Exception {
        Files.writeString(fixture.root.resolve("writers.lock"), "0");
        Files.createDirectory(fixture.root.resolve("intents"));
        var identity = RecoveryControlHistory.read(fixture.root.resolve("identity.json"));
        var descriptor = RecoveryControlFixture.JSON.createObjectNode().put("formatVersion", 2)
            .put("installationId", fixture.installation.toString())
            .put("identitySha256", RecoveryControlFormat.hash(identity))
            .put("fileIdentity", RecoveryWriterProtocol.fileIdentity(fixture.root.resolve("writers.lock")));
        RecoveryControlFixture.write(fixture.root.resolve("writers.json"), descriptor);
        int head = fixture.latest.path("revision").asInt();
        for (int number = 2; number <= head; number++) appendIntent(fixture, number);
        // Production readers, rather than a test-only interpretation, validate all reconstructed rows.
        fixture.read();
    }

    private static void appendIntent(RecoveryControlFixture fixture, int number) throws Exception {
        String name = String.format(Locale.ROOT, "%018d.json", number);
        var row = RecoveryControlHistory.read(fixture.root.resolve("history").resolve(name));
        var intent = RecoveryControlFixture.JSON.createObjectNode().put("formatVersion", 1)
            .put("installationId", fixture.installation.toString()).put("targetRevision", Integer.toString(number))
            .put("previousRecordSha256", row.path("previousRecordSha256").asText())
            .put("commandSha256", row.path("commandSha256").asText());
        intent.set("command", row.path("command").deepCopy());
        intent.put("intentSha256", RecoveryControlFormat.hash(intent));
        RecoveryControlFixture.write(fixture.root.resolve("intents").resolve(name), intent);
        var header = RecoveryControlFixture.JSON.createObjectNode();
        for (String key : RecoveryControlIntents.HEAD_FIELDS.split(" ")) header.set(key, intent.path(key));
        RecoveryControlFixture.write(fixture.root.resolve("admission.json"), header);
    }
}
