package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.command.PhysicalDispositionManifest;
import com.acrylic.outbound.temporary.command.PhysicalDispositionStore;
import com.acrylic.outbound.temporary.command.PhysicalDispositionVerification;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aTemporaryCloseDispositionIT {
    private static final String CLOSE_REASON = "isolated close disposition verification";

    @Test void realMysqlClosesRejectedSourceWithReleaseAndQuarantine() throws Exception {
        var profile = new E2aCrossSourceProfile();
        var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        var seed = seedNonemptyV34Source(profile, jdbc);
        profile.migrate(jdbc, "58");
        prepareRejectedClose(jdbc, seed);
        var before = productionFacts(jdbc);
        UUID disposition = closeSource(jdbc, seed);
        assertClosed(jdbc, seed, disposition, before);
        assertRepeatMigration(jdbc, profile);
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "V34 to V58 close disposition release/quarantine and repeated migration passed\n");
    }

    private Map<String, String> seedNonemptyV34Source(E2aCrossSourceProfile profile, JdbcTemplate jdbc) throws Exception {
        profile.migrate(jdbc, "34");
        var seed = E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        addSecondReservation(jdbc, seed);
        return seed;
    }

    private void addSecondReservation(JdbcTemplate jdbc, Map<String, String> seed) {
        String bale = UUID.randomUUID().toString();
        String barcode = "E2C" + bale.replace("-", "").substring(0, 8);
        jdbc.update("INSERT INTO bale(id,barcode,warehouse_id,dispatch_cycle,created_at) VALUES(UUID_TO_BIN(?),?,UUID_TO_BIN(?),1,UTC_TIMESTAMP(3))",
            bale, barcode, seed.get("warehouseId"));
        jdbc.update("INSERT INTO source_evidence_item(set_id,warehouse_id,scope_id,submission_id,work_round,item_kind,original_record_id,object_id,original_device_seq,record_sha256,bale_id,original_cycle,capture_id,record_status) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),1,'SCAN',UUID_TO_BIN(?),UUID_TO_BIN(?),3,REPEAT('e',64),UUID_TO_BIN(?),1,UUID_TO_BIN(?),'ACTIVE')",
            seed.get("mappedSetId"), seed.get("warehouseId"), seed.get("mappedScopeId"), seed.get("mappedSubmissionId"),
            UUID.randomUUID().toString(), seed.get("mappedObjectId"), bale, UUID.randomUUID().toString());
        jdbc.update("INSERT INTO bale_commitment(bale_id,order_id,submission_id,dispatch_cycle,state,created_at,source_scope_id,source_evidence_set_id,source_warehouse_id) VALUES(UUID_TO_BIN(?),NULL,NULL,1,'SOURCE_RESERVED',UTC_TIMESTAMP(3),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",
            bale, seed.get("mappedScopeId"), seed.get("mappedSetId"), seed.get("warehouseId"));
        jdbc.update("UPDATE source_evidence_set SET item_count=3,capture_high_watermark=3 WHERE id=UUID_TO_BIN(?)", seed.get("mappedSetId"));
        seed.put("secondReservedBaleId", bale);
    }

    private void prepareRejectedClose(JdbcTemplate jdbc, Map<String, String> seed) {
        jdbc.update("UPDATE submission SET status='REJECTED',issues_json=? WHERE id=UUID_TO_BIN(?)",
            "{\"issues\":[{\"code\":\"SUPERVISOR_REJECTED\"}]}", seed.get("mappedSubmissionId"));
        jdbc.update("UPDATE source_evidence_verification SET result='VERIFIED_COMPLETE_AT_STOP' WHERE id=UUID_TO_BIN(?)",
            seed.get("mappedVerificationId"));
        jdbc.update("INSERT INTO order_submission_slot(order_id,scope_id,work_round,current_submission_id,phase) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),1,UUID_TO_BIN(?),'FROZEN')",
            seed.get("orderId"), seed.get("mappedScopeId"), seed.get("mappedSubmissionId"));
    }

    private UUID closeSource(JdbcTemplate jdbc, Map<String, String> seed) {
        var transaction = new TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
        return transaction.execute(status -> closeInTransaction(jdbc, seed));
    }

    private UUID closeInTransaction(JdbcTemplate jdbc, Map<String, String> seed) {
        var verified = verified(jdbc, seed);
        var items = manifestItems(verified.activeBales());
        var manifest = manifest(items);
        return new PhysicalDispositionStore(jdbc).apply(verified, SubmissionJson.sha(manifest), 1, items, Instant.now(), true, CLOSE_REASON);
    }

    private PhysicalDispositionVerification.Verified verified(JdbcTemplate jdbc, Map<String, String> seed) {
        String hash = jdbc.queryForObject("SELECT set_sha256 FROM source_evidence_verification WHERE id=UUID_TO_BIN(?)",
            String.class, seed.get("mappedVerificationId"));
        return new PhysicalDispositionVerification(jdbc).readForClose(UUID.fromString(seed.get("warehouseId")),
            UUID.fromString(seed.get("mappedScopeId")), UUID.fromString(seed.get("mappedSubmissionId")), 1,
            UUID.fromString(seed.get("mappedSetId")), UUID.fromString(seed.get("mappedVerificationId")), hash, 1, 1);
    }

    private List<PhysicalDispositionManifest.Item> manifestItems(List<UUID> bales) {
        assertThat(bales).hasSize(2);
        return List.of(new PhysicalDispositionManifest.Item(bales.getFirst(), "RELEASE"),
            new PhysicalDispositionManifest.Item(bales.getLast(), "QUARANTINE"));
    }

    private com.fasterxml.jackson.databind.node.ObjectNode manifest(List<PhysicalDispositionManifest.Item> items) {
        var manifest = SubmissionJson.object().put("version", 1);
        var values = manifest.putArray("items");
        items.forEach(item -> values.addObject().put("baleId", item.baleId().toString()).put("disposition", item.action()));
        return manifest;
    }

    private Map<String, Integer> productionFacts(JdbcTemplate jdbc) {
        return Map.of("ledger", count(jdbc, "inventory_ledger"), "dispatch", count(jdbc, "dispatch_record"),
            "result", count(jdbc, "order_result_version"));
    }

    private void assertClosed(JdbcTemplate jdbc, Map<String, String> seed, UUID disposition, Map<String, Integer> before) {
        assertThat(jdbc.queryForObject("SELECT close_mode FROM temporary_physical_disposition WHERE id=?", Boolean.class,
            Ids.bytes(disposition))).isTrue();
        assertThat(jdbc.queryForObject("SELECT close_reason FROM temporary_physical_disposition WHERE id=?", String.class,
            Ids.bytes(disposition))).isEqualTo(CLOSE_REASON);
        assertReservations(jdbc, disposition);
        assertThat(jdbc.queryForObject("SELECT current_submission_id IS NULL FROM submission_scope WHERE id=UUID_TO_BIN(?)",
            Boolean.class, seed.get("mappedScopeId"))).isTrue();
        assertThat(jdbc.queryForObject("SELECT CONCAT(phase,':',current_submission_id IS NULL) FROM order_submission_slot WHERE scope_id=UUID_TO_BIN(?)",
            String.class, seed.get("mappedScopeId"))).isEqualTo("CLOSED:1");
        assertThat(productionFacts(jdbc)).isEqualTo(before);
    }

    private void assertReservations(JdbcTemplate jdbc, UUID disposition) {
        var rows = jdbc.queryForList("SELECT state,BIN_TO_UUID(disposition_id) disposition_id FROM bale_commitment ORDER BY bale_id");
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst()).containsEntry("state", "QUARANTINED")
            .containsEntry("disposition_id", disposition.toString());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE disposition_id=?", Integer.class,
            Ids.bytes(disposition))).isEqualTo(1);
    }

    private void assertRepeatMigration(JdbcTemplate jdbc, E2aCrossSourceProfile profile) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='58'", Integer.class)).isEqualTo(1);
        profile.migrate(jdbc, "58");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='58'", Integer.class)).isEqualTo(1);
    }

    private int count(JdbcTemplate jdbc, String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
