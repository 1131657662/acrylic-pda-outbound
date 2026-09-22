package com.acrylic.outbound.temporary.protocol;

import java.io.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class E2aV35MigrationTest {
    private final String ddl=resource("/db/migration/V35__source_evidence_intake_receipts.sql");
    @Test void originalKeyUsesBinaryIdentityAndAllSourceContext() {
        assertThat(ddl).contains("original_key BINARY(16) NOT NULL")
            .contains("route_scope VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL")
            .contains("UNIQUE KEY uq_evidence_intake_original(principal_id,route_scope,original_key)")
            .contains("CHECK(principal_id=original_operator_id)");
    }
    @Test void evidenceReferencesCarryCompositeContext() {
        assertThat(ddl).contains("FOREIGN KEY(object_id,scope_id,submission_id,work_round)")
            .contains("FOREIGN KEY(evidence_set_id,scope_id,submission_id,work_round)")
            .contains("FOREIGN KEY(evidence_verification_id,evidence_set_id,scope_id)")
            .contains("object_id IS NULL AND evidence_set_id IS NULL AND evidence_verification_id IS NULL")
            .contains("object_id IS NOT NULL AND evidence_set_id IS NOT NULL AND evidence_verification_id IS NOT NULL");
    }
    @Test void openCannotCreateSetAndRowsAreImmutable() {
        assertThat(ddl).contains("operation_kind='OPEN' AND object_id IS NULL AND evidence_set_id IS NULL AND evidence_verification_id IS NULL")
            .contains("BEFORE UPDATE ON source_evidence_intake_receipt")
            .contains("BEFORE DELETE ON source_evidence_intake_receipt");
    }
    private static String resource(String name) {
        try(InputStream input=E2aV35MigrationTest.class.getResourceAsStream(name)) {
            if(input==null)throw new AssertionError("missing "+name);return new String(input.readAllBytes(),StandardCharsets.UTF_8);
        } catch(IOException error) { throw new UncheckedIOException(error); }
    }
}
