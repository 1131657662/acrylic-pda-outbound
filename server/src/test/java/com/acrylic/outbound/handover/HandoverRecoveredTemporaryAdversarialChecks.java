package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

/** Rejects incompatible transaction resources and unverifiable projection changes on an actual restored database. */
final class HandoverRecoveredTemporaryAdversarialChecks {
    static void boundaries(HandoverRecoveredTemporaryFixture f,JsonNode request,HandoverRecoveredTemporaryBinding foreign) throws Exception {
        var before=f.snapshots.capture(); HandoverRecoveredTemporaryFixture.authenticate(f.requester);
        try {
            var transaction=new TransactionTemplate(new DataSourceTransactionManager(f.jdbc.getDataSource()));
            assertThatThrownBy(()->transaction.execute(status->f.commands.executeRecoveredTemporary(
                HandoverReceiptCodec.bytes(request),"nested-recovery",f.binding)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("outer transaction boundary");
            assertThatThrownBy(()->f.commands.executeRecoveredTemporary(HandoverReceiptCodec.bytes(request),"foreign-recovery",foreign))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("this database's closed transaction");
        } finally { SecurityContextHolder.clearContext(); }
        assertThat(f.snapshots.capture()).isEqualTo(before);
        Files.writeString(f.directory.resolve("boundary-result.json"),object().put("nestedRejected",true).put("foreignDataSourceRejected",true).toPrettyString());
    }

    static void verificationTail(HandoverRecoveredTemporaryFixture f,JsonNode fault) throws Exception {
        var before=f.snapshots.capture();
        f.jdbc.execute("CREATE TRIGGER received_verification_tail BEFORE INSERT ON operation_event FOR EACH ROW BEGIN "
            +"IF NEW.event_type='HANDOVER_FAULT_FENCE' THEN UPDATE source_evidence_verification SET known_facts_sha256=REPEAT('0',64); END IF; END");
        try { assertThatThrownBy(()->f.execute(f.supervisor,fault)).hasMessageContaining("RECOVERY_NATIVE_VERIFICATION_CHANGED"); }
        finally { f.jdbc.execute("DROP TRIGGER received_verification_tail"); }
        var after=f.snapshots.capture();
        Files.writeString(f.directory.resolve("verification-tail-before.json"),before.toPrettyString());
        Files.writeString(f.directory.resolve("verification-tail-after.json"),after.toPrettyString());
        assertThat(withoutAuditCounter(after)).isEqualTo(withoutAuditCounter(before));
        Files.writeString(f.directory.resolve("verification-tail-result.json"),object().put("allEffectsRolledBack",true)
            .put("onlyOperationEventCounterExcluded",true).toPrettyString());
    }

    static void projection(HandoverRecoveredTemporaryFixture f,JsonNode fault) throws Exception {
        byte[] order=Ids.bytes(f.binding.request().evidence().expected().canonicalOrderId());
        long version=f.jdbc.queryForObject("SELECT row_version FROM outbound_order WHERE id=?",Long.class,order);
        corrupt(f,fault,new Mutation("UPDATE outbound_order SET row_version=? WHERE id=?",new Object[]{version-1,order},new Object[]{version,order}));
        corrupt(f,fault,new Mutation("UPDATE outbound_order SET row_version=? WHERE id=?",new Object[]{version+1,order},new Object[]{version,order}));
        var outbox=f.jdbc.queryForMap("SELECT id,payload FROM order_sync_outbox WHERE order_id=? AND event_type='HANDOVER_FAULT_FENCED'",order);
        corrupt(f,fault,new Mutation("UPDATE order_sync_outbox SET payload=? WHERE id=?",new Object[]{"{}",outbox.get("id")},new Object[]{outbox.get("payload"),outbox.get("id")}));
        corrupt(f,fault,new Mutation("UPDATE order_sync_outbox SET published_seq=? WHERE id=?",new Object[]{1L,outbox.get("id")},new Object[]{null,outbox.get("id")}));
        Files.writeString(f.directory.resolve("projection-result.json"),object().put("versionRollbackRejected",true).put("versionAdvanceRejected",true)
            .put("outboxPayloadRejected",true).put("publishedOutboxRejected",true).toPrettyString());
    }

    private static void corrupt(HandoverRecoveredTemporaryFixture f,JsonNode fault,Mutation mutation) throws Exception {
        var original=f.snapshots.capture(); f.jdbc.update(mutation.sql(),mutation.corrupt()); var corrupted=f.snapshots.capture();
        try {
            assertThatThrownBy(()->f.execute(f.supervisor,fault)).isInstanceOfSatisfying(BusinessException.class,
                error->assertThat(error.code()).isEqualTo("HANDOVER_RECOVERED_PROJECTION_CHANGED"));
            assertThat(f.snapshots.capture()).isEqualTo(corrupted);
        } finally { f.jdbc.update(mutation.sql(),mutation.restore()); }
        assertThat(f.snapshots.capture()).isEqualTo(original);
    }
    private record Mutation(String sql,Object[] corrupt,Object[] restore) { }
    private static JsonNode withoutAuditCounter(JsonNode value) {
        var copy=value.deepCopy(); var schema=(ObjectNode)copy.path("schema");
        schema.put("operation_event",text(schema,"operation_event").replaceAll(" AUTO_INCREMENT=[0-9]+","")); return copy;
    }
}
