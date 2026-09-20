package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class TemporarySourceEvidenceIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    @Value("${outbound.storage}") String storage;
    TemporarySourceEvidenceFixture fixture(boolean large) throws Exception {
        return new TemporarySourceEvidenceFixture(new TemporarySourceEvidenceFixture.Inputs(jdbc,mvc,json,passwords,storage),large);
    }
    @Test void abortBeforeBodyKeepsNullableIdentityAndExactStoredProofWithoutWrites() throws Exception {
        var f=fixture(false);f.temporary.abort();String receipt=f.storedReceipt();var before=f.facts();var view=f.read();
        assertThat(view.at("/mapping/canonicalOrderId").isNull()).isTrue();assertThat(view.at("/mapping/order").isNull()).isTrue();
        assertThat(view.at("/mapping/slot").isNull()).isTrue();assertThat(view.at("/control/assignmentId").isNull()).isTrue();
        assertThat(view.at("/receivedPayload/received").asBoolean()).isFalse();assertThat(view.at("/declaredUpload/present").asBoolean()).isFalse();
        assertThat(view.at("/terminalProof/originalStoredReceiptUtf8").asText()).isEqualTo(receipt);
        assertThat(view.at("/terminalProof/verification").asText()).isEqualTo("VERIFIED");
        assertThat(view.at("/physical/knownCommittedBaleCount").asText()).isEqualTo("0");
        assertThat(view.at("/physical/vehicleEmptyProven").asBoolean()).isFalse();assertThat(view.at("/physical/complete").asBoolean()).isFalse();
        f.reviewer.send(f.request("/payload")).andExpect(status().isNotFound());
        assertThat(f.facts()).isEqualTo(before);assertCapabilitiesFalse(view);
    }
    @Test void partialChunksThenAbortKeepDeclarationSeparateFromAcceptedBody() throws Exception {
        var f=fixture(true);var bytes=f.begin();var chunk=Arrays.copyOfRange(bytes,0,262144);f.chunk(0,chunk);f.temporary.abort();
        var before=f.facts();String receipt=f.storedReceipt();var view=f.read();
        assertThat(view.at("/declaredUpload/declaredSha256").asText()).isEqualTo(SubmissionJson.sha(f.temporary.body));
        assertThat(view.at("/declaredUpload/receivedChunks")).hasSize(1);
        assertThat(view.at("/receivedPayload/sha256").isNull()).isTrue();assertThat(view.at("/mapping/canonicalOrderId").isNull()).isTrue();
        var download=f.reviewer.send(f.request("/chunks/0")).andExpect(status().isOk()).andReturn().getResponse();
        assertThat(download.getContentAsByteArray()).isEqualTo(chunk);assertThat(download.getHeader("Cache-Control")).contains("no-store");
        f.reviewer.send(f.request("/payload")).andExpect(status().isNotFound());
        f.reviewer.send(f.request("/chunks/1")).andExpect(status().isNotFound());
        assertThat(view.at("/terminalProof/originalStoredReceiptUtf8").asText()).isEqualTo(receipt);
        assertThat(json.readTree(receipt).path("payloadSha256").isNull()).isTrue();assertThat(f.facts()).isEqualTo(before);
    }
    @Test void acceptedBodyThenAbortReturnsOriginalBytesAndKeepsTargetAndReservations() throws Exception {
        var f=fixture(false);var accepted=f.temporary.submit();f.temporary.abort();var before=f.facts();var view=f.read();
        assertThat(view.at("/mapping/canonicalOrderId")).isEqualTo(accepted.path("orderId"));
        assertThat(view.at("/receivedPayload/sha256").asText()).isEqualTo(SubmissionJson.sha(f.temporary.body));
        assertThat(view.at("/physical/knownCommittedBaleCount").asText()).isEqualTo("2");
        byte[] bytes=f.reviewer.send(f.request("/payload")).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(bytes).isEqualTo(SubmissionJson.canonical(f.temporary.body).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(view.at("/terminalProof/verification").asText()).isEqualTo("VERIFIED");
        assertThat(view.toString()).doesNotContain("file_key","privateKey","submissions/");assertThat(f.facts()).isEqualTo(before);
        assertCapabilitiesFalse(view);
    }
    @Test void fullChunksWithoutFinalizeAreNotAnAcceptedBody() throws Exception {
        var f=fixture(false);var bytes=f.begin();f.chunk(0,bytes);var before=f.facts();var view=f.read();
        assertThat(view.at("/declaredUpload/allDeclaredChunksReceived").asBoolean()).isTrue();
        assertThat(view.at("/declaredUpload/acceptedPayload").asBoolean()).isFalse();
        assertThat(view.at("/receivedPayload/received").asBoolean()).isFalse();assertThat(f.facts()).isEqualTo(before);
    }
    @Test void ordinaryConfirmPermissionCanReadPendingWithoutOrderReadPermission() throws Exception {
        var f=fixture(false);f.temporary.submit();var view=f.read();
        assertThat(view.at("/control/status").asText()).isEqualTo("REVIEW_REQUIRED");
        assertThat(view.at("/terminalProof/verification").asText()).isEqualTo("NOT_PRESENT");assertCapabilitiesFalse(view);
    }
    private static void assertCapabilitiesFalse(JsonNode view) {
        for(String key:List.of("writeAllowed","writeConfirmationAvailable","releaseAllowed","mayResumeScanning","stockReleaseAllowed","localDeletionAllowed","evidenceUploadAllowed"))
            assertThat(view.at("/currentAssessment/"+key).asBoolean()).as(key).isFalse();
    }
}
