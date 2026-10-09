package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.node.ObjectNode;

/** Same pinned development originals plus their actual native E2A encoding, not original identity proof. */
final class RecoveryNativeTemporaryFixture {
    final RecoveryOriginalEvidenceFixture evidence = new RecoveryOriginalEvidenceFixture();
    final RecoveryInitialFixture initial = evidence.initial;

    RecoveryNativeTemporaryFixture() throws Exception {
        initial.state.payload.removeAll(); initial.state.payload.setAll(evidence.frozen);
        ((ObjectNode)initial.state.authority.source.manifest.path("source"))
            .put("captureHighWatermark", "1").put("auditThroughSeq", "5").put("orderScanHighWatermark", "1");
        initial.publish();
    }

    RecoveryInitialSource.Proof read() { return initial.reader().readTemporary(initial.request(), evidence.request()); }
}
