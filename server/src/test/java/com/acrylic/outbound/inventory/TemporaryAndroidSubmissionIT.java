package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.pda.PdaSessions;
import com.acrylic.outbound.submissions.*;
import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @EnabledIfEnvironmentVariable(named="R06_ANDROID_BODY_VERIFY",matches="true")
class TemporaryAndroidSubmissionIT {
    @Autowired SubmissionService submissions;@Autowired JdbcTemplate jdbc;
    @Autowired PdaSessions sessions;@Autowired com.acrylic.outbound.auth.IdentityStore identities;
    @Autowired com.acrylic.outbound.devices.DeviceStore devices;
    @Test void actualAndroidFreezeIsAcceptedAgainstOriginalServerAuthorization() throws Exception {
        var body=SubmissionJson.parse(Files.readString(Path.of("build","pda-temporary-android-body.json")));
        var a=jdbc.queryForMap("SELECT BIN_TO_UUID(user_id) user_id,BIN_TO_UUID(device_id) device_id,BIN_TO_UUID(warehouse_id) warehouse_id,user_auth_version,device_version FROM temporary_authorization WHERE id=UUID_TO_BIN(?)",body.get("authorizationId").asText());
        var device=UUID.fromString(a.get("device_id").toString());
        String username=jdbc.queryForObject("SELECT username FROM app_user WHERE id=UUID_TO_BIN(?)",String.class,a.get("user_id").toString());
        // Service/Room contract test: issue a fresh test session for the same authorized identity; HTTP login is covered separately.
        var tokens=sessions.issue(identities.loadUserByUsername(username),devices.find(device,false));
        var actor=sessions.authenticate(tokens.accessToken());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,null,java.util.List.of()));
        try {
            var result=submissions.submitTemporary(body,"1");assertThat(result.get("status").asText()).isEqualTo("REVIEW_REQUIRED");
            assertThat(result.get("payloadSha256").asText()).isEqualTo(SubmissionJson.sha(body));
            assertThat(submissions.submitTemporary(body,"1")).isEqualTo(result);
            Files.writeString(Path.of("build","pda-temporary-android-result.json"),SubmissionJson.canonical(result));
        } finally { SecurityContextHolder.clearContext(); }
    }
}
