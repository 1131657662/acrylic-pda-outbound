package com.acrylic.outbound.recovery;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

class RecoveryKeyringIT {
    @TempDir(cleanup = CleanupMode.NEVER) Path parent;

    @Test void retrievedArchiveVerifiesWithoutLiveControlRecordsOrBusinessRoots() throws Exception {
        var f = RecoveryKeyringFixture.create(parent); var supply = f.rotation().rotation().supply();
        for (Path root : java.util.List.of(supply.roots().authorityRoot(), supply.base().resolve("business")))
            Files.move(root, root.resolveSibling("preserved-unavailable-" + root.getFileName()));
        Path root = supply.roots().root();
        for (String name : java.util.List.of("lease-root.json", "leases", "processes"))
            Files.move(root.resolve(name), supply.base().resolve("preserved-unavailable-" + name));
        var report = f.run("offline", 0);
        assertThat(report.path("status").asText()).isEqualTo("VERIFIED");
        assertThat(report.path("catalogCoverageVerified").asBoolean()).isTrue();
        assertThat(report.path("references").get(0).path("originalSignaturesVerified").asBoolean()).isTrue();
        assertThat(report.path("keys")).hasSize(2);
        for (var key : report.path("keys")) assertThat(key.path("referenceCount").asInt()).isEqualTo(1);
    }

    @Test void changedKeyringCannotChooseItsOwnTrustedPin() throws Exception {
        var f = RecoveryKeyringFixture.create(parent); String pin = sha256(Files.readAllBytes(f.keyring()));
        var value = f.manifest(); f.key(value).put("minimumRetainUntil", "2030-03-01"); f.saveManifest(value);
        assertThat(f.run("key-pin", 3, pin, sha256(Files.readAllBytes(f.catalog()))).path("status").asText()).isEqualTo("UNKNOWN");
    }

    @Test void truncatedReferenceCatalogCannotReuseTheCompleteCatalogPin() throws Exception {
        var f = RecoveryKeyringFixture.create(parent); String pin = sha256(Files.readAllBytes(f.catalog()));
        var value = f.catalogValue(); value.withArray("references").removeAll(); f.saveCatalog(value);
        f.run("catalog-pin", 3, sha256(Files.readAllBytes(f.keyring())), pin);
    }

    @Test void wrongRetrievedKeyRefusesWithoutChangingAnyInput() throws Exception {
        var f = RecoveryKeyringFixture.create(parent); String file = f.key(f.manifest()).path("file").asText();
        Path key = f.keyring().getParent().resolve(file); Files.copy(key, f.catalog().getParent().resolve("preserved-key.bin"));
        Files.write(key, new byte[32]); f.run("wrong-key", 3);
    }

    @Test void missingReferencedGenerationCannotBeOmittedFromTheKeyring() throws Exception {
        var f = RecoveryKeyringFixture.create(parent); var value = f.manifest();
        String file = value.withArray("keys").remove(0).path("file").asText();
        Files.move(f.keyring().getParent().resolve(file), f.catalog().getParent().resolve("preserved-key.bin"));
        f.saveManifest(value); f.run("missing-generation", 3);
    }

    @Test void fewerThanFiveCalendarYearsIsRejectedEvenWithFreshPins() throws Exception {
        var f = RecoveryKeyringFixture.create(parent); var value = f.manifest();
        f.key(value).put("minimumRetainUntil", "2029-02-28"); f.saveManifest(value); f.run("retention", 3);
    }

    @Test void currentManifestPurposeCannotSelectRuntimeFallback() throws Exception {
        var f = RecoveryKeyringFixture.create(parent); var value = f.manifest();
        value.put("purpose", "CURRENT_RUNTIME"); f.saveManifest(value); f.run("purpose", 3);
    }

    @Test void archiveWithMissingOriginalCannotShrinkItsAuthenticatedSet() throws Exception {
        var f = RecoveryKeyringFixture.create(parent);
        try (var paths = Files.list(f.archive().resolve("processes"))) {
            Files.move(paths.findFirst().orElseThrow(), f.catalog().getParent().resolve("preserved-original.json"));
        }
        f.run("missing-original", 3);
    }

    @Test void missingInventoryCannotDowngradeToIndividualSignatures() throws Exception {
        var f = RecoveryKeyringFixture.create(parent);
        Files.move(f.archive().resolve(RecoveryRekeyInventory.FILE), f.catalog().getParent().resolve("preserved-inventory.json"));
        f.run("missing-inventory", 3);
    }

    @Test void changedRequestCannotBorrowAnotherTrustedInventory() throws Exception {
        var f = RecoveryKeyringFixture.create(parent); var request = f.rotation().rotation().request();
        String original = Files.readString(request); Files.writeString(f.catalog().getParent().resolve("preserved-request.json"), original);
        f.rotation().rotation().change("identitySha256", "0".repeat(64)); f.run("request-pin", 3);
    }

