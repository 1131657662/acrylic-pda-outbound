package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.submissions.*;
import com.acrylic.outbound.temporary.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

/** Faults stay inside transactions; no failed business mutation is kept in the isolated database. */
final class TemporaryAccountingFailureChecks {
    private final WebApplicationContext context;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final TemporaryAccountingChecks checks;
    private TemporarySubmissionFixture fixture;
    private SubmissionStore.Head head;
    private SubmissionSnapshot.Validated value;
    private Map<String, Object> baseline;

    TemporaryAccountingFailureChecks(WebApplicationContext context) {
        this.context = context; jdbc = context.getBean(JdbcTemplate.class);
        transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        checks = new TemporaryAccountingChecks(context);
    }

    void verify() throws Exception {
        fixture = checks.fixture(true);
        checks.http(fixture, "/pda/temporary-orders/submissions", fixture.body);
        var store = context.getBean(SubmissionStore.class);
        head = store.required(UUID.fromString(fixture.id()));
        var plan = context.getBean(TemporarySubmissionPlan.class).validate(head, fixture.body);
        value = context.getBean(TemporarySubmissionSnapshot.class).validate(plan, fixture.body);
        baseline = state();
        assertThatThrownBy(() -> commit(accounting(), head.actor()))
            .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        policyAndSource(); stockAndSlot(); atomicity(); pending();
    }

    private void policyAndSource() {
        reject("INVALID_SUBMISSION", () -> {
            var changed = fixture.body.deepCopy().put("autoDeductInventory", false);
            accounting().commit(head, changed, value, head.actor());
        });
        reject("INVALID_SUBMISSION", () -> {
            var changed = fixture.body.deepCopy().put("policyVersion", "999");
            accounting().commit(head, changed, value, head.actor());
        });
        reject("TEMPORARY_ACCOUNTING_CONTEXT_CHANGED", () -> {
            var changed = new SubmissionStore.Head(head.control(), head.actor(), UUID.randomUUID(), head.status(), null, null, head.target());
            accounting().commit(changed, fixture.body, value, head.actor());
        });
        reject("TEMPORARY_ACCOUNTING_CONTEXT_CHANGED", () -> {
            jdbc.update("UPDATE submission_scope SET mapping_version=mapping_version+1 WHERE current_submission_id=?", id());
            commit(accounting(), head.actor());
        });
        reject("TEMPORARY_ACCOUNTING_CONTEXT_CHANGED", () -> {
            var receipt = context.getBean(SubmissionReceipts.class).abort(head, SubmissionJson.sha(fixture.body), "隔离取消负例");
            context.getBean(SubmissionStore.class).state(head.control().id(), "ABORTED", null, SubmissionJson.canonical(receipt));
            commit(accounting(), head.actor());
        });
        reject("INVALID_SUBMISSION", () -> {
            var empty = new SubmissionSnapshot.Validated(List.of(), List.of(), 0, true);
            accounting().commit(head, fixture.body, empty, head.actor());
        });
    }

    private void stockAndSlot() {
        reject("INVALID_SUBMISSION", () -> {
            jdbc.update("UPDATE order_submission_slot SET phase='COMPLETED' WHERE current_submission_id=?", id());
            commit(accounting(), head.actor());
        });
        reject("SUBMISSION_SUPERSEDED", () -> {
            jdbc.update("UPDATE submission_scope SET current_submission_id=NULL WHERE current_submission_id=?", id());
            commit(accounting(), head.actor());
        });
        reject("BALE_COMMITMENT_CONFLICT", () -> {
            jdbc.update("UPDATE bale_commitment SET order_id=UUID_TO_BIN(?) WHERE submission_id=?", fixture.f.order, id());
            commit(accounting(), head.actor());
        });
        reject("BALE_COMMITMENT_CONFLICT", () -> {
            jdbc.update("UPDATE bale_commitment SET dispatch_cycle=dispatch_cycle+1 WHERE bale_id=?", secondBale());
            commit(accounting(), head.actor());
        });
        reject("INVENTORY_ALREADY_DEDUCTED", () -> {
            jdbc.update("UPDATE inventory_position SET book_status='DEDUCTED' WHERE bale_id=?", secondBale());
            commit(accounting(), head.actor());
        });
    }

