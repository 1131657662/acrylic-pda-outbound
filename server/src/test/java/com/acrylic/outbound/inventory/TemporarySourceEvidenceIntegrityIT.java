package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
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

@SpringBootTest(properties="outbound.signing.historical-public-keys=${outbound.storage}/missing-history-for-classification.der")
@AutoConfigureMockMvc
class TemporarySourceEvidenceIntegrityIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    @Value("${outbound.storage}") String storage;
    TemporarySourceEvidenceFixture fixture(boolean large) throws Exception {
        return new TemporarySourceEvidenceFixture(new TemporarySourceEvidenceFixture.Inputs(jdbc,mvc,json,passwords,storage),large);
    }
    @Test void damagedPayloadFailsClosedWithoutRepairingBytes() throws Exception {
        var f=fixture(false);f.temporary.submit();f.temporary.abort();
        var path=Path.of(storage,"submissions",SubmissionJson.sha(f.temporary.body)+".json");byte[] original=Files.readAllBytes(path);
        try {
            byte[] damaged=original.clone();damaged[0]^=1;Files.write(path,damaged);var before=f.facts();
            f.reviewer.send(f.request("")).andExpect(status().isConflict());
            f.reviewer.send(f.request("/payload")).andExpect(status().isConflict());assertThat(f.facts()).isEqualTo(before);
        } finally { Files.write(path,original); }
    }
    @Test void absentPayloadFileIsNotRecreatedFromDatabaseMetadata() throws Exception {
        var f=fixture(false);f.temporary.submit();var path=Path.of(storage,"submissions",SubmissionJson.sha(f.temporary.body)+".json");
        var held=path.resolveSibling(path.getFileName()+".held");Files.move(path,held);
        try {
            var before=f.facts();f.reviewer.send(f.request("/payload")).andExpect(status().isConflict());
            assertThat(Files.exists(path)).isFalse();assertThat(f.facts()).isEqualTo(before);
        } finally { Files.move(held,path); }
    }
    @Test void damagedChunkIsNotReturnedAsAnAcceptedOrValidFile() throws Exception {
        var f=fixture(true);var bytes=f.begin();var part=Arrays.copyOfRange(bytes,0,262144);f.chunk(0,part);f.temporary.abort();
        String hash=com.acrylic.outbound.inventory.ImportFiles.sha256(part);var path=Path.of(storage,"submissions",hash+".json");
        try {
            byte[] damaged=part.clone();damaged[0]^=1;Files.write(path,damaged);
            f.reviewer.send(f.request("/chunks/0")).andExpect(status().isConflict());f.reviewer.send(f.request("")).andExpect(status().isConflict());
        } finally { Files.write(path,part); }
    }
    @Test void invalidStoredSignatureIsPreservedAsUnverifiedEvidence() throws Exception {
        var f=fixture(false);f.temporary.abort();String original=f.storedReceipt();var receipt=SubmissionJson.parse(original);
        ((com.fasterxml.jackson.databind.node.ObjectNode)receipt).put("signedReceipt","invalid.token.signature");String modified=SubmissionJson.canonical(receipt);
        try {
            jdbc.update("UPDATE submission SET receipt_text=? WHERE id=UUID_TO_BIN(?)",modified,f.temporary.id());
            var before=f.facts();var view=f.read();
            assertThat(view.at("/terminalProof/verification").asText()).isEqualTo("INVALID_SIGNATURE");
            assertThat(view.at("/terminalProof/originalStoredReceiptUtf8").asText()).isEqualTo(modified);
            assertThat(view.at("/currentAssessment/gaps").toString()).contains("VERIFIED_TERMINAL_PROOF_NOT_AVAILABLE");
            assertCapabilitiesFalse(view);
            assertThat(f.facts()).isEqualTo(before);
        } finally { jdbc.update("UPDATE submission SET receipt_text=? WHERE id=UUID_TO_BIN(?)",original,f.temporary.id()); }
    }
    @Test void unavailableHistoricalKeyRemainsUnverifiedAndPreservesOriginalEvidence() throws Exception {
        var f=fixture(false);f.temporary.abort();String original=f.storedReceipt();
        assertThat(f.read().at("/terminalProof/verification").asText()).isEqualTo("VERIFIED");
        assertThat(Path.of(storage,"missing-history-for-classification.der")).doesNotExist();
        var receipt=(com.fasterxml.jackson.databind.node.ObjectNode)SubmissionJson.parse(original);
        var parts=receipt.path("signedReceipt").asText().split("\\.");
        var header=(com.fasterxml.jackson.databind.node.ObjectNode)SubmissionJson.parse(Base64.getUrlDecoder().decode(parts[0]));
        header.put("kid","unknown-history-classification-test");
        parts[0]=Base64.getUrlEncoder().withoutPadding().encodeToString(SubmissionJson.canonical(header).getBytes(StandardCharsets.UTF_8));
        receipt.put("signedReceipt",String.join(".",parts));String modified=SubmissionJson.canonical(receipt);
        try {
            jdbc.update("UPDATE submission SET receipt_text=? WHERE id=UUID_TO_BIN(?)",modified,f.temporary.id());
            var before=f.facts();var view=f.read();
            assertThat(view.at("/terminalProof/verification").asText()).isEqualTo("UNVERIFIABLE_KEY");
            assertThat(view.at("/terminalProof/originalStoredReceiptUtf8").asText()).isEqualTo(modified);
            assertThat(view.at("/currentAssessment/gaps").toString()).contains("VERIFIED_TERMINAL_PROOF_NOT_AVAILABLE");
            assertCapabilitiesFalse(view);assertThat(f.facts()).isEqualTo(before);
        } finally { jdbc.update("UPDATE submission SET receipt_text=? WHERE id=UUID_TO_BIN(?)",original,f.temporary.id()); }
        assertThat(f.read().at("/terminalProof/verification").asText()).isEqualTo("VERIFIED");
    }
    private static void assertCapabilitiesFalse(JsonNode view) {
        for(String key:List.of("writeAllowed","writeConfirmationAvailable","releaseAllowed","mayResumeScanning",
            "stockReleaseAllowed","localDeletionAllowed","evidenceUploadAllowed"))
            assertThat(view.at("/currentAssessment/"+key).asBoolean()).as(key).isFalse();
    }
    @Test void validBodyWithWrongOriginalControlIsNeverReturnedUnderAnotherSubmission() throws Exception {
        var f=fixture(false);f.temporary.submit();var another=fixture(false);another.temporary.submit();
        var replacement=jdbc.queryForMap("SELECT payload_sha256,byte_count,file_key FROM submission_payload WHERE submission_id=UUID_TO_BIN(?)",another.temporary.id());
        var original=jdbc.queryForMap("SELECT payload_sha256,byte_count,file_key FROM submission_payload WHERE submission_id=UUID_TO_BIN(?)",f.temporary.id());
        try {
            jdbc.update("UPDATE submission_payload SET payload_sha256=?,byte_count=?,file_key=? WHERE submission_id=UUID_TO_BIN(?)",
                replacement.get("payload_sha256"),replacement.get("byte_count"),replacement.get("file_key"),f.temporary.id());
            f.reviewer.send(f.request("/payload")).andExpect(status().isConflict());
        } finally {
            jdbc.update("UPDATE submission_payload SET payload_sha256=?,byte_count=?,file_key=? WHERE submission_id=UUID_TO_BIN(?)",
                original.get("payload_sha256"),original.get("byte_count"),original.get("file_key"),f.temporary.id());
        }
    }
}
