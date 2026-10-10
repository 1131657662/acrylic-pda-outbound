package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RecoveryReleaseInventoryTest {
    @Test void preservesFrozenEvidenceWhenCallerMutatesInputOrReturnedFacts() {
        var facts=SubmissionJson.object().put("scopeId","original");
        var snapshot=new RecoveryReleaseInventory(facts,List.of("MISSING"));
        facts.put("scopeId","changed");
        ((com.fasterxml.jackson.databind.node.ObjectNode)snapshot.facts()).put("scopeId","changed");
        assertThat(snapshot.facts().path("scopeId").asText()).isEqualTo("original");
        assertThat(snapshot.sha256()).isEqualTo(RecoveryControlFormat.sha256(RecoveryTemporaryEvidenceMaterial.encode(snapshot.facts())));
        assertThatThrownBy(()->snapshot.gaps().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void rejectsChangedFactsOrGapSetWhenRecheckingPriorObservation() {
        var facts=SubmissionJson.object().put("scopeId","original");
        var snapshot=new RecoveryReleaseInventory(facts,List.of("MISSING"));
        snapshot.requireSame(new RecoveryReleaseInventory(facts,List.of("MISSING")));
        assertThatThrownBy(()->snapshot.requireSame(new RecoveryReleaseInventory(facts,List.of())))
            .hasMessage("RECOVERY_RELEASE_INVENTORY_CHANGED");
        assertThatThrownBy(()->snapshot.requireSame(new RecoveryReleaseInventory(facts.deepCopy().put("revision","2"),List.of("MISSING"))))
            .hasMessage("RECOVERY_RELEASE_INVENTORY_CHANGED");
    }

    @Test void inventoryAloneNeverGrantsReleaseEvenWhenNoGapIsSupplied() {
        var snapshot=new RecoveryReleaseInventory(SubmissionJson.object(),List.of());
        assertThat(snapshot.releaseAllowed()).isFalse();
        assertThat(snapshot.ordinaryWritesAllowed()).isFalse();
        assertThat(snapshot.localDeletionAllowed()).isFalse();
    }
}
