package com.acrylic.outbound.ledgerverify;

import com.acrylic.outbound.inventory.LedgerVerifySeed;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Database oracle reads persisted relationships and raw BLOBs independently of CommandStore. */
final class LedgerDatabaseFacts {
    private final JdbcTemplate jdbc;
    LedgerDatabaseFacts(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    Map<String,Object> counts(LedgerVerifySeed seed,String requestId) {
        return jdbc.queryForMap("SELECT d.decision_version decisionVersion,"
            +"(SELECT COUNT(*) FROM api_idempotency_request WHERE principal_id=UUID_TO_BIN(?) AND route_scope='r06.command.v1') requests,"
            +"(SELECT COUNT(*) FROM temporary_command_receipt WHERE scope_id=c.id) receipts,"
            +"(SELECT COUNT(*) FROM temporary_decision_event WHERE scope_id=c.id) events,"
            +"(SELECT COUNT(*) FROM operation_event WHERE request_id=?) audit "
            +"FROM submission s JOIN submission_scope c ON c.id=s.scope_id JOIN temporary_decision_state d ON d.scope_id=c.id WHERE s.id=UUID_TO_BIN(?)",
            seed.user(),requestId,seed.id());
    }

    Stored stored(LedgerVerifySeed seed,ObjectNode command) throws Exception {
        var values=jdbc.queryForMap("SELECT q.request_sha256,q.resource_id,q.id request_id,r.id receipt_id,r.api_request_id,"
            +"r.canonical_command_bytes,r.command_sha256,r.original_response_bytes,r.original_response_sha256,r.signed_receipt "
            +"FROM api_idempotency_request q JOIN temporary_command_receipt r ON r.api_request_id=q.id "
            +"WHERE q.principal_id=UUID_TO_BIN(?) AND q.route_scope='r06.command.v1' AND q.idempotency_key=?",seed.user(),command.path("commandId").asText());
        assertThat((byte[])values.get("resource_id")).isEqualTo((byte[])values.get("receipt_id"));
        assertThat((byte[])values.get("request_id")).isEqualTo((byte[])values.get("api_request_id"));
        byte[] body=(byte[])values.get("canonical_command_bytes"),response=(byte[])values.get("original_response_bytes");
        assertThat(new ObjectMapper().readTree(body)).isEqualTo(command);
        assertThat(values.get("request_sha256")).isEqualTo(values.get("command_sha256"));
        assertThat(values.get("command_sha256")).isEqualTo(hashWithPrefix(body));
        assertThat(values.get("original_response_sha256")).isEqualTo(hash(response));
        return new Stored(body,response,(String)values.get("original_response_sha256"),(String)values.get("signed_receipt"));
    }

    private static String hashWithPrefix(byte[] body) throws Exception {
        var digest=MessageDigest.getInstance("SHA-256");digest.update("ACRYLIC-R06/COMMAND/1\n".getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest.digest(body));
    }
    static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    record Stored(byte[] command,byte[] response,String responseHash,String token) { }
}