    @Test void duplicatedReferenceCannotInflateCoverage() throws Exception {
        var f = RecoveryKeyringFixture.create(parent); var value = f.catalogValue();
        value.withArray("references").add(f.reference(value).deepCopy()); f.saveCatalog(value); f.run("duplicate", 3);
    }

    @Test void hardLinkedKeyIsNotAnIndependentRetainedFile() throws Exception {
        var f = RecoveryKeyringFixture.create(parent); String file = f.key(f.manifest()).path("file").asText();
        Files.createLink(f.catalog().getParent().resolve("key-hardlink.bin"), f.keyring().getParent().resolve(file));
        f.run("hardlink", 3);
    }

    @Test void booleanFormatVersionCannotMasqueradeAsIntegerOne() throws Exception {
        var f = RecoveryKeyringFixture.create(parent); var value = f.manifest(); value.put("formatVersion", true);
        f.saveManifest(value); f.run("boolean", 3);
    }

    @Test void keyringInsideWritableBusinessTreeIsRejected() throws Exception {
        var f = RecoveryKeyringFixture.create(parent); var business = f.rotation().rotation().supply().base().resolve("business");
        Path destination = business.resolve("misplaced-keyring"); Files.move(f.keyring().getParent(), destination);
        var misplaced = new RecoveryKeyringFixture(f.rotation(), destination.resolve("manifest.json"), f.catalog(), f.archive());
        misplaced.run("inside-business", 3);
    }

    @Test void everyPinnedReferenceIsConsumedAcrossDifferentInstallations() throws Exception {
        var first = RecoveryKeyringFixture.create(parent); var second = RecoveryKeyringFixture.create(parent);
        var manifest = first.manifest(); var secondManifest = second.manifest();
        for (var entry : secondManifest.path("keys")) {
            manifest.withArray("keys").add(entry.deepCopy()); String file = entry.path("file").asText();
            Files.copy(second.keyring().getParent().resolve(file), first.keyring().getParent().resolve(file));
        }
        first.saveManifest(manifest); var catalog = first.catalogValue();
        catalog.withArray("references").add(second.reference(second.catalogValue()).deepCopy()); first.saveCatalog(catalog);
        var report = first.run("two-installations", 0);
        assertThat(report.path("references")).hasSize(2); assertThat(report.path("keys")).hasSize(4);
        Files.move(second.archive().resolve("leases/index.json"), second.catalog().getParent().resolve("preserved-index.json"));
        first.run("second-reference-missing", 3);
    }

    @Test void expiredMinimumDateNeverAuthorizesDeletingAReferencedKey() throws Exception {
        var f = RecoveryKeyringFixture.create(parent); var value = f.manifest();
        for (var entry : value.path("keys")) {
            ((com.fasterxml.jackson.databind.node.ObjectNode) entry).put("retainedFrom", "2000-01-01")
                .put("minimumRetainUntil", "2005-01-01");
        }
        f.saveManifest(value); var report = f.run("still-referenced", 0);
        for (var entry : report.path("keys")) assertThat(entry.path("deletionAllowed").asBoolean()).isFalse();
    }

    @Test void signedBooleanIndexCannotMasqueradeAsTheOriginalFormat() throws Exception {
        var f = RecoveryKeyringFixture.create(parent); Path index = f.archive().resolve("leases/index.json");
        var value = (com.fasterxml.jackson.databind.node.ObjectNode) RecoveryControlHistory.read(index);
        value.put("formatVersion", true); f.adversarialSeal(index, value, "indexTag");
        f.run("signed-boolean-index", 3);
    }

    @Test void validSignaturesCannotRebindAnOriginalProcessIdentity() throws Exception {
        var f = RecoveryKeyringFixture.create(parent); Path process;
        try (var paths = Files.list(f.archive().resolve("processes"))) { process = paths.findFirst().orElseThrow(); }
        var value = (com.fasterxml.jackson.databind.node.ObjectNode) RecoveryControlHistory.read(process);
        value.put("identitySha256", "0".repeat(64)); f.adversarialSeal(process, value, "anchorTag");
        f.run("signed-wrong-identity", 3);
    }

    @Test void separateProcessesCanReadTheSameHistoricalMaterialWithoutWriting() throws Exception {
        var f = RecoveryKeyringFixture.create(parent); var before = f.facts();
        var one = java.util.concurrent.CompletableFuture.supplyAsync(() -> read(f, "reader-one"));
        var two = java.util.concurrent.CompletableFuture.supplyAsync(() -> read(f, "reader-two"));
        assertThat(one.get()).isEqualTo("VERIFIED"); assertThat(two.get()).isEqualTo("VERIFIED");
        assertThat(f.facts()).isEqualTo(before);
    }

    private String read(RecoveryKeyringFixture fixture, String name) {
        try { return fixture.run(name, 0).path("status").asText(); }
        catch (Exception failure) { throw new java.util.concurrent.CompletionException(failure); }
    }
}
