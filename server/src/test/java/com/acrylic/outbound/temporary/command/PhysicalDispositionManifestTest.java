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
        var manifest = manifest(a, b);
        assertThat(PhysicalDispositionManifest.require(manifest, List.of(b, a), hash(manifest), 1))
            .containsExactly(a, b);
    }

    @Test void rejectsMissingBale() {
        var manifest = manifest(a);
        assertThatThrownBy(() -> PhysicalDispositionManifest.require(manifest, List.of(a, b), hash(manifest), 1))
            .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.code())
                .isEqualTo("DISPOSITION_MANIFEST_INCOMPLETE"));
    }

    @Test void rejectsDuplicateAndWrongDisposition() {
        var manifest = manifest(a, a);
        assertThatThrownBy(() -> PhysicalDispositionManifest.require(manifest, List.of(a, b), hash(manifest), 1))
            .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.code())
                .isEqualTo("DISPOSITION_MANIFEST_SET_CONFLICT"));
    }

    @Test void rejectsHashAndVersionMismatch() {
        var manifest = manifest(a, b);
        assertThatThrownBy(() -> PhysicalDispositionManifest.require(manifest, List.of(a, b), "0".repeat(64), 1))
            .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.code())
                .isEqualTo("DISPOSITION_MANIFEST_HASH_CONFLICT"));
        assertThatThrownBy(() -> PhysicalDispositionManifest.require(manifest, List.of(a, b), hash(manifest), 2))
            .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.code())
                .isEqualTo("DISPOSITION_MANIFEST_VERSION_CONFLICT"));
    }

    private ObjectNode manifest(UUID... ids) {
        var node = SubmissionJson.object().put("version", 1);
        ArrayNode items = node.putArray("items");
        for (UUID id : ids) items.addObject().put("baleId", id.toString()).put("disposition", "PHYSICAL_DISPOSED");
        return node;
    }

    private String hash(ObjectNode manifest) {
        return ImportFiles.sha256(SubmissionJson.canonical(manifest).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
