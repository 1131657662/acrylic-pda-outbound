package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PhysicalDispositionManifestTest {
    private final UUID a = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID b = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test void acceptsCompleteStableManifest() {
        var manifest = manifest("PHYSICAL_DISPOSED", a, b);
        var items = PhysicalDispositionManifest.require(manifest, List.of(b, a), hash(manifest), 1);
        assertThat(items).extracting(PhysicalDispositionManifest.Item::baleId).containsExactly(a, b);
        assertThat(items).extracting(PhysicalDispositionManifest.Item::action).containsExactly("QUARANTINE", "QUARANTINE");
    }

    @Test void acceptsReleaseManifest() {
        var manifest = manifest("RELEASE", a, b);
        var items = PhysicalDispositionManifest.require(manifest, List.of(b, a), hash(manifest), 1);
        assertThat(items).extracting(PhysicalDispositionManifest.Item::action).containsExactly("RELEASE", "RELEASE");
    }

    @Test void rejectsMissingBale() {
        var manifest = manifest("RELEASE", a);
        assertThatThrownBy(() -> PhysicalDispositionManifest.require(manifest, List.of(a, b), hash(manifest), 1))
            .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.code())
                .isEqualTo("DISPOSITION_MANIFEST_INCOMPLETE"));
    }

    @Test void rejectsDuplicateAndUnsupportedDisposition() {
        var duplicate = manifest("RELEASE", a, a);
        assertThatThrownBy(() -> PhysicalDispositionManifest.require(duplicate, List.of(a, b), hash(duplicate), 1))
            .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.code())
                .isEqualTo("DISPOSITION_MANIFEST_SET_CONFLICT"));
        var unsupported = manifest("RETAIN", a);
        assertThatThrownBy(() -> PhysicalDispositionManifest.require(unsupported, List.of(a), hash(unsupported), 1))
            .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.code())
                .isEqualTo("DISPOSITION_MANIFEST_ITEM_INVALID"));
    }

    @Test void rejectsHashAndVersionMismatch() {
        var manifest = manifest("RELEASE", a, b);
        assertThatThrownBy(() -> PhysicalDispositionManifest.require(manifest, List.of(a, b), "0".repeat(64), 1))
            .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.code())
                .isEqualTo("DISPOSITION_MANIFEST_HASH_CONFLICT"));
        assertThatThrownBy(() -> PhysicalDispositionManifest.require(manifest, List.of(a, b), hash(manifest), 2))
            .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.code())
                .isEqualTo("DISPOSITION_MANIFEST_VERSION_CONFLICT"));
    }

    private ObjectNode manifest(String disposition, UUID... ids) {
        var node = SubmissionJson.object().put("version", 1);
        ArrayNode items = node.putArray("items");
        for (UUID id : ids) items.addObject().put("baleId", id.toString()).put("disposition", disposition);
        return node;
    }

    private String hash(ObjectNode manifest) {
        return ImportFiles.sha256(SubmissionJson.canonical(manifest).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
