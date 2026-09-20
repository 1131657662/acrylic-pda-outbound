package com.acrylic.outbound.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.IllegalTransactionStateException;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class IdentityLockAuthenticationIT extends IdentityLockFixture {
    @ParameterizedTest @ValueSource(strings={"login","refresh","logout"})
    void personnelSessionRevocationSerializesAuthentication(String action) throws Exception {
        var web=web();var pda=new PdaTestDriver(web);var device=pda.register();var tokens=pda.login(device);
        var body=switch(action) {
            case "login" -> pda.loginBody(device);
            case "refresh" -> pda.refreshBody(device,tokens.get("refreshToken").asText());
            default -> json.createObjectNode();
        };
        var request=pda.command("/auth/"+action,body);long sessions=count("SELECT COUNT(*) FROM pda_session WHERE device_id=UUID_TO_BIN(?)",device.id());
        String result=afterUserDisabled("C03-"+action,web,()->code(action.equals("logout")?pda.send(request,tokens):mvc.perform(request)));
        assertThat(result).isEqualTo(switch(action) { case "login" -> "INVALID_CREDENTIALS";case "refresh" -> "AUTH_EXPIRED";default -> "OK"; });
        assertThat(count("SELECT COUNT(*) FROM pda_session WHERE device_id=UUID_TO_BIN(?)",device.id())).isEqualTo(sessions);
        assertThat(count("SELECT COUNT(*) FROM pda_session WHERE device_id=UUID_TO_BIN(?) AND revoked_at IS NULL",device.id())).isZero();
        if(body.has("challengeId")) assertThat(count("SELECT COUNT(*) FROM device_challenge WHERE id=UUID_TO_BIN(?) AND used_at IS NULL",body.get("challengeId").asText())).isEqualTo(1);
    }
    @Test void recoveryKeepsExistingAuthenticationAvailableAndOrdinaryWritesClosed() throws Exception {
        var web=web();var pda=new PdaTestDriver(web);var device=pda.register();
        jdbc.update("UPDATE server_state SET service_mode='RECOVERY_PENDING' WHERE id=1");
        try {
            JsonNode tokens=pda.login(device);
            var refreshed=pda.data(pda.command("/auth/refresh",pda.refreshBody(device,tokens.get("refreshToken").asText())));
            assertThat(code(pda.send(pda.command("/auth/logout",json.createObjectNode()),refreshed))).isEqualTo("OK");
            var body=json.createObjectNode().put("expectedVersion","1").put("status","DISABLED").put("reason","恢复期禁止日常修改");
            assertThat(code(web.send(post("/api/v1/devices/"+device.id()+"/status").header("Idempotency-Key",UUID.randomUUID().toString())
                .contentType("application/json").content(body.toString())))).isEqualTo("SERVER_RECOVERY_REQUIRED");
            assertThat(count("SELECT COUNT(*) FROM device WHERE id=UUID_TO_BIN(?) AND status='ACTIVE'",device.id())).isEqualTo(1);
        } finally { jdbc.update("UPDATE server_state SET service_mode='NORMAL' WHERE id=1"); }
    }
    @Test void prefixCannotAcquireLocksOutsideCallerTransaction() {
        assertThatThrownBy(()->guard.lock("1")).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(guard::authentication).isInstanceOf(IllegalTransactionStateException.class);
    }
}
