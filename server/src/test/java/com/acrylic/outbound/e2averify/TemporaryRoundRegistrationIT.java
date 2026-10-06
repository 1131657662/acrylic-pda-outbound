package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.temporary.continuation.TemporaryRoundControl;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="B4_RETENTION_VERIFY",matches="true")
class TemporaryRoundRegistrationIT {
    @Test void immutableRegistrationRollsBackAndSerializesWithoutSelectingOrMovingStock() throws Exception {
        try(var f=new TemporaryRoundRegistrationFixture()) {
            var stock=f.http.database.stock();var source=source(f);long before=f.count("submission");
            var request=f.request;
            assertThatThrownBy(()->f.writer.register(TemporaryRoundControl.read(request),f.actor()))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
            mismatches(f);TemporaryRoundRegistrationSqlChecks.verify(f);rollback(f,before);
            concurrentReplay(f);
            assertThat(f.count("submission")).isEqualTo(before+1);
            assertThat(f.count("temporary_source_continuation_submission")).isEqualTo(1);
            assertThat(f.http.database.stock()).isEqualTo(stock);assertThat(source(f)).isEqualTo(source);
            assertThatThrownBy(()->f.register(request.deepCopy().put("submissionId",UUID.randomUUID().toString())))
                .isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.code()).isEqualTo("TEMP_CONTINUATION_SUBMISSION_ALREADY_BOUND"));
            immutable(f);
            assertThat(f.http.submit(request,"explicit-intake-remains-closed").statusCode()).isEqualTo(400);
            ContinuationIntakeGuardHttpChecks.verify(f.http,f.activation,"registration");
            assertThat(f.http.database.stock()).isEqualTo(stock);
            Files.writeString(f.http.database.profile.attempt.resolve("test-completed.marker"),
                "Nonempty additive migration; actual HTTP ticket/activation and closed intake; synthetic predecessor; "
                    +"application registration rollback, concurrent exact replay, immutable binding. Not intake/accounting proof.\n");
        }
    }
    private void mismatches(TemporaryRoundRegistrationFixture f) {
        java.util.List<Consumer<ObjectNode>> mutations=java.util.List.of(
            b->b.put("authorizationId",f.http.database.seed.get("authorizationId")),
            b->b.put("workRound","3"),b->b.put("sourceRecoveryEpoch","2"),
            b->((ObjectNode)b.get("source")).put("clientOrderId",UUID.randomUUID().toString()),
            b->reference(b).put("predecessorSubmissionId",UUID.randomUUID().toString()),
            b->reference(b).put("predecessorPayloadSha256","a".repeat(64)),
            b->reference(b).put("retainedManifestSha256","a".repeat(64)));
        for(var mutation:mutations) {
            var changed=f.request.deepCopy();mutation.accept(changed);
            assertThatThrownBy(()->f.register(changed)).isInstanceOf(BusinessException.class);
        }
        var actor=f.actor();var other=new com.acrylic.outbound.pda.PdaSessions.Principal(actor.sessionId(),
            UUID.randomUUID(),actor.username(),actor.deviceId(),actor.warehouseId(),"1","1");
        assertThatThrownBy(()->f.tx.execute(s->f.writer.register(TemporaryRoundControl.read(f.request),other)))
            .isInstanceOf(BusinessException.class);
        assertThat(f.count("temporary_source_continuation_submission")).isZero();
    }
    private void rollback(TemporaryRoundRegistrationFixture f,long before) {
        f.jdbc.execute("CREATE TRIGGER round_registration_failure AFTER INSERT ON temporary_source_continuation_submission "
            +"FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='round final binding failure'");
        try { assertThatThrownBy(()->f.register(f.request)).hasRootCauseMessage("round final binding failure"); }
        finally { f.jdbc.execute("DROP TRIGGER round_registration_failure"); }
        assertThat(f.count("submission")).isEqualTo(before);
        assertThat(f.count("temporary_source_continuation_submission")).isZero();
    }
    private void concurrentReplay(TemporaryRoundRegistrationFixture f) throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            Callable<Object> work=()->{ if(!start.await(10,TimeUnit.SECONDS))throw new IllegalStateException("barrier timeout");return f.register(f.request); };
            var first=pool.submit(work);var second=pool.submit(work);start.countDown();
            assertThat(first.get(30,TimeUnit.SECONDS)).isEqualTo(second.get(30,TimeUnit.SECONDS));
        }
    }
    private void immutable(TemporaryRoundRegistrationFixture f) {
        assertThatThrownBy(()->f.jdbc.update("UPDATE temporary_source_continuation_submission SET predecessor_payload_sha256=REPEAT('a',64)"))
            .hasRootCauseMessage("TEMP continuation submission bindings are immutable");
        assertThatThrownBy(()->f.jdbc.update("DELETE FROM temporary_source_continuation_submission"))
            .hasRootCauseMessage("TEMP continuation submission bindings are retained");
        assertThatThrownBy(()->f.jdbc.update("UPDATE temporary_source_continuation_activation SET new_client_order_id=?",Ids.bytes(UUID.randomUUID())))
            .hasRootCauseMessage("TEMP continuation activations are immutable");
        assertThatThrownBy(()->f.jdbc.update("DELETE FROM temporary_source_continuation_activation"))
            .hasRootCauseMessage("TEMP continuation activations are retained");
        assertThatThrownBy(()->f.jdbc.update("UPDATE submission SET operator_id=? WHERE id=?",
            Ids.bytes(f.http.database.id("mismatchUserId")),Ids.bytes(uuid(f.request,"submissionId"))))
            .hasRootCauseMessage("TEMP continuation submission identity is immutable");
    }
    private Object source(TemporaryRoundRegistrationFixture f) {
        return f.jdbc.queryForList("SELECT HEX(id),HEX(current_submission_id),work_round,HEX(creator_device_id),HEX(client_order_id),HEX(canonical_order_id) "
            +"FROM submission_scope WHERE id=?",Ids.bytes(f.http.database.id("mappedScopeId")));
    }
    private static ObjectNode reference(ObjectNode body) { return (ObjectNode)body.get("temporaryContinuation"); }
}
