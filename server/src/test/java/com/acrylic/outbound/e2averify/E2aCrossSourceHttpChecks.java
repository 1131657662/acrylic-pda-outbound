package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.Ids;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

final class E2aCrossSourceHttpChecks {
    private E2aCrossSourceHttpChecks() { }

    static void verify(E2aCrossSourceHttp http, E2aCrossSourceHttpSetup setup) throws Exception {
        var wire = http.wire;
        http.unauthorized();
        var raw = new E2aCrossSourceRawHttp(http, setup);
        raw.beforeOpen();
        byte[] open = http.post("open", wire.openKey, wire.open());
        assertThat(http.post("open", wire.openKey, wire.open())).isEqualTo(open);
        http.original(wire.openKey, open);
        raw.upload();
        byte[] page = http.post("page", wire.pageKey, wire.pageRequest());
        assertThat(http.post("page", wire.pageKey, wire.pageRequest())).isEqualTo(page);
        http.original(wire.pageKey, page);
        var stopBody = wire.stop(setup.audit, setup.deviceKey);
        byte[] stop = http.post("stop", wire.stopKey, stopBody);
        assertThat(http.post("stop", wire.stopKey, stopBody)).isEqualTo(stop);
        http.original(wire.stopKey, stop);
        verifyFacts(http);
    }

    private static void verifyFacts(E2aCrossSourceHttp http) throws Exception {
        var wire = http.wire; var f = wire.fixture;
        http.reject("verify", UUID.randomUUID(), wire.verify(1), "E2A_AUDIT_PREFIX_UNVERIFIABLE");
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM source_evidence_set WHERE submission_id=?", Integer.class,
            Ids.bytes(f.submission))).isZero();
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE bale_id IN (?,?)", Integer.class,
            Ids.bytes(f.ownBale), Ids.bytes(f.priorBale))).isZero();
    }
}
