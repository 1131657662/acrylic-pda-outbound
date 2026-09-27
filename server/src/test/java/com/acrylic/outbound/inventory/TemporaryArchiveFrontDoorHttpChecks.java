package com.acrylic.outbound.inventory;

import com.acrylic.outbound.auth.SessionUser;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.devices.DeviceCrypto;
import com.acrylic.outbound.submissions.SubmissionArchiveProofs;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-HTTP verification of the TEMP archive gate ({@code outbound.temporary.archive.enabled}). Both phases POST the
 * production route on 127.0.0.1:19434 with a Bearer PDA session — never MockMvc.
 *
 * <p>Phase 1 runs against a context whose switch is at its default (off): a genuinely confirmed TEMP submission is
 * prepared with {@link TemporaryConfirmationFixture} (real command kernel, real closure), and the front door must still
 * refuse with the historical {@code 409 TEMPORARY_CONFIRMATION_REQUIRED} while archiving nothing. Phase 2 re-posts the
 * identical request against a second context started with {@code --outbound.temporary.archive.enabled=true} and checks
 * the signed archive proof plus the single durable row. The request bytes, the session and the submission are carried
 * across the two contexts, so phase 2 can only differ by the switch.
 */
public final class TemporaryArchiveFrontDoorHttpChecks {
    private static final String ROUTE = "http://127.0.0.1:19434/api/v1/pda/submissions/";
    private static final String EPOCH = "1";
    private final WebApplicationContext closedContext;private final String submissionId;private final ObjectNode request;
    private final String token;private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private TemporaryArchiveFrontDoorHttpChecks(WebApplicationContext closedContext,String submissionId,ObjectNode request,String token) {
        this.closedContext=closedContext;this.submissionId=submissionId;this.request=request;this.token=token;
    }

    /** Build the confirmed TEMP submission, the archive request and the PDA session the two phases share. */
    public static TemporaryArchiveFrontDoorHttpChecks prepare(WebApplicationContext closedContext) throws Exception {
        var fixture=new TemporaryConfirmationFixture(closedContext,false);SessionUser actor=fixture.operator();
        var request=SubmissionJson.object().put("payloadSha256",SubmissionJson.sha(fixture.source.body)).put("receiptSha256","0".repeat(64));
        var command=SubmissionJson.parse(fixture.execute(fixture.command("NORMAL_CONFIRM",actor),actor));
        request.put("receiptSha256",SubmissionJson.sha(command.at("/businessDecision/submissionReceipt")));
        String token=DeviceCrypto.secret();
        fixture.jdbc.update("""
            INSERT INTO pda_session(id,device_id,user_id,user_auth_version,device_version,access_hash,refresh_hash,
              access_expires_at,expires_at,created_at)
            VALUES(?,?,?,1,1,?,?,DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 1 DAY),
              DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 2 DAY),UTC_TIMESTAMP(3))
            """,Ids.bytes(UUID.randomUUID()),Ids.bytes(UUID.fromString(fixture.source.a.device())),Ids.bytes(actor.id()),
            DeviceCrypto.sha(token),DeviceCrypto.sha(DeviceCrypto.secret()));
        return new TemporaryArchiveFrontDoorHttpChecks(closedContext,fixture.source.id(),request,token);
    }

    /** Default switch: the front door refuses with the historical code and stores nothing. */
    public void closedByDefault() throws Exception {
        var response=send();
        assertThat(response.statusCode()).as("closed front door: "+body(response)).isEqualTo(409);
        assertThat(SubmissionJson.parse(response.body()).path("code").asText())
            .as("closed front door keeps the historical rejection code").isEqualTo("TEMPORARY_CONFIRMATION_REQUIRED");
        assertThat(archiveRows(closedContext)).as("a closed gate archives nothing").isZero();
    }

    /** Switch on: the identical request passes the wired TEMP dispatch and returns the signed archive proof. */
    public void openWhenEnabled(WebApplicationContext openContext) throws Exception {
        var response=send();
        assertThat(response.statusCode()).as("open front door: "+body(response)).isEqualTo(200);
        JsonNode proof=SubmissionJson.parse(response.body()).path("data");
        assertThat(proof.path("archiveVersion").asInt()).as("TEMP archives use manifest v2").isEqualTo(2);
        assertThat(proof.path("submissionId").asText()).isEqualTo(submissionId);
        assertThat(proof.path("localDeletionAllowed").asBoolean()).isFalse();
        assertThat(proof.path("storagePolicy").asText()).isEqualTo("SERVER_DURABLE_FILES_V1");
        assertThat(Instant.parse(proof.path("retainUntil").asText())).as("five year retention from archivedAt")
            .isEqualTo(Instant.parse(proof.path("archivedAt").asText()).atOffset(ZoneOffset.UTC).plusYears(5).toInstant());
        openContext.getBean(SubmissionArchiveProofs.class).verify(proof,"signedArchive","acrylic-pda-archive","archive");
        assertThat(archiveRows(openContext)).as("one durable archive row").isEqualTo(1);
        var replay=send();
        assertThat(archiveRows(openContext)).as("replay stores no second row").isEqualTo(1);
        // Compare the proof itself, not the envelope: requestId/serverTime are per-request by design and the first
        // response carries the freshly built manifest while a replay returns the stored one.
        assertThat(SubmissionJson.canonical(SubmissionJson.parse(replay.body()).path("data")))
            .as("repeated confirmation returns the stored proof").isEqualTo(SubmissionJson.canonical(proof));
    }

    private HttpResponse<byte[]> send() throws Exception {
        var http=HttpRequest.newBuilder(URI.create(ROUTE+submissionId+"/archive-confirmation"))
            .timeout(Duration.ofSeconds(30)).header("Content-Type","application/json")
            .header("Authorization","Bearer "+token).header("X-Server-Recovery-Epoch",EPOCH)
            .POST(HttpRequest.BodyPublishers.ofByteArray(SubmissionJson.canonical(request).getBytes(StandardCharsets.UTF_8)));
        return client.send(http.build(),HttpResponse.BodyHandlers.ofByteArray());
    }

    private int archiveRows(WebApplicationContext context) {
        return context.getBean(JdbcTemplate.class).queryForObject(
            "SELECT COUNT(*) FROM submission_archive WHERE submission_id=UUID_TO_BIN(?)",Integer.class,submissionId);
    }

    private static String body(HttpResponse<byte[]> response) { return new String(response.body(),StandardCharsets.UTF_8); }
}
