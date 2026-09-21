package com.acrylic.outbound.ledgerverify;

import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LedgerCorruptionIT extends LedgerRuntimeFixture {
    @ParameterizedTest @ValueSource(strings={"request-resource","receipt-request","command-blob","response-blob","response-hash",
        "signature","unknown-key","event-context","audit-missing"})
    void invalidPersistedRelationshipOrBytesNeverProduceReceipt(String variant) throws Exception {
        var seed=seed("complete");var command=new LedgerCommandData(jdbc).command(seed,"NORMAL_CONFIRM");
        byte[] bytes=execute(seed,command);String receipt=json.readTree(bytes).path("receiptId").asText();
        damage(variant,receipt,seed.user());
        seed.sendPda(get("/api/v1/pda/temporary-command-receipts/"+command.path("commandId").asText()+"/original").param("warehouseId",seed.warehouse()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COMMAND_RECEIPT_INTEGRITY_FAILURE"));
        new LedgerOriginalFacts(jdbc).save("corrupt-"+variant+"-"+command.path("commandId").asText()+".json",Map.of("case",variant,"httpStatus",409,"receiptId",receipt));
    }
    private void damage(String kind,String receipt,String user) throws Exception {
        switch(kind) {
            case "request-resource" -> jdbc.update("UPDATE api_idempotency_request SET resource_id=UUID_TO_BIN(?) WHERE resource_id=UUID_TO_BIN(?)",UUID.randomUUID().toString(),receipt);
            case "receipt-request" -> {
                String request=UUID.randomUUID().toString();
                jdbc.update("INSERT INTO api_idempotency_request VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'ledger.verify.orphan',?,?,UUID_TO_BIN(?),UTC_TIMESTAMP(3))",
                    request,user,UUID.randomUUID().toString(),"0".repeat(64),receipt);
                jdbc.update("UPDATE temporary_command_receipt SET api_request_id=UUID_TO_BIN(?) WHERE id=UUID_TO_BIN(?)",request,receipt);
            }
            case "command-blob" -> jdbc.update("UPDATE temporary_command_receipt SET canonical_command_bytes=? WHERE id=UUID_TO_BIN(?)",new byte[]{123,125},receipt);
            case "response-blob" -> jdbc.update("UPDATE temporary_command_receipt SET original_response_bytes=? WHERE id=UUID_TO_BIN(?)",new byte[]{123,125},receipt);
            case "response-hash" -> jdbc.update("UPDATE temporary_command_receipt SET original_response_sha256=? WHERE id=UUID_TO_BIN(?)","0".repeat(64),receipt);
            case "signature" -> damageSignature(receipt);
            case "unknown-key" -> jdbc.update("UPDATE temporary_command_receipt SET signature_key_id='unknown-ledger-test-key' WHERE id=UUID_TO_BIN(?)",receipt);
            case "event-context" -> jdbc.update("UPDATE temporary_decision_event SET context_bytes=? WHERE receipt_id=UUID_TO_BIN(?)",new byte[]{123,125},receipt);
            case "audit-missing" -> jdbc.update("UPDATE operation_event SET entity_type='LEDGER_VERIFY_DAMAGED' WHERE entity_type='TEMPORARY_COMMAND' AND entity_id=UUID_TO_BIN(?)",receipt);
            default -> throw new IllegalArgumentException(kind);
        }
    }
    private void damageSignature(String receipt) throws Exception {
        byte[] raw=jdbc.queryForObject("SELECT original_response_bytes FROM temporary_command_receipt WHERE id=UUID_TO_BIN(?)",byte[].class,receipt);
        var outer=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(raw);String token=outer.path("signedReceipt").asText();
        int index=token.lastIndexOf('.')+1;token=token.substring(0,index)+(token.charAt(index)=='A'?'B':'A')+token.substring(index+1);
        outer.put("signedReceipt",token);byte[] changed=com.acrylic.outbound.temporary.protocol.TemporaryProtocol.canonical(outer.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String hash=LedgerDatabaseFacts.hash(changed);
        jdbc.update("UPDATE temporary_command_receipt SET original_response_bytes=?,original_response_sha256=?,signed_receipt=? WHERE id=UUID_TO_BIN(?)",changed,hash,token,receipt);
        jdbc.update("UPDATE operation_event SET details=JSON_SET(details,'$.originalResponseSha256',?) WHERE entity_type='TEMPORARY_COMMAND' AND entity_id=UUID_TO_BIN(?)",hash,receipt);
    }
}
