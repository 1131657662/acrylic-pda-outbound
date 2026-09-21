package com.acrylic.outbound.ledgerverify;

import com.acrylic.outbound.inventory.LedgerVerifySeed;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LedgerReadAccessIT extends LedgerRuntimeFixture {
    @ParameterizedTest @ValueSource(strings={"web-detail","web-original","pda-detail","pda-original"})
    void everyReadRouteRechecksCurrentActorWarehouseAndReadPermission(String route) throws Exception {
        var owner=seed("complete");var outsider=seed("complete");var command=new LedgerCommandData(jdbc).command(owner,"NORMAL_CONFIRM");byte[] original=execute(owner,command);
        String path="/api/v1/"+(route.startsWith("pda")?"pda/":"")+"temporary-command-receipts/"+command.path("commandId").asText()
            +(route.endsWith("original")?"/original":"");
        var okay=send(owner,path,owner.warehouse(),route);okay.andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        if(route.endsWith("original"))okay.andExpect(content().bytes(original));
        for(String warehouse:List.of(owner.warehouse(),outsider.warehouse()))send(outsider,path,warehouse,route)
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("COMMAND_RECEIPT_NOT_FOUND"));
        var mutation=owner.userChange();mutation.putArray("roles").add("PDA_OPERATOR");owner.changeUser(mutation);owner.relogin();
        send(owner,path,owner.warehouse(),route).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("COMMAND_RECEIPT_NOT_FOUND"));
    }
    private org.springframework.test.web.servlet.ResultActions send(LedgerVerifySeed seed,String path,String warehouse,String route) throws Exception {
        return route.startsWith("pda")?seed.sendPda(get(path).param("warehouseId",warehouse)):seed.sendCurrentWeb(get(path),warehouse);
    }
}
