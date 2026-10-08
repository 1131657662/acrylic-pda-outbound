package com.acrylic.outbound.handover;

import com.acrylic.outbound.submissions.SubmissionJson;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class HandoverFaultViewTest {
    @Test void defaultGateDoesNotReadOrOfferFaultWrite() {
        var origins = mock(HandoverStopStore.class);
        var archives = mock(HandoverFaultArchive.class);
        var view = new HandoverFaultView(origins, archives, false);
        var context = SubmissionJson.object().put("stopState", "REQUESTED").put("sourceKind", "TEMP")
            .put("status", "EVIDENCE_PENDING").put("requestedBy", UUID.randomUUID().toString());
        var actor = new HandoverCommand.Actor(UUID.randomUUID(), UUID.randomUUID(), "1", null, List.of("order:handover"), List.of());
        var result = view.read(context, actor);
        assertThat(result.path("entryAvailable").asBoolean()).isFalse();
        assertThat(result.path("receipt").isNull()).isTrue();
        verifyNoInteractions(origins, archives);
    }

    @Test void resumePermissionAndOwnRequestCannotOfferFaultWrite() {
        var origins = mock(HandoverStopStore.class); var archives = mock(HandoverFaultArchive.class);
        var view = new HandoverFaultView(origins, archives, true); var user = UUID.randomUUID();
        var context = SubmissionJson.object().put("stopState", "REQUESTED").put("sourceKind", "TEMP")
            .put("status", "EVIDENCE_PENDING").put("requestedBy", user.toString());
        for (var permissions : List.of(List.of("order:resume"), List.of("order:handover"))) {
            var actor = new HandoverCommand.Actor(user, UUID.randomUUID(), "1", null, permissions, List.of());
            assertThat(view.read(context, actor).path("entryAvailable").asBoolean()).isFalse();
        }
        verifyNoInteractions(origins, archives);
    }
}
