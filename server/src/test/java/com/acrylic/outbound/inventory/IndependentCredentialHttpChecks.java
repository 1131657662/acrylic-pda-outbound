package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

public final class IndependentCredentialHttpChecks {
    private IndependentCredentialHttpChecks() { }
    public static void disabled(WebApplicationContext context) throws Exception {
        var wire=new IndependentCredentialHttpWire(context);var response=wire.issue(UUID.randomUUID());
        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(wire.code(response)).isEqualTo("INDEPENDENT_CAPTURE_DISABLED");assertThat(wire.count()).isZero();
        assertThat(wire.get(UUID.randomUUID().toString()).statusCode()).isEqualTo(409);
    }
    public static void verify(WebApplicationContext context,Path attempt) throws Exception {
        var wire=new IndependentCredentialHttpWire(context);UUID key=UUID.randomUUID();
        var before=IndependentCredentialConservation.snapshot(wire.jdbc);
        var response=wire.issue(key);var original=wire.data(response,200);
        assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
        assertThat(wire.data(wire.issue(key),200)).isEqualTo(original);assertThat(wire.count()).isEqualTo(1);
        assertThat(wire.data(wire.get(original.path("credentialId").asText()),200)).isEqualTo(original);
        var one=CompletableFuture.supplyAsync(()->issue(wire,key));var two=CompletableFuture.supplyAsync(()->issue(wire,key));
        assertThat(one.get(45,TimeUnit.SECONDS)).isEqualTo(original);assertThat(two.get(45,TimeUnit.SECONDS)).isEqualTo(original);
        verifyClaims(context,wire,original,attempt);
        IndependentCredentialSubjectChecks.verify(context,wire,original,key);
        IndependentCredentialBoundaryChecks.verify(wire,original,key);
        var after=IndependentCredentialConservation.snapshot(wire.jdbc);
        assertThat(after).as("all business rows and checksums preserved").isEqualTo(before);
        Files.writeString(attempt.resolve("independent-http-checks.json"),context.getBean(com.fasterxml.jackson.databind.ObjectMapper.class)
            .writeValueAsString(Map.of("passed",true,"identityClass","registered synthetic real-HTTP fixture",
                "businessBefore",before,"businessAfter",after,"noStockOrSubmissionCreated",true)));
    }
    private static JsonNode issue(IndependentCredentialHttpWire wire,UUID key) {
        try { return wire.data(wire.issue(key),200); } catch(Exception error) { throw new CompletionException(error); }
    }
    private static void verifyClaims(WebApplicationContext context,IndependentCredentialHttpWire wire,JsonNode original,Path attempt) throws Exception {
        var claims=SubmissionJson.parse(Base64.getUrlDecoder().decode(original.path("signedCredential").asText().split("\\.")[1]));
        assertThat(claims.path("operatorId").asText()).isEqualTo(wire.web.identity().user().id().toString());
        assertThat(claims.path("deviceId").asText()).isEqualTo(wire.device.id());
        assertThat(claims.path("warehouseId").asText()).isEqualTo(wire.web.identity().warehouse().toString());
        assertThat(claims.path("deviceKeySha256").asText()).isEqualTo(ImportFiles.sha256(wire.device.key().getPublic().getEncoded()));
        assertThat(Duration.between(Instant.parse(original.path("issuedAt").asText()),Instant.parse(original.path("expiresAt").asText()))).isEqualTo(Duration.ofHours(48));
        long events=wire.jdbc.queryForObject("SELECT COUNT(*) FROM operation_event WHERE entity_id=? AND event_type='INDEPENDENT_CREDENTIAL_ISSUED'",
            Long.class,Ids.bytes(UUID.fromString(original.path("credentialId").asText())));assertThat(events).isEqualTo(1);
        Files.writeString(attempt.resolve("independent-original-vector.json"),context.getBean(com.fasterxml.jackson.databind.ObjectMapper.class)
            .writeValueAsString(Map.of("receipt",original,"claims",claims,"signingKey",context.getBean(OfflineSigner.class).publicKey(),
                "identityClass","registered synthetic real-HTTP identity")));
    }
}
