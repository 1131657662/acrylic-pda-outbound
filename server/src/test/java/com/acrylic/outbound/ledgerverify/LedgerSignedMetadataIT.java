package com.acrylic.outbound.ledgerverify;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LedgerSignedMetadataIT extends LedgerRuntimeFixture {
    @ParameterizedTest @ValueSource(strings={"unchanged-control","version-string","version-null","actor-partial-web",
        "actor-foreign-session","actor-number-version","permission-unknown-field","permission-duplicate","permission-wrong-type","source-foreign-operator"})
    void readerChecksSemanticsEvenWhenMalformedReceiptHasValidSignature(String variant) throws Exception {
        var seed=seed("complete");var command=new LedgerCommandData(jdbc).command(seed,"NORMAL_CONFIRM");
        var response=(ObjectNode)json.readTree(execute(seed,command));
        if(variant.equals("actor-foreign-session")) {
            var other=seed("complete");response.with("actor").put("sessionId",other.tokens().path("sessionId").asText())
                .put("deviceId",other.tokens().path("deviceId").asText()).put("deviceVersion",other.tokens().path("deviceVersion").asText());
        }
        mutate(response,variant);
        new LedgerResignedData(jdbc).replace(response);
        var request=get("/api/v1/pda/temporary-command-receipts/"+command.path("commandId").asText()+"/original").param("warehouseId",seed.warehouse());
        var result=seed.sendPda(request);
        if(variant.equals("unchanged-control"))result.andExpect(status().isOk());
        else result.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COMMAND_RECEIPT_INTEGRITY_FAILURE"));
        new LedgerOriginalFacts(jdbc).save("signed-metadata-"+variant+"-"+command.path("commandId").asText()+".json",
            Map.of("case",variant,"httpStatus",result.andReturn().getResponse().getStatus(),"receiptId",response.path("receiptId").asText(),"validRsaProof",true));
    }
    private void mutate(ObjectNode response,String variant) {
        var actor=(ObjectNode)response.get("actor");var permissions=(ObjectNode)actor.get("permissionSnapshot");
        switch(variant) {
            case "unchanged-control" -> { }
            case "version-string" -> response.put("receiptVersion","1");
            case "version-null" -> response.putNull("receiptVersion");
            case "actor-partial-web" -> actor.putNull("sessionId");
            case "actor-foreign-session" -> actor.put("sessionId",UUID.randomUUID().toString());
            case "actor-number-version" -> actor.put("userAuthVersion",1);
            case "permission-unknown-field" -> permissions.put("extra",true);
            case "permission-duplicate" -> permissions.withArray("effectivePermissions").add(permissions.path("effectivePermissions").get(0));
            case "permission-wrong-type" -> permissions.put("warehouseIds",response.at("/originalContext/warehouseId").asText());
            case "source-foreign-operator" -> ((ObjectNode)response.get("originalContext")).put("originalOperatorId",UUID.randomUUID().toString());
            default -> throw new IllegalArgumentException(variant);
        }
    }
}
