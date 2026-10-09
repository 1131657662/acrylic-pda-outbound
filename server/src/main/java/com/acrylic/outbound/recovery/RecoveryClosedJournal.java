package com.acrylic.outbound.recovery;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Append-only local transaction witnesses. An unfinished transaction survives a process crash. */
final class RecoveryClosedJournal {
    record Operation(long ordinal, UUID id, String startHash) { }
    private final Path directory;
    private final String authorityPin;
    private final RecoveryClosedWitness witness;

    RecoveryClosedJournal(Path root, String authorityPin, RecoveryClosedWitness witness) {
        directory = root.resolve("transactions"); this.authorityPin = authorityPin; this.witness = witness;
    }

    void check(Operation active) throws IOException {
        byte[] index = witness.read();
        int size = count(index);
        for (long ordinal = 1; ordinal <= (size + 1) / 2; ordinal++) {
            var start = RecoveryControlHistory.read(path(ordinal, "start"));
            fields(start, "formatVersion authoritySha256 ordinal operationId"); version(start);
            RecoveryClosedWitness.matches(index, (int)(ordinal * 2 - 2), hash(start));
            require(authorityPin.equals(text(start, "authoritySha256")) && revision(start, "ordinal") == ordinal,
                "CONTROL_CLOSED_TRANSACTION_JOURNAL");
            var operation = new Operation(ordinal, uuid(start, "operationId"), hash(start));
            if (active != null && active.equals(operation)) {
                require(ordinal * 2 - 1 == size, "CONTROL_CLOSED_TRANSACTION_JOURNAL"); return;
            }
            require(Files.exists(path(ordinal, "complete"), LinkOption.NOFOLLOW_LINKS),
                "CONTROL_TRANSACTION_OUTCOME_UNCONFIRMED");
            RecoveryClosedWitness.matches(index, (int)(ordinal * 2 - 1), completion(operation));
        }
        require(active == null, "CONTROL_CLOSED_TRANSACTION_JOURNAL");
    }

    Operation begin() throws IOException {
        check(null);
        int records = RecoveryClosedWitness.count(witness.read());
        long ordinal = records / 2L + 1;
        require(ordinal <= 100000, "CONTROL_CLOSED_TRANSACTION_LIMIT");
        var value = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("formatVersion", 1)
            .put("authoritySha256", authorityPin).put("ordinal", Long.toString(ordinal))
            .put("operationId", UUID.randomUUID().toString());
        witness.append(records, hash(value));
        RecoveryClosedDisk.write(path(ordinal, "start"), RecoveryControlJson.encode(value));
        return new Operation(ordinal, uuid(value, "operationId"), hash(value));
    }

    void complete(Operation operation, int status) throws IOException {
        check(operation);
        require(status == 0 || status == 1, "CONTROL_TRANSACTION_OUTCOME_UNCONFIRMED");
        var value = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("formatVersion", 1)
            .put("operationId", operation.id().toString()).put("startSha256", operation.startHash())
            .put("outcome", status == 0 ? "COMMITTED" : "ROLLED_BACK");
        witness.append((int)(operation.ordinal() * 2 - 1), hash(value));
        RecoveryClosedDisk.write(path(operation.ordinal(), "complete"), RecoveryControlJson.encode(value));
    }

    private String completion(Operation operation) throws IOException {
        var value = RecoveryControlHistory.read(path(operation.ordinal(), "complete"));
        fields(value, "formatVersion operationId startSha256 outcome"); version(value);
        require(operation.id().equals(uuid(value, "operationId"))
            && operation.startHash().equals(text(value, "startSha256"))
            && Set.of("COMMITTED", "ROLLED_BACK").contains(text(value, "outcome")), "CONTROL_CLOSED_TRANSACTION_JOURNAL");
        return hash(value);
    }

    private int count(byte[] witness) throws IOException {
        require(Files.isDirectory(RecoveryControlPaths.checked(directory), LinkOption.NOFOLLOW_LINKS),
            "CONTROL_CLOSED_TRANSACTION_JOURNAL");
        var files = new ArrayList<String>();
        try (var entries = Files.newDirectoryStream(directory)) {
            for (Path entry : entries) {
                require(files.size() < 200000, "CONTROL_CLOSED_TRANSACTION_LIMIT"); files.add(entry.getFileName().toString());
            }
        }
        files.sort(String::compareTo);
        int expected = RecoveryClosedWitness.count(witness);
        require(files.size() == expected || (expected % 2 == 0 && files.size() == expected - 1),
            "CONTROL_CLOSED_TRANSACTION_JOURNAL");
        // Canonical pairs are start/complete, regardless of the filenames' lexical suffix order.
        for (int index = 0; index < files.size(); index++) {
            long ordinal = index / 2L + 1;
            require(files.get(index).equals(path(ordinal, index % 2 == 0 && index + 1 < files.size()
                ? "complete" : "start").getFileName().toString()), "CONTROL_CLOSED_TRANSACTION_JOURNAL");
        }
        return expected;
    }

    private Path path(long ordinal, String kind) {
        return directory.resolve(String.format(Locale.ROOT, "%018d.%s.json", ordinal, kind));
    }

}
