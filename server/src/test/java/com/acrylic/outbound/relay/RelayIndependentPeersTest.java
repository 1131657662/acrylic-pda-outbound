package com.acrylic.outbound.relay;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

/** Synthetic grouping inputs; signature and historical authorization are verified separately. */
class RelayIndependentPeersTest {
    private final UUID device=UUID.randomUUID(),task=UUID.randomUUID();
    @Test void repeatedTaskExportsAreNotCrossDeviceConflicts() {
        var selected=bundle(device,task,"ACTIVE");var group=RelayIndependentPeers.group(selected);
        RelayIndependentPeers.merge(group,selected,selected);RelayIndependentPeers.merge(group,bundle(device,task,"ACTIVE"),selected);
        assertThat(group.path("selectedTask").asBoolean()).isTrue();assertThat(group.path("crossTaskConflict").asBoolean()).isFalse();
        assertThat(group.path("closureDivergence").asBoolean()).isFalse();
    }
    @Test void differentEndFactsOfTheSameTaskStayVisible() {
        var selected=bundle(device,task,"ACTIVE");var group=RelayIndependentPeers.group(selected);
        RelayIndependentPeers.merge(group,selected,selected);var divergent=bundle(device,task,"ACTIVE");
        ((ObjectNode)divergent.closure()).put("differentFact",true);RelayIndependentPeers.merge(group,divergent,selected);
        assertThat(group.path("closureDivergence").asBoolean()).isTrue();assertThat(group.path("crossTaskConflict").asBoolean()).isFalse();
    }
    @Test void anotherDeviceActiveBarcodeIsACrossDeviceConflict() {
        var selected=bundle(device,task,"ACTIVE");var peer=bundle(UUID.randomUUID(),UUID.randomUUID(),"ACTIVE");var group=RelayIndependentPeers.group(peer);
        RelayIndependentPeers.merge(group,peer,selected);
        assertThat(group.path("crossTaskConflict").asBoolean()).isTrue();assertThat(group.path("crossDeviceConflict").asBoolean()).isTrue();
    }
    @Test void anotherTaskOnSameDeviceStillRequiresReconciliation() {
        var selected=bundle(device,task,"ACTIVE");var peer=bundle(device,UUID.randomUUID(),"ACTIVE");var group=RelayIndependentPeers.group(peer);
        RelayIndependentPeers.merge(group,peer,selected);
        assertThat(group.path("crossTaskConflict").asBoolean()).isTrue();assertThat(group.path("crossDeviceConflict").asBoolean()).isFalse();
    }
    @Test void voidIsNotAnActiveBarcodeConflict() {
        var selected=bundle(device,task,"ACTIVE");var peer=bundle(UUID.randomUUID(),UUID.randomUUID(),"VOID");var group=RelayIndependentPeers.group(peer);
        RelayIndependentPeers.merge(group,peer,selected);assertThat(group.path("crossTaskConflict").asBoolean()).isFalse();
    }
    private RelayArchive.Bundle bundle(UUID sourceDevice,UUID sourceTask,String status) {
        var identity=new RelayArchive.Identity(UUID.randomUUID(),sourceDevice,UUID.randomUUID(),"INDEPENDENT",sourceTask);
        return new RelayArchive.Bundle(UUID.randomUUID(),identity,"public-key","key-hash","archive-hash",null,null,object(),
            object().put("stableFact",true),new RelayIndependentFacts(List.of(),List.of(object().put("barcode","00100000001").put("status",status))));
    }
}
