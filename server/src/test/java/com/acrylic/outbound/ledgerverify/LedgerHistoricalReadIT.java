package com.acrylic.outbound.ledgerverify;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.inventory.LedgerVerifySeed;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LedgerHistoricalReadIT extends LedgerRuntimeFixture {
    @Test void historicalMappingAndReceiptSurviveSeveralCurrentContextChanges() throws Exception {
        var seed=seed("complete");var command=new LedgerCommandData(jdbc).command(seed,"NORMAL_CONFIRM");byte[] receipt=execute(seed,command);
        String other=seed.otherOrder();String scope=json.readTree(receipt).at("/originalContext/scopeId").asText();
        // Explicit synthetic pointer evolution, not a product LINK or source-write capability.
        jdbc.update("INSERT INTO temporary_source_mapping SELECT UUID_TO_BIN(?),warehouse_id,id,mapping_version+1,UUID_TO_BIN(?),'INITIAL_ACCEPTANCE',UTC_TIMESTAMP(3) FROM submission_scope WHERE id=UUID_TO_BIN(?)",UUID.randomUUID().toString(),other,scope);
        jdbc.update("UPDATE submission_scope SET canonical_order_id=UUID_TO_BIN(?),mapping_version=mapping_version+1,current_submission_id=NULL,work_round=work_round+1 WHERE id=UUID_TO_BIN(?)",other,scope);
        assertThat(original(seed,command)).isEqualTo(receipt);var view=detail(seed,command);
        assertThat(view.path("originalCommandReceiptUtf8").asText().getBytes(StandardCharsets.UTF_8)).isEqualTo(receipt);
        assertThat(reasons(view)).contains("CONTEXT_CHANGED:canonicalOrderId","CONTEXT_CHANGED:mappingVersion","CONTEXT_CHANGED:currentSubmissionId","CONTEXT_CHANGED:scopeRound","SUBMISSION_NOT_CURRENT");
        view.at("/currentEffect/capabilities").forEach(v->assertThat(v.asBoolean()).isFalse());
    }
    @Test void recoveryModeAndNewEpochAffectCurrentEffectButLeaveOriginalReadAvailable() throws Exception {
        var seed=seed("complete");var command=new LedgerCommandData(jdbc).command(seed,"NORMAL_CONFIRM");byte[] receipt=execute(seed,command);
        var before=jdbc.queryForMap("SELECT recovery_epoch,service_mode FROM server_state WHERE id=1");
        try {
            jdbc.update("UPDATE server_state SET recovery_epoch=2,service_mode='RECOVERY_PENDING' WHERE id=1");
            assertThat(original(seed,command)).isEqualTo(receipt);assertThat(execute(seed,command)).isEqualTo(receipt);
            assertThat(reasons(detail(seed,command))).contains("SERVER_NOT_NORMAL","RECOVERY_EPOCH_CHANGED");
            var next=command.deepCopy().put("commandId",UUID.randomUUID().toString());
            assertThatThrownBy(()->execute(seed,next)).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.code()).isEqualTo("SERVER_RECOVERY_REQUIRED"));
            jdbc.update("UPDATE server_state SET service_mode='NORMAL' WHERE id=1");
            assertThat(original(seed,command)).isEqualTo(receipt);
            assertThatThrownBy(()->execute(seed,next)).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.code()).isEqualTo("SERVER_RECOVERY_EPOCH_MISMATCH"));
        } finally { jdbc.update("UPDATE server_state SET recovery_epoch=?,service_mode=? WHERE id=1",before.get("recovery_epoch"),before.get("service_mode")); }
    }
    @Test void otherActorWarehouseAndMissingReadPermissionUseGenericNoDisclosure() throws Exception {
        var owner=seed("complete");var outsider=seed("complete");var command=new LedgerCommandData(jdbc).command(owner,"NORMAL_CONFIRM");execute(owner,command);
        String path="/api/v1/pda/temporary-command-receipts/"+command.path("commandId").asText()+"/original";
        for(String warehouse:List.of(owner.warehouse(),outsider.warehouse()))outsider.sendPda(get(path).param("warehouseId",warehouse))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("COMMAND_RECEIPT_NOT_FOUND"));
        var change=owner.userChange();change.putArray("roles").add("PDA_OPERATOR");owner.changeUser(change);owner.relogin();
        owner.sendPda(get(path).param("warehouseId",owner.warehouse())).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("COMMAND_RECEIPT_NOT_FOUND"));
    }
    private JsonNode detail(LedgerVerifySeed seed,ObjectNode command) throws Exception {
        return seed.pda(get("/api/v1/pda/temporary-command-receipts/"+command.path("commandId").asText()).param("warehouseId",seed.warehouse()));
    }
    private List<String> reasons(JsonNode view) {
        var values=new ArrayList<String>();view.at("/currentEffect/reasons").forEach(v->values.add(v.asText()));return values;
    }
}
