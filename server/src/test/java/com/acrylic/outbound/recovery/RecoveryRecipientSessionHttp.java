package com.acrylic.outbound.recovery;

import com.acrylic.outbound.devices.DeviceCrypto;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.Signature;
import java.time.Duration;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;

/** Actual password and receiving-key challenge login; public evidence omits credentials and tokens. */
final class RecoveryRecipientSessionHttp {
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    Result login(RecoveryQualificationFixture fixture,Path attempt) throws Exception {
        var enrollment=fixture.enrollment; String username=enrollment.recipient.username();
        assertThat(enrollment.app.getBean(org.springframework.security.crypto.password.PasswordEncoder.class)
            .matches("Test-password-123",enrollment.recipient.passwordHash())).as("Original source credential remains unchanged").isTrue();
        var data=challenge(fixture,username);
        var proof=Signature.getInstance("SHA256withECDSA"); proof.initSign(enrollment.receivingKey.getPrivate());
        proof.update(data.path("signedText").asText().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String signature=Base64.getEncoder().encodeToString(proof.sign());
        var login=SubmissionJson.object().put("username",username).put("password","Test-password-123")
            .put("deviceId",fixture.body.path("recipient").path("deviceId").asText()).put("challengeId",data.path("challengeId").asText()).put("signature",signature);
        assertThat(post("/auth/login",login.deepCopy().put("password","wrong-password")).statusCode()).isEqualTo(401);
        assertThat(post("/auth/login",login.deepCopy().put("signature","AA==")).statusCode()).isEqualTo(401);
        var response=post("/auth/login",login); assertThat(response.statusCode()).isEqualTo(200);
        return consume(data,response,signature,attempt);
    }
    private JsonNode challenge(RecoveryQualificationFixture fixture,String username) throws Exception {
        var body=SubmissionJson.object().put("deviceId",fixture.body.path("recipient").path("deviceId").asText())
            .put("purpose","LOGIN").put("bindingSha256",DeviceCrypto.sha(username));
        var response=post("/auth/challenges",body); assertThat(response.statusCode()).isEqualTo(200);
        return SubmissionJson.parse(response.body()).path("data");
    }
    private Result consume(JsonNode data,HttpResponse<byte[]> response,String signature,Path attempt) throws Exception {
        var tokens=SubmissionJson.parse(response.body()).path("data"); String token=tokens.path("accessToken").asText();
        var me=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434/api/v1/pda/devices/me"))
            .header("Authorization","Bearer "+token).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
        assertThat(me.statusCode()).isEqualTo(200);
        Files.write(attempt.resolve("private/recipient-login-response.json"),response.body());
        var evidence=SubmissionJson.object().put("challengeHttp",200).put("wrongPasswordHttp",401).put("wrongProofHttp",401)
            .put("loginHttp",200).put("bearerHttp",200).put("sessionId",tokens.path("sessionId").asText())
            .put("accessTokenSha256",DeviceCrypto.sha(token)).put("loginRequestId",response.headers().firstValue("X-Request-Id").orElseThrow());
        Files.write(attempt.resolve("recipient-login-http.json"),RecoveryControlJson.encode(evidence));
        return new Result(data,tokens,signature,evidence.path("loginRequestId").asText());
    }
    private HttpResponse<byte[]> post(String path,JsonNode body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434/api/v1/pda"+path)).timeout(Duration.ofSeconds(20))
            .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(),HttpResponse.BodyHandlers.ofByteArray());
    }
    record Result(JsonNode challenge,JsonNode tokens,String deviceProof,String requestId) { }
}
