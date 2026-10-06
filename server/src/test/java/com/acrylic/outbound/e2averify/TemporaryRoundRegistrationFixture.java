package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.pda.PdaSessions;
import com.acrylic.outbound.submissions.*;
import com.acrylic.outbound.temporary.continuation.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Synthetic predecessor bytes, real HTTP ticket/activation, real application registration transaction. */
final class TemporaryRoundRegistrationFixture implements AutoCloseable {
    final ContinuationReceiptHttpFixture http;
    final JdbcTemplate jdbc;
    final TransactionTemplate tx;
    final TemporaryRoundRegistration writer;
    final ObjectNode request;
    final String predecessorHash;
    final JsonNode activation;

    TemporaryRoundRegistrationFixture() throws Exception {
        this(false);
    }
    TemporaryRoundRegistrationFixture(boolean localPredecessor) throws Exception {
        http=new ContinuationReceiptHttpFixture();jdbc=http.app.getBean(JdbcTemplate.class);
        predecessorHash=seedPredecessor(localPredecessor);
        var commands=new ContinuationReceiptHttpCommands(http);
        var reopen=commands.request(null);byte[] issuedBytes=http.accepted(reopen,"round-ticket");var issued=parse(issuedBytes);
        var ticket=uuid(issued.path("businessDecision"),"ticketId");
        var activate=commands.request(ticket);byte[] activated=http.accepted(activate,"round-activation");activation=parse(activated);
        var historical=RetentionDatabaseFixture.historical(jdbc);var stock=http.database.stock();
        var binding=jdbc.queryForList("SELECT HEX(ticket_id),HEX(new_authorization_id),receipt_text FROM temporary_source_continuation_activation");
        http.database.profile.migrate(jdbc,"66");
        assertThat(RetentionDatabaseFixture.historical(jdbc)).isEqualTo(historical);
        assertThat(http.database.stock()).isEqualTo(stock);
        assertThat(jdbc.queryForList("SELECT HEX(ticket_id),HEX(new_authorization_id),receipt_text FROM temporary_source_continuation_activation")).isEqualTo(binding);
        assertThat(http.accepted(reopen,"round-ticket-after-upgrade")).isEqualTo(issuedBytes);
        assertThat(http.accepted(activate,"round-activation-after-upgrade")).isEqualTo(activated);
        request=control(activation.path("businessDecision"));
        tx=new TransactionTemplate(http.app.getBean(org.springframework.transaction.PlatformTransactionManager.class));
        tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        writer=http.app.getBean(TemporaryRoundRegistration.class);
    }
    private String seedPredecessor(boolean local) throws Exception {
        var db=http.database;
        var body=object().put("schemaVersion",2).put("submissionId",db.seed.get("mappedSubmissionId"))
            .put("authorizationId",db.seed.get("authorizationId")).put("freezeDeviceId",db.seed.get("deviceId"))
            .put("workRound","1").put("sourceRecoveryEpoch","1").putNull("assignmentId").putNull("continuationId")
            .put("planSha256","c".repeat(64)).put("fixture","registration identity only; not scan/intake evidence");
        body.putObject("source").put("kind","TEMP").put("creatorDeviceId",db.seed.get("deviceId"))
            .put("clientOrderId",db.seed.get("mappedClientOrderId"));
        byte[] bytes=canonical(body).getBytes(StandardCharsets.UTF_8);
        if(local) { localPredecessor(bytes,sha(body));return sha(body); }
        String hash=http.app.getBean(SubmissionFiles.class).store(bytes);
        jdbc.update("INSERT INTO submission_payload VALUES(?,2,?,?,?,?,?,?,UTC_TIMESTAMP(3))",
            Ids.bytes(db.id("mappedSubmissionId")),hash,"submissions/"+hash+".json",bytes.length,3,1,4);
        return hash;
    }
    private void localPredecessor(byte[] bytes,String hash) throws Exception {
        var db=http.database;UUID object=UUID.randomUUID();
        var files=http.app.getBean(com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeObjectFiles.class);
        var stored=files.store(new java.io.ByteArrayInputStream(bytes),bytes.length,hash);
        jdbc.update("""
            INSERT INTO source_evidence_object(id,warehouse_id,scope_id,submission_id,work_round,kind,original_object_id,
              bytes_sha256,byte_count,file_key,origin_device_id,uploaded_by,received_at)
            VALUES(?,?,?,?,1,'LOCAL_FROZEN_PAYLOAD',?,?,?,?,?,?,UTC_TIMESTAMP(3))
            """,Ids.bytes(object),Ids.bytes(db.id("warehouseId")),Ids.bytes(db.id("mappedScopeId")),Ids.bytes(db.id("mappedSubmissionId")),
            Ids.bytes(UUID.randomUUID()),hash,bytes.length,stored.key(),Ids.bytes(db.id("deviceId")),Ids.bytes(db.id("operatorId")));
        jdbc.update("""
            INSERT INTO source_evidence_item(set_id,warehouse_id,scope_id,submission_id,work_round,item_kind,
              original_record_id,object_id,original_device_seq,record_sha256,record_status)
            VALUES(?,?,?,?,1,'STOP',?,?,1,?,'FROZEN')
            """,Ids.bytes(db.id("mappedSetId")),Ids.bytes(db.id("warehouseId")),Ids.bytes(db.id("mappedScopeId")),
            Ids.bytes(db.id("mappedSubmissionId")),Ids.bytes(db.id("mappedSubmissionId")),Ids.bytes(object),hash);
    }
    private ObjectNode control(JsonNode decision) {
        var body=object().put("schemaVersion",2).put("submissionId",UUID.randomUUID().toString())
            .put("authorizationId",text(decision,"newAuthorizationId")).put("freezeDeviceId",text(decision,"targetDeviceId"))
            .put("workRound",text(decision,"toWorkRound")).put("sourceRecoveryEpoch","1")
            .putNull("assignmentId").putNull("continuationId");
        body.putObject("source").put("kind","TEMP").put("creatorDeviceId",text(decision,"targetDeviceId"))
            .put("clientOrderId",text(decision,"newClientOrderId"));
        body.putObject("temporaryContinuation").put("version",1).put("ticketId",text(decision,"ticketId"))
            .put("predecessorSubmissionId",http.database.seed.get("mappedSubmissionId"))
            .put("predecessorPayloadSha256",predecessorHash).put("retainedManifestSha256",text(decision,"retainedManifestSha256"));
        return body;
    }
    PdaSessions.Principal actor() {
        return new PdaSessions.Principal(UUID.randomUUID(),http.database.id("operatorId"),"synthetic",
            http.database.id("deviceId"),http.database.id("warehouseId"),"1","1");
    }
    TemporaryRoundRegistration.Registered register(ObjectNode body) {
        return tx.execute(status->writer.register(TemporaryRoundControl.read(body),actor()));
    }
    long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class); }
    @Override public void close() { http.close(); }
}
