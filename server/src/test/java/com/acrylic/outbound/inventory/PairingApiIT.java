package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class PairingApiIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    private PublicationDriver web;private PairingTestDriver pair;
    @BeforeEach void fixture() throws Exception {
        web=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));pair=PairingTestDriver.fresh(web);
    }
    @Test void bothSidesMustConfirmBeforeRealPrivateKeyLogin() throws Exception {
        var application=pair.call("CREATE");assertThat(application.get("shortCode").asText()).matches("[0-9]{8}");
        var lookup=web.data(get("/api/v1/device-pairings").param("code",application.get("shortCode").asText()));
        assertThat(lookup.get("applicationId").asText()).isEqualTo(pair.id());
        mvc.perform(pair.command("CONFIRM",pair.proof("CONFIRM"))).andExpect(status().isConflict());
        pair.approve();assertThat(pair.call("STATUS").get("deviceId").isNull()).isTrue();
        var result=pair.call("CONFIRM");assertThat(result.get("status").asText()).isEqualTo("COMPLETED");
        var pda=new PdaTestDriver(web);var device=new PdaTestDriver.Device(result.get("deviceId").asText(),pair.key());
        var tokens=pda.login(device);pda.send(get("/api/v1/pda/devices/me"),tokens).andExpect(status().isOk());
    }
    @Test void responseLossRetriesPreserveCodeApprovalAndCompletedIdentityAfterExpiry() throws Exception {
        var first=pair.call("CREATE");assertThat(pair.call("CREATE")).isEqualTo(first);
        String key=UUID.randomUUID().toString();var body=pair.approvalBody();var approval=web.data(pair.approval(body,key));
        assertThat(web.data(pair.approval(body,key))).isEqualTo(approval);
        pair.payload().put("approvalSha256",approval.get("approvalSha256").asText());var result=pair.call("CONFIRM");
        jdbc.update("UPDATE device_pairing SET expires_at=TIMESTAMPADD(MINUTE,-1,UTC_TIMESTAMP(3)) WHERE id=?",Ids.bytes(UUID.fromString(pair.id())));
        assertThat(pair.call("STATUS").get("deviceId")).isEqualTo(result.get("deviceId"));
        assertThat(pair.call("CONFIRM").get("deviceId")).isEqualTo(result.get("deviceId"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM device WHERE public_key=?",Integer.class,pair.payload().get("publicKey").asText())).isEqualTo(1);
    }
    @Test void twoDevicesCannotClaimEachOthersApplicationEvenKnowingCodeAndCredential() throws Exception {
        var one=pair.call("CREATE");var other=PairingTestDriver.fresh(web);var two=other.call("CREATE");
        assertThat(one.get("shortCode")).isNotEqualTo(two.get("shortCode"));
        other.payload().put("applicationId",pair.id()).put("credential",pair.payload().get("credential").asText());
        mvc.perform(other.command("STATUS",other.proof("STATUS"))).andExpect(status().isUnauthorized());
        assertThat(pair.call("STATUS").get("status").asText()).isEqualTo("WAITING");
    }
    @Test void forgedSignatureReplayedChallengeAndChangedBodyAreRejected() throws Exception {
        var proof=pair.proof("CREATE");
        mvc.perform(pair.command("CREATE",proof.deepCopy().put("signature","AAAA"))).andExpect(status().isUnauthorized());
        pair.data(pair.command("CREATE",proof));mvc.perform(pair.command("CREATE",proof)).andExpect(status().isUnauthorized());
        var changed=pair.proof("STATUS");((com.fasterxml.jackson.databind.node.ObjectNode)changed.get("payload")).put("model","other");
        mvc.perform(pair.command("STATUS",changed)).andExpect(status().isUnauthorized());
    }
    @Test void expiredApplicationCannotRegisterAndSameKeyCanStartAgain() throws Exception {
        pair.call("CREATE");pair.approve();
        jdbc.update("UPDATE device_pairing SET expires_at=TIMESTAMPADD(MINUTE,-1,UTC_TIMESTAMP(3)) WHERE id=?",Ids.bytes(UUID.fromString(pair.id())));
        assertThat(pair.call("CONFIRM").get("status").asText()).isEqualTo("EXPIRED");
        pair.payload().put("applicationId",UUID.randomUUID().toString()).put("approvalSha256","");
        assertThat(pair.call("CREATE").get("status").asText()).isEqualTo("WAITING");
    }
    @Test void finalConfirmationRechecksAdministratorAuthority() throws Exception {
        pair.call("CREATE");pair.approve();
        jdbc.update("UPDATE app_user SET auth_version=auth_version+1 WHERE id=?",Ids.bytes(web.identity().user().id()));
        mvc.perform(pair.command("CONFIRM",pair.proof("CONFIRM"))).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("PAIRING_APPROVER_REVOKED"));
        assertThat(pair.call("STATUS").get("deviceId").isNull()).isTrue();
        assertThat(pair.call("CANCEL").get("status").asText()).isEqualTo("CANCELLED");
    }
    @Test void approvedDetailsCannotBeReplacedAndPdaMustConfirmTheirHash() throws Exception {
        pair.call("CREATE");pair.approve();
        mvc.perform(pair.command("CONFIRM",pair.proof("STATUS"))).andExpect(status().isUnauthorized());
        pair.payload().put("approvalSha256","0".repeat(64));
        mvc.perform(pair.command("CONFIRM",pair.proof("CONFIRM"))).andExpect(status().isConflict());
        web.send(pair.approval(pair.approvalBody().put("name","different"),UUID.randomUUID().toString())).andExpect(status().isConflict());
    }
    @Test void cancellationPreventsLaterConfirmationWithoutCreatingDevice() throws Exception {
        pair.call("CREATE");pair.approve();assertThat(pair.call("CANCEL").get("status").asText()).isEqualTo("CANCELLED");
        assertThat(pair.call("CONFIRM").get("deviceId").isNull()).isTrue();
    }
    @Test void existingRegisteredKeyCannotCreateAnotherIdentityOrReleaseAssignment() throws Exception {
        var pda=new PdaTestDriver(web);var device=pda.register();var order=pda.readyOrder();var tokens=pda.login(device);
        var assignment=pda.data(pda.claim(order,UUID.randomUUID().toString()),tokens);
        var p=pair.payload().deepCopy().put("publicKey",java.util.Base64.getEncoder().encodeToString(device.key().getPublic().getEncoded()));
        var already=new PairingTestDriver(web,device.key(),p,pair.remote());
        mvc.perform(already.command("CREATE",already.proof("CREATE"))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEVICE_ALREADY_REGISTERED"));
        assertThat(web.data(get("/api/v1/orders/"+order+"/assignment")).at("/assignment/id")).isEqualTo(assignment.get("id"));
    }
}
