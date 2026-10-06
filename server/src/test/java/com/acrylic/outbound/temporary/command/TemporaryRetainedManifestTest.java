package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TemporaryRetainedManifestTest {
    @Test void acceptsTheCompleteRetainedSetInBaleOrderIndependentForm() {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        var manifest = manifest(second, first);

        var result = TemporaryRetainedManifest.require(manifest,
            List.of(expected(first, 8, "b"), expected(second, 7, "a")), SubmissionJson.sha(manifest), 1);

        assertThat(result).extracting(TemporaryRetainedManifest.Item::baleId).containsExactlyInAnyOrder(first, second);
    }

    @Test void rejectsAChangedCycleOrRecordDigest() {
        UUID bale = UUID.randomUUID(); var manifest = manifest(bale);

        assertThatThrownBy(() -> TemporaryRetainedManifest.require(manifest,
            List.of(expected(bale, 8, "a")), SubmissionJson.sha(manifest), 1))
            .extracting("code").isEqualTo("RETAINED_MANIFEST_SET_CONFLICT");
    }

    @Test void rejectsDuplicateRetainedBales() {
        UUID bale = UUID.randomUUID(); var manifest = manifest(bale, bale);

        assertThatThrownBy(() -> TemporaryRetainedManifest.parse(manifest, SubmissionJson.sha(manifest), 1))
            .extracting("code").isEqualTo("RETAINED_MANIFEST_SET_CONFLICT");
    }

    private static TemporaryRetainedManifest.Expected expected(UUID bale, long cycle, String marker) {
        return new TemporaryRetainedManifest.Expected(bale, cycle, marker.repeat(64));
    }

    private static ObjectNode manifest(UUID... bales) {
        var manifest = SubmissionJson.object().put("version", 1); var items = manifest.putArray("items");
        for (int index = 0; index < bales.length; index++) items.addObject().put("baleId", bales[index].toString())
            .put("expectedDispatchCycle", Long.toString(7 + index)).put("recordSha256", (index == 0 ? "a" : "b").repeat(64));
        return manifest;
    }
}
