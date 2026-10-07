package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Prepared evidence remains inaccessible when a recipient credential or source proof can no longer be verified. */
final class HandoverTemporaryTargetReadChecks {
    private final HandoverTemporaryTargetFixture f;
    private final String route;
    HandoverTemporaryTargetReadChecks(HandoverTemporaryTargetFixture f) {
        this.f = f; route = "/handovers/" + f.source.kase;
    }

    void verify() throws Exception {
        String id = f.authorization.path("authorizationId").asText();
        f.source.jdbc.update("UPDATE temporary_authorization SET issued_at=issued_at-INTERVAL 7 DAY,expires_at=expires_at-INTERVAL 7 DAY WHERE id=UUID_TO_BIN(?)", id);
        try { refused("TEMPORARY_AUTH_EXPIRED"); }
        finally { f.source.jdbc.update("UPDATE temporary_authorization SET issued_at=issued_at+INTERVAL 7 DAY,expires_at=expires_at+INTERVAL 7 DAY WHERE id=UUID_TO_BIN(?)", id); }
        var ack = f.source.jdbc.queryForObject("SELECT signed_ack FROM temporary_preparation_ack WHERE authorization_id=UUID_TO_BIN(?)", String.class, id);
        f.source.jdbc.update("UPDATE temporary_preparation_ack SET signed_ack='invalid' WHERE authorization_id=UUID_TO_BIN(?)", id);
        try { refused("HANDOVER_TARGET_CREDENTIAL_INVALID"); }
        finally { f.source.jdbc.update("UPDATE temporary_preparation_ack SET signed_ack=? WHERE authorization_id=UUID_TO_BIN(?)", ack, id); }
        missingBody(); changedMapping();
    }

    private void missingBody() throws Exception {
        var hash = f.source.jdbc.queryForObject("SELECT payload_sha256 FROM submission_payload WHERE submission_id=?", String.class,
            Ids.bytes(f.source.head.control().id()));
        var path = f.source.directory.getParent().resolve("storage/submissions/" + hash + ".json");
        var saved = path.resolveSibling(path.getFileName() + ".d06-target-retained"); Files.move(path, saved);
        try { refused("HANDOVER_STOP_FILE_UNAVAILABLE"); }
        finally { Files.move(saved, path); }
    }

    private void changedMapping() throws Exception {
        var scope = f.source.jdbc.queryForObject("SELECT BIN_TO_UUID(scope_id) FROM handover_case WHERE id=?", String.class, Ids.bytes(f.source.kase));
        var version = f.source.jdbc.queryForObject("SELECT mapping_version FROM submission_scope WHERE id=UUID_TO_BIN(?)", Long.class, scope);
        f.source.jdbc.update("UPDATE submission_scope SET mapping_version=mapping_version+1 WHERE id=UUID_TO_BIN(?)", scope);
        try { refused("HANDOVER_RECEIPT_INTEGRITY_FAILURE"); }
        finally { f.source.jdbc.update("UPDATE submission_scope SET mapping_version=? WHERE id=UUID_TO_BIN(?)", version, scope); }
    }

    private void refused(String code) throws Exception {
        var before = f.source.jdbc.queryForObject("SELECT row_version FROM handover_case WHERE id=?", Long.class, Ids.bytes(f.source.kase));
        f.get(route + "/manifest", f.token, 409, code); f.get(route + "/recovery-package", f.token, 409, code);
        assertThat(f.source.jdbc.queryForObject("SELECT row_version FROM handover_case WHERE id=?", Long.class, Ids.bytes(f.source.kase))).isEqualTo(before);
    }
}
