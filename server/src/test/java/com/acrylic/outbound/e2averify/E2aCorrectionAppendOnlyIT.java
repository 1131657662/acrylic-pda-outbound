package com.acrylic.outbound.e2averify;

import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** R07-P08-A: real MySQL rejects correction-fact rewrites while allowing lifecycle advances. */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aCorrectionAppendOnlyIT {
    @Test
    void correctionFactsAndReceiptsAreAppendOnlyOnNonemptyMysql() throws Exception {
        var profile = new E2aCrossSourceProfile();
        JdbcTemplate jdbc = profile.connect();
        arrange(profile, jdbc);
        Probe probe = seedProbe(profile, jdbc);
        assertDocumentFactsImmutable(jdbc, probe);
        assertChildFactsImmutable(jdbc, probe);
        assertReceiptAndReconciliationImmutable(jdbc, probe);
        assertLifecycleRules(jdbc, probe);
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "V50->V51 applied; confirmed correction facts and receipts reject UPDATE/DELETE while lifecycle updates pass\n");
    }

    private static void arrange(E2aCrossSourceProfile profile, JdbcTemplate jdbc) throws Exception {
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34");
        E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        profile.migrate(jdbc, "50");
        profile.migrate(jdbc, "51");
        E2aCrossSourceHttpSetup.signing(profile.attempt);
    }

    private static Probe seedProbe(E2aCrossSourceProfile profile, JdbcTemplate jdbc) throws Exception {
        var order = new E2aCorrectionChainIT().confirmedOrder(profile, jdbc);
        String orderId = order.orderId();
        String warehouseId = order.warehouseId();
        String requester = order.operatorId().toString();
        String resultVersion = order.resultVersion();
        String approver = jdbc.queryForObject(
            "SELECT BIN_TO_UUID(id) FROM app_user WHERE id<>UUID_TO_BIN(?) LIMIT 1",
            String.class, requester);
        Map<String, Object> dispatch = jdbc.queryForMap(
            "SELECT BIN_TO_UUID(id) dispatch_id,BIN_TO_UUID(bale_id) bale_id,delivery_code,dispatch_cycle,weight_g "
                + "FROM dispatch_record WHERE id=UUID_TO_BIN(?)", order.dispatchIds().getFirst());
        Probe probe = new Probe(orderId, warehouseId, requester, approver, resultVersion,
            String.valueOf(dispatch.get("dispatch_id")), String.valueOf(dispatch.get("bale_id")),
            String.valueOf(dispatch.get("delivery_code")), ((Number) dispatch.get("dispatch_cycle")).longValue(),
            ((Number) dispatch.get("weight_g")).longValue(), UUID.randomUUID().toString(), UUID.randomUUID().toString(),
            UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(),
            UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString());
        insertDocument(jdbc, probe);
        insertLineAndEffect(jdbc, probe);
        insertLedgerAndReopening(jdbc, probe);
        insertReceiptAndReconciliation(jdbc, probe);
        return probe;
    }

    private static void insertDocument(JdbcTemplate jdbc, Probe p) {
        jdbc.update("INSERT INTO correction_document(id,warehouse_id,order_id,type,expected_result_version,reason,"
            + "document_sha256,status,source_recovery_epoch,row_version,requested_by,requested_at) VALUES(UUID_TO_BIN(?),"
            + "UUID_TO_BIN(?),UUID_TO_BIN(?),'WEIGHT_CORRECTION',?, 'append-only probe',REPEAT('a',64),'SUBMITTED',1,1,"
            + "UUID_TO_BIN(?),UTC_TIMESTAMP(6))", p.document, p.warehouseId, p.orderId,
            Long.parseLong(p.resultVersion), p.requester);
    }

    private static void insertLineAndEffect(JdbcTemplate jdbc, Probe p) {
        jdbc.update("INSERT INTO correction_line(id,document_id,line_no,dispatch_id,bale_id,delivery_code,before_count,"
            + "before_weight_g,target_count,target_weight_g,business_occurred_at) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),"
            + "1,UUID_TO_BIN(?),UUID_TO_BIN(?),?,1,?,0,0,UTC_TIMESTAMP(6))", p.line, p.document, p.dispatchId,
            p.baleId, p.deliveryCode, p.weight);
        jdbc.update("INSERT INTO correction_effect(id,line_id,order_id,result_version,dispatch_id,action,count_delta,"
            + "weight_delta_g,business_occurred_at,effective_at) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),"
            + "?,UUID_TO_BIN(?),'WEIGHT',-1,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", p.effect, p.line, p.orderId,
            Long.parseLong(p.resultVersion), p.dispatchId, -p.weight);
    }

    private static void insertLedgerAndReopening(JdbcTemplate jdbc, Probe p) {
        jdbc.update("INSERT INTO correction_ledger(id,dispatch_id,bale_id,correction_line_id,entry_kind,quantity_delta,"
            + "weight_delta_g,business_key,created_at) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),"
            + "'REVERSAL',-1,?,CONCAT('P08:',?),UTC_TIMESTAMP(6))", p.ledger, p.dispatchId, p.baleId, p.line,
            -p.weight, p.ledger);
        jdbc.update("INSERT INTO cycle_reopening(id,original_dispatch_id,correction_line_id,cycle_before,cycle_after,"
            + "evidence_sha256,state_version,issued_recovery_epoch,created_at) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),"
            + "UUID_TO_BIN(?),?,?,REPEAT('b',64),1,1,UTC_TIMESTAMP(6))", p.reopening, p.dispatchId, p.line,
            p.cycle, p.cycle + 1);
    }

    private static void insertReceiptAndReconciliation(JdbcTemplate jdbc, Probe p) {
        jdbc.update("INSERT INTO correction_command_receipt(id,document_id,order_id,original_key,command_type,command_sha256,"
            + "canonical_command_bytes,response_bytes,response_sha256,actor_id,expected_json,status,event_id,recorded_at)"
            + " VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),'CREATE',UNHEX(REPEAT('c',64)),"
            + "UNHEX('01'),UNHEX('02'),UNHEX(REPEAT('d',64)),UUID_TO_BIN(?),'{}','RECORDED',UUID_TO_BIN(?),UTC_TIMESTAMP(6))",
            p.receipt, p.document, p.orderId, p.originalKey, p.requester, UUID.randomUUID().toString());
        jdbc.update("INSERT INTO inventory_reconciliation(id,warehouse_id,kind,status,expected_result_version,"
            + "expected_ledger_version,expected_stock_state_version,row_version,created_at,updated_at) VALUES(UUID_TO_BIN(?),"
            + "UUID_TO_BIN(?),'STOCK_MISMATCH','OPEN',?,1,1,1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", p.reconciliation,
            p.warehouseId, Long.parseLong(p.resultVersion));
    }

    private static void assertDocumentFactsImmutable(JdbcTemplate jdbc, Probe p) {
        expectFailure(jdbc, "UPDATE correction_document SET requested_by=UUID_TO_BIN(?) WHERE id=UUID_TO_BIN(?)",
            p.approver, p.document);
        expectFailure(jdbc, "DELETE FROM correction_document WHERE id=UUID_TO_BIN(?)", p.document);
    }

    private static void assertChildFactsImmutable(JdbcTemplate jdbc, Probe p) {
        expectFailure(jdbc, "UPDATE correction_line SET target_count=1 WHERE id=UUID_TO_BIN(?)", p.line);
        expectFailure(jdbc, "DELETE FROM correction_line WHERE id=UUID_TO_BIN(?)", p.line);
        expectFailure(jdbc, "UPDATE correction_effect SET weight_delta_g=0 WHERE id=UUID_TO_BIN(?)", p.effect);
        expectFailure(jdbc, "DELETE FROM correction_effect WHERE id=UUID_TO_BIN(?)", p.effect);
        expectFailure(jdbc, "UPDATE correction_ledger SET business_key='tampered' WHERE id=UUID_TO_BIN(?)", p.ledger);
        expectFailure(jdbc, "DELETE FROM correction_ledger WHERE id=UUID_TO_BIN(?)", p.ledger);
        expectFailure(jdbc, "UPDATE cycle_reopening SET cycle_after=99 WHERE id=UUID_TO_BIN(?)", p.reopening);
        expectFailure(jdbc, "DELETE FROM cycle_reopening WHERE id=UUID_TO_BIN(?)", p.reopening);
    }

    private static void assertReceiptAndReconciliationImmutable(JdbcTemplate jdbc, Probe p) {
        expectFailure(jdbc, "UPDATE correction_command_receipt SET response_bytes=UNHEX('03') WHERE id=UUID_TO_BIN(?)",
            p.receipt);
        expectFailure(jdbc, "DELETE FROM correction_command_receipt WHERE id=UUID_TO_BIN(?)", p.receipt);
        expectFailure(jdbc, "UPDATE inventory_reconciliation SET kind='OTHER' WHERE id=UUID_TO_BIN(?)",
            p.reconciliation);
        expectFailure(jdbc, "DELETE FROM inventory_reconciliation WHERE id=UUID_TO_BIN(?)", p.reconciliation);
    }

    private static void assertLifecycleRules(JdbcTemplate jdbc, Probe p) {
        assertThat(jdbc.update("UPDATE correction_document SET status='APPROVED',approved_by=UUID_TO_BIN(?),"
            + "approved_at=UTC_TIMESTAMP(6),row_version=2 WHERE id=UUID_TO_BIN(?)", p.approver, p.document)).isEqualTo(1);
        assertThat(jdbc.update("UPDATE correction_document SET status='EFFECTIVE',effective_result_version=?,"
            + "row_version=3 WHERE id=UUID_TO_BIN(?)", Long.parseLong(p.resultVersion), p.document)).isEqualTo(1);
        assertThat(jdbc.update("UPDATE inventory_reconciliation SET status='EVIDENCE_PENDING',conclusion='reviewed',"
            + "row_version=2,updated_at=UTC_TIMESTAMP(6) WHERE id=UUID_TO_BIN(?)", p.reconciliation)).isEqualTo(1);
        String rejected = insertRejectedDocument(jdbc, p);
        expectFailure(jdbc, "UPDATE correction_document SET status='SUBMITTED' WHERE id=UUID_TO_BIN(?)", rejected);
    }

    private static String insertRejectedDocument(JdbcTemplate jdbc, Probe p) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO correction_document(id,warehouse_id,order_id,type,expected_result_version,reason,"
            + "document_sha256,status,source_recovery_epoch,row_version,requested_by,requested_at) VALUES(UUID_TO_BIN(?),"
            + "UUID_TO_BIN(?),UUID_TO_BIN(?),'WEIGHT_CORRECTION',?, 'terminal guard probe',REPEAT('e',64),'REJECTED',1,1,"
            + "UUID_TO_BIN(?),UTC_TIMESTAMP(6))", id, p.warehouseId, p.orderId, Long.parseLong(p.resultVersion),
            p.requester);
        return id;
    }

    private static void expectFailure(JdbcTemplate jdbc, String sql, Object... args) {
        assertThatThrownBy(() -> jdbc.update(sql, args)).isInstanceOf(DataAccessException.class);
    }

    private record Probe(String orderId, String warehouseId, String requester, String approver, String resultVersion,
        String dispatchId, String baleId, String deliveryCode, long cycle, long weight, String document, String line,
        String effect, String ledger, String reopening, String receipt, String originalKey, String reconciliation) { }
}
