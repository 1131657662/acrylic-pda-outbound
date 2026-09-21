package com.acrylic.outbound.foundationmigration;

import com.acrylic.outbound.submissions.SubmissionArchiveProofs;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = "spring.flyway.enabled=false")
@EnabledIfEnvironmentVariable(named = "FOUNDATION_NEGATIVE", matches = "true")
class FoundationNegativeIT {
    @Autowired SubmissionArchiveProofs proofs;

    @DynamicPropertySource static void guard(DynamicPropertyRegistry properties) {
        FoundationEnvironment.require();
        assertThat(FoundationEnvironment.database()).isNotEqualTo(FoundationEnvironment.DATABASE);
        var jdbc = FoundationEnvironment.jdbc();
        assertThat(jdbc.queryForObject("SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success=1", Integer.class)).isEqualTo(33);
    }

    @Test void refusesInjectedDefectAndRetainsIndependentFailureEvidence() throws Exception {
        var jdbc = FoundationEnvironment.jdbc();
        new LegacySnapshot(jdbc).assertValuesAndFiles("v33-original.json");
        var preflight = new LegacyEvidencePreflight(jdbc, proofs); preflight.verify();
        String mode = System.getenv("FOUNDATION_CASE").replaceFirst("[0-9]+$", "");
        switch (mode) {
            case "missing" -> missing(preflight);
            case "signature" -> signature(preflight);
            case "dirty" -> dirty();
            case "ddl" -> ddl();
            case "lock" -> new FoundationMetadataLock(jdbc).verify();
            default -> throw new IllegalStateException("No registered negative scenario");
        }
        new LegacySnapshot(jdbc).save("negative-" + mode + "-retained.json");
    }

    private void missing(LegacyEvidencePreflight preflight) throws Exception {
        var jdbc = FoundationEnvironment.jdbc();
        String key = jdbc.queryForObject("SELECT file_key FROM submission_payload ORDER BY submission_id LIMIT 1", String.class);
        Path path = FoundationEnvironment.storage().resolve(key).normalize();
        assertThat(path.startsWith(FoundationEnvironment.storage())).isTrue();
        Files.move(path, path.resolveSibling(path.getFileName() + ".retained-negative-original"));
        assertThatThrownBy(preflight::verify).isInstanceOf(IllegalStateException.class).hasMessageContaining("file preflight");
        assertV33Only();
    }

    private void signature(LegacyEvidencePreflight preflight) throws Exception {
        var jdbc = FoundationEnvironment.jdbc(); var json = new ObjectMapper();
        var row = jdbc.queryForMap("SELECT id,receipt_text FROM submission WHERE status='ABORTED' ORDER BY id LIMIT 1");
        var receipt = (ObjectNode) json.readTree(row.get("receipt_text").toString());
        String token = receipt.path("signedReceipt").asText();
        int start = token.lastIndexOf('.') + 1;
        receipt.put("signedReceipt", token.substring(0, start) + (token.charAt(start) == 'A' ? 'B' : 'A') + token.substring(start + 1));
        jdbc.update("UPDATE submission SET receipt_text=? WHERE id=?", receipt.toString(), row.get("id"));
        assertThatThrownBy(preflight::verify).isInstanceOf(com.acrylic.outbound.common.BusinessException.class);
        assertV33Only();
    }

    private void dirty() throws Exception {
        var jdbc = FoundationEnvironment.jdbc();
        byte[] bale = jdbc.queryForObject("SELECT k.bale_id FROM bale_commitment k JOIN submission s ON s.id=k.submission_id "
                + "JOIN submission_scope c ON c.id=s.scope_id WHERE c.creator_device_id IS NOT NULL ORDER BY k.bale_id LIMIT 1", byte[].class);
        byte[] wrong = jdbc.queryForObject("SELECT source_order_id FROM submission_scope WHERE source_order_id IS NOT NULL ORDER BY id LIMIT 1", byte[].class);
        jdbc.update("UPDATE bale_commitment SET order_id=? WHERE bale_id=?", wrong, bale);
        new LegacySnapshot(jdbc).save("negative-dirty-injected.json");
        assertThatThrownBy(() -> migration().migrate()).hasMessageContaining("V34");
        new LegacySnapshot(jdbc).assertValuesAndFiles("negative-dirty-injected.json");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() "
                + "AND TABLE_NAME='submission_scope' AND COLUMN_NAME='mapping_version'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT order_id FROM bale_commitment WHERE bale_id=?", byte[].class, bale)).isEqualTo(wrong);
    }

    private void ddl() throws Exception {
        var jdbc = FoundationEnvironment.jdbc();
        jdbc.execute("CREATE TABLE source_evidence_set (failure_marker INT PRIMARY KEY)");
        jdbc.update("INSERT INTO source_evidence_set VALUES(34)");
        assertThatThrownBy(() -> migration().migrate()).hasMessageContaining("V34");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() "
                + "AND TABLE_NAME='source_evidence_object'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT failure_marker FROM source_evidence_set", Integer.class)).isEqualTo(34);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='34' AND success=0", Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> migration().migrate()).hasMessageContaining("failed migration");
        new LegacySnapshot(jdbc).assertValuesAndFiles("v33-original.json");
    }

    private Flyway migration() {
        return Flyway.configure().dataSource(FoundationEnvironment.jdbc().getDataSource()).target("34").load();
    }

    private void assertV33Only() {
        var jdbc = FoundationEnvironment.jdbc();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='34'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()", Integer.class)).isEqualTo(93);
    }
}
