package com.acrylic.outbound.ledgerverify;

import com.acrylic.outbound.inventory.LedgerVerifySeed;
import com.acrylic.outbound.temporary.command.TemporaryCommandService;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(LedgerTestOperations.class)
abstract class LedgerRuntimeFixture extends LedgerVerifyFixture {
    @Autowired TemporaryCommandService service;
    byte[] execute(LedgerVerifySeed seed,ObjectNode command) throws Exception {
        return execute(seed,command,"verify-"+command.path("commandId").asText());
    }
    byte[] execute(LedgerVerifySeed seed,ObjectNode command,String requestId) throws Exception {
        return seed.asPda(()->service.execute(command.toString().getBytes(StandardCharsets.UTF_8),requestId));
    }
    byte[] original(LedgerVerifySeed seed,ObjectNode command) throws Exception {
        return seed.sendPda(get("/api/v1/pda/temporary-command-receipts/"+command.path("commandId").asText()+"/original")
            .param("warehouseId",seed.warehouse())).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }
}