    private void atomicity() {
        var unavailable = new OfflineSigner("", "", context.getBean(JsonText.class));
        var failingSigner = new SubmissionAccounting(jdbc, new SubmissionReceipts(unavailable, jdbc),
            context.getBean(SubmissionStore.class), context.getBean(SubmissionEvents.class));
        reject("SIGNING_KEY_UNAVAILABLE", () -> commit(failingSigner, head.actor()));
        // A nonexistent audit actor violates the real database FK after result/slot/dispatch writes.
        assertThatThrownBy(() -> transaction.execute(status -> {
            context.getBean(SubmissionStore.class).lockExisting(head);
            commit(accounting(), UUID.randomUUID()); return null;
        })).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
            .hasMessageContaining("operation_event");
        pending();
    }

    private void reject(String code, Runnable action) {
        assertThatThrownBy(() -> transaction.execute(status -> {
            context.getBean(SubmissionStore.class).lockExisting(head);
            action.run(); return null;
        })).isInstanceOf(BusinessException.class).extracting(error -> ((BusinessException) error).code()).isEqualTo(code);
        pending();
    }

    private void pending() {
        assertThat(state()).isEqualTo(baseline);
        var actual = context.getBean(SubmissionStore.class).required(UUID.fromString(fixture.id()));
        assertThat(actual.status()).isEqualTo("REVIEW_REQUIRED"); assertThat(actual.receipt()).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_record WHERE submission_id=?", Integer.class, id())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_result_version WHERE submission_id=?", Integer.class, id())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_ledger l JOIN bale_commitment k ON k.bale_id=l.bale_id WHERE k.submission_id=?",
            Integer.class, id())).isZero();
        assertThat(jdbc.queryForList("SELECT state FROM bale_commitment WHERE submission_id=?", String.class, id()))
            .containsExactly("REVIEW_RESERVED", "REVIEW_RESERVED");
        assertThat(jdbc.queryForList("SELECT p.book_status FROM inventory_position p JOIN bale_commitment k ON k.bale_id=p.bale_id WHERE k.submission_id=?",
            String.class, id())).containsExactly("NOT_DEDUCTED", "NOT_DEDUCTED");
        assertThat(jdbc.queryForObject("SELECT phase FROM order_submission_slot WHERE current_submission_id=?", String.class, id())).isEqualTo("FROZEN");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_event WHERE entity_id=? AND event_type='SUBMISSION_COMPLETED'",
            Integer.class, Ids.bytes(head.order()))).isZero();
    }

    private Map<String, Object> state() {
        return Map.of(
            "submission", jdbc.queryForMap("SELECT status,receipt_text,issues_json,updated_at FROM submission WHERE id=?", id()),
            "order", jdbc.queryForMap("SELECT status,row_version,current_result_version,updated_at FROM outbound_order WHERE id=?", Ids.bytes(head.order())),
            "slot", jdbc.queryForMap("SELECT phase,slot_version FROM order_submission_slot WHERE current_submission_id=?", id()),
            "stock", jdbc.queryForList("SELECT BIN_TO_UUID(k.bale_id) bale,k.dispatch_cycle,k.state,p.book_status,p.row_version "
                + "FROM bale_commitment k JOIN inventory_position p ON p.bale_id=k.bale_id WHERE k.submission_id=? ORDER BY k.bale_id", id()),
            "barcode", jdbc.queryForList("SELECT BIN_TO_UUID(p.bale_id) bale,p.row_version FROM barcode_sync_pending p "
                + "JOIN bale_commitment k ON k.bale_id=p.bale_id WHERE k.submission_id=? ORDER BY p.bale_id", id()),
            "outbox", jdbc.queryForList("SELECT BIN_TO_UUID(id) id,entity_version,event_type,payload FROM order_sync_outbox WHERE order_id=? ORDER BY entity_version", Ids.bytes(head.order())));
    }

    private void commit(SubmissionAccounting accounting, UUID actor) { accounting.commit(head, fixture.body, value, actor); }
    private SubmissionAccounting accounting() { return context.getBean(SubmissionAccounting.class); }
    private byte[] id() { return Ids.bytes(head.control().id()); }
    private byte[] secondBale() { return Ids.bytes(value.scans().getLast().bale().id()); }
}
