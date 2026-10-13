package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

final class IndependentCredentialSubjectChecks {
    private IndependentCredentialSubjectChecks() { }
    static void verify(WebApplicationContext context,IndependentCredentialHttpWire wire,JsonNode original,UUID key) throws Exception {
        String id=original.path("credentialId").asText();
        var other=new IndependentCredentialHttpWire(context);
        assertThat(other.get(id).statusCode()).as("different warehouse and person").isEqualTo(404);
        crossPerson(context,wire,id,key);
        disabledDevice(wire,id,key);
        immutableOriginal(wire,id);
        assertThat(wire.data(wire.issue(key),200)).isEqualTo(original);
    }
    private static void crossPerson(WebApplicationContext context,IndependentCredentialHttpWire wire,String id,UUID key) throws Exception {
        var person=IdentityFixture.create(wire.jdbc,context.getBean(PasswordEncoder.class),"PDA_OPERATOR");
        wire.jdbc.update("INSERT INTO user_warehouse VALUES(?,?)",Ids.bytes(person.user().id()),Ids.bytes(wire.web.identity().warehouse()));
        try {
            wire.login(wire.device,person.user().username());
            assertThat(wire.get(id).statusCode()).as("same device and warehouse, another person").isEqualTo(404);
        } finally { wire.login(wire.device); }
    }
    private static void disabledDevice(IndependentCredentialHttpWire wire,String id,UUID key) throws Exception {
        wire.jdbc.update("UPDATE device SET status='DISABLED' WHERE id=?",Ids.bytes(UUID.fromString(wire.device.id())));
        try {
            assertThat(wire.issue(key).statusCode()).isEqualTo(401);
            assertThat(wire.get(id).statusCode()).isEqualTo(401);
        } finally { wire.jdbc.update("UPDATE device SET status='ACTIVE' WHERE id=?",Ids.bytes(UUID.fromString(wire.device.id()))); }
    }
    private static void immutableOriginal(IndependentCredentialHttpWire wire,String id) {
        byte[] bytes=Ids.bytes(UUID.fromString(id));
        assertThatThrownBy(()->wire.jdbc.update("UPDATE independent_capture_credential SET signed_credential='altered' WHERE id=?",bytes))
            .isInstanceOf(DataAccessException.class).hasMessageContaining("INDEPENDENT_CREDENTIAL_IMMUTABLE");
        assertThatThrownBy(()->wire.jdbc.update("DELETE FROM independent_capture_credential WHERE id=?",bytes))
            .isInstanceOf(DataAccessException.class).hasMessageContaining("INDEPENDENT_CREDENTIAL_IMMUTABLE");
    }
}
