package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Offline copy of real product rotation originals; never a production custody assertion. */
record RecoveryKeyringFixture(RecoveryRekeyInspectFixture rotation, Path keyring, Path catalog, Path archive) {
    static RecoveryKeyringFixture create(Path parent) throws Exception {
        var f = RecoveryRekeyInspectFixture.rotated(parent);
        Path base = f.rotation().supply().base();
        Path archive = Files.createDirectory(base.resolve("offline-archive"));
        copy(f.backup(), archive);
        Path dir = Files.createDirectory(base.resolve("historical-keyring"));
        String oldId = sha256(f.rotation().supply().roots().anchor()), nextId = sha256(f.rotation().next());
        Files.copy(f.keys().resolve("old.bin"), dir.resolve(oldId + ".bin"));
        Files.copy(f.keys().resolve("next.bin"), dir.resolve(nextId + ".bin"));
        ObjectNode manifest = JsonNodeFactory.instance.objectNode().put("formatVersion", 1).put("purpose", "HISTORICAL_VERIFY_ONLY");
        var entries = manifest.putArray("keys");
        for (String id : List.of(oldId, nextId)) entries.addObject().put("keyId", id).put("file", id + ".bin")
            .put("retainedFrom", "2024-02-29").put("minimumRetainUntil", "2029-03-01");
        Path keyring = dir.resolve("manifest.json"); Files.writeString(keyring, canonical(manifest));
        ObjectNode catalog = JsonNodeFactory.instance.objectNode().put("formatVersion", 1).put("operation", "VERIFY_P5_REFERENCES");
        catalog.putArray("references").addObject().put("requestFile", f.rotation().request().toString())
            .put("requestSha256", sha256(Files.readAllBytes(f.rotation().request()))).put("backupDirectory", archive.toString())
            .put("inventorySha256", sha256(Files.readAllBytes(archive.resolve(RecoveryRekeyInventory.FILE))));
        Path catalogFile = base.resolve("reference-catalog.json"); Files.writeString(catalogFile, canonical(catalog));
        return new RecoveryKeyringFixture(f, keyring, catalogFile, archive);
    }

    static void copy(Path from, Path to) throws Exception {
        try (var paths = Files.walk(from)) {
            for (Path path : paths.sorted().toList()) {
                Path target = to.resolve(from.relativize(path));
                if (Files.isDirectory(path)) Files.createDirectories(target); else Files.copy(path, target);
            }
        }
    }

    List<String> command(String keyPin, String catalogPin) {
        return List.of(Objects.requireNonNull(System.getenv("P07_PYTHON")),
            Path.of(Objects.requireNonNull(System.getenv("P07_TOOLS_ROOT")), "recovery_keyring.py").toString(),
            "--keyring", keyring.toString(), "--keyring-sha256", keyPin,
            "--catalog", catalog.toString(), "--catalog-sha256", catalogPin);
    }

    JsonNode run(String scenario, int exit) throws Exception {
        return run(scenario, exit, sha256(Files.readAllBytes(keyring)), sha256(Files.readAllBytes(catalog)));
    }

    JsonNode run(String scenario, int exit, String keyPin, String catalogPin) throws Exception {
        var before = facts(); Path logs = Files.createDirectory(catalog.getParent().resolve("audit-" + scenario));
        var cmd = command(keyPin, catalogPin); Files.writeString(logs.resolve("command.txt"), String.join(" ", cmd));
        var child = new ProcessBuilder(cmd).redirectOutput(logs.resolve("stdout.json").toFile())
            .redirectError(logs.resolve("stderr.txt").toFile()).start();
        try {
            assertThat(child.waitFor(30, TimeUnit.SECONDS)).as(logs.toString()).isTrue();
            assertThat(child.exitValue()).as(logs.toString()).isEqualTo(exit);
            assertThat(Files.readString(logs.resolve("stderr.txt"))).isEmpty();
            assertThat(facts()).isEqualTo(before);
            String output = Files.readString(logs.resolve("stdout.json"));
            assertThat(output).doesNotContain(rotation.rotation().supply().anchorHex(), hex(rotation.rotation().next()));
            var report = readCanonical(output.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            for (String name : List.of("recoveryAllowed", "productionApproved", "runtimeUpdated", "deletionAllowed",
                    "writerLivenessVerified", "writeProtectionVerified", "externalCustodyVerified", "globalReferenceCoverageVerified"))
                assertThat(report.path(name).isBoolean() && !report.path(name).asBoolean()).as(name).isTrue();
            assertThat(report.path("keepWritersStopped").asBoolean()).isTrue();
            return report;
        } finally { if (child.isAlive()) { child.destroyForcibly(); child.waitFor(5, TimeUnit.SECONDS); } }
    }

    Map<String, String> facts() throws Exception {
        var result = new TreeMap<String, String>();
        for (Path root : List.of(keyring.getParent(), archive)) {
            try (var files = Files.walk(root)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) result.put(file.toString(), sha256(Files.readAllBytes(file)));
            }
        }
        for (Path file : List.of(catalog, rotation.rotation().request())) result.put(file.toString(), sha256(Files.readAllBytes(file)));
        return result;
    }

    ObjectNode manifest() throws Exception { return (ObjectNode) RecoveryControlHistory.read(keyring); }
    ObjectNode catalogValue() throws Exception { return (ObjectNode) RecoveryControlHistory.read(catalog); }
    void saveManifest(ObjectNode value) throws Exception { Files.writeString(keyring, canonical(value)); }
    void saveCatalog(ObjectNode value) throws Exception { Files.writeString(catalog, canonical(value)); }
    ObjectNode reference(ObjectNode value) { return (ObjectNode) value.path("references").get(0); }
    ObjectNode key(ObjectNode value) { return (ObjectNode) value.path("keys").get(0); }

    void repinInventory() throws Exception {
        var value = catalogValue(); reference(value).put("inventorySha256", sha256(Files.readAllBytes(archive.resolve(RecoveryRekeyInventory.FILE))));
        saveCatalog(value);
    }

    void adversarialSeal(Path record, ObjectNode value, String field) throws Exception {
        Files.copy(record, catalog.getParent().resolve("preserved-" + record.getFileName()));
        value.remove(field); value.put(field, hmac(rotation.rotation().supply().roots().anchor(), value));
        Files.writeString(record, canonical(value));
        var digest = java.security.MessageDigest.getInstance("SHA-256"); long bytes = 0;
        try (var paths = Files.walk(archive)) {
            var files = paths.filter(Files::isRegularFile).filter(p -> !p.getFileName().toString().equals(RecoveryRekeyInventory.FILE))
                .sorted(java.util.Comparator.comparing(p -> archive.relativize(p).toString().replace('\\', '/'))).toList();
            for (Path path : files) {
                byte[] content = Files.readAllBytes(path); bytes += content.length;
                var entry = JsonNodeFactory.instance.objectNode().put("bytes", content.length)
                    .put("path", archive.relativize(path).toString().replace('\\', '/')).put("sha256", sha256(content));
                digest.update(canonical(entry).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        Path path = archive.resolve(RecoveryRekeyInventory.FILE); Files.copy(path, catalog.getParent().resolve("preserved-inventory.json"));
        var manifest = (ObjectNode) RecoveryControlHistory.read(path); manifest.remove("inventoryTag");
        manifest.put("recordsSha256", java.util.HexFormat.of().formatHex(digest.digest())).put("totalBytes", bytes);
        manifest.put("inventoryTag", hmac(rotation.rotation().supply().roots().anchor(), manifest));
        Files.writeString(path, canonical(manifest)); repinInventory();
    }
}
