package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.auth.SessionUser;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.handover.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.context.ConfigurableApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

final class HandoverConcurrencyChecks {
    private final HandoverRollbackFixture f;
    private final HandoverSourceCommands commands;
    private final HandoverRollbackCommands calls;
    private final Path attempt;
    private final StringBuilder notes = new StringBuilder("Commands use service transactions; stop/abort use HTTP; generated identities.\n");

    HandoverConcurrencyChecks(ConfigurableApplicationContext app, Path attempt) {
        f = new HandoverRollbackFixture(app); this.attempt = attempt;
        var store = app.getBean(HandoverStore.class);
        commands = new HandoverSourceCommands(f, store);
        calls = new HandoverRollbackCommands(f, store, app.getBean(HandoverCommandService.class));
    }
    void verify() throws Exception {
        var request = commands.request(); var original = calls.call(request, f.requester);
        f.caseId = UUID.fromString(SubmissionJson.parse(original).path("businessDecision").path("caseId").asText());
        replay(request, original); f.stopped(f.caseId); approvals(); interleaved(request);
        Files.writeString(attempt.resolve("concurrency.txt"), notes.toString());
    }
    private void replay(ObjectNode request, byte[] original) throws Exception {
        var outcomes = together(() -> calls.call(request, f.requester), () -> calls.call(request, f.requester));
        for (var bytes : outcomes) assertThat(bytes).isEqualTo(original);
        assertThat(count("handover_command_receipt")).isEqualTo(1); oneCase();
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM operation_event WHERE entity_type='HANDOVER_COMMAND' "
            + "AND entity_id IN (SELECT id FROM handover_command_receipt WHERE case_id=?)", Integer.class, Ids.bytes(f.caseId))).isEqualTo(1);
        notes.append("M12 both concurrent replays return original bytes; one case, receipt and audit\n");
    }
    private void approvals() throws Exception {
        int receipts = count("handover_command_receipt"), lines = count("handover_checklist");
        var first = commands.command("APPROVE"); var second = commands.command("APPROVE");
        var outcomes = together(() -> code(first, f.approver), () -> code(second, f.approver));
        assertThat(outcomes.stream().filter("OK"::equals).count()).as(outcomes.toString()).isEqualTo(1);
        assertThat(outcomes.stream().filter(code -> Set.of("HANDOVER_CONTEXT_CHANGED", "HANDOVER_STATE_INVALID").contains(code)).count()).isEqualTo(1);
        assertThat(count("handover_command_receipt")).isEqualTo(receipts + 1);
        assertThat(count("handover_checklist")).isEqualTo(lines + 1); approved();
        notes.append("M10 distinct keys at same version: ").append(outcomes).append("; one checklist and receipt\n");
    }
    private void interleaved(ObjectNode original) throws Exception {
        var request = original.deepCopy().put("commandId", UUID.randomUUID().toString());
        var approve = commands.command("APPROVE");
        int receipts = count("handover_command_receipt");
        var outcomes = together(() -> code(approve, f.approver), () -> code(request, f.requester));
        assertThat(outcomes).containsExactlyInAnyOrder("HANDOVER_STATE_INVALID", "HANDOVER_RESULT_UNKNOWN");
        oneCase(); approved(); assertThat(count("handover_command_receipt")).isEqualTo(receipts);
        notes.append("M11 REQUEST versus repeated APPROVE: ").append(outcomes).append("; no duplicate case or write\n");
    }
    private String code(ObjectNode command, SessionUser actor) {
        try { calls.call(command, actor); return "OK"; }
        catch (BusinessException refused) { return refused.code(); }
    }
    private int count(String table) {
        if (!Set.of("handover_command_receipt", "handover_checklist").contains(table)) throw new IllegalArgumentException(table);
        return f.jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE case_id=?", Integer.class, Ids.bytes(f.caseId));
    }
    private void oneCase() {
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM handover_case WHERE scope_id=?", Integer.class, Ids.bytes(f.scope))).isEqualTo(1);
    }
    private void approved() {
        assertThat(f.jdbc.queryForObject("SELECT status FROM handover_case WHERE id=?", String.class, Ids.bytes(f.caseId))).isEqualTo("APPROVED");
    }
    private <T> List<T> together(Callable<T> first, Callable<T> second) throws Exception {
        var start = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<T>> tasks = new ArrayList<>();
            for (var work : List.of(first, second)) tasks.add(pool.submit(() -> { start.await(); return work.call(); }));
            start.countDown(); List<T> results = new ArrayList<>();
            for (var task : tasks) results.add(task.get(120, TimeUnit.SECONDS));
            return results;
        } finally { pool.shutdownNow(); }
    }
}