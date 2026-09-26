package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.pda.PdaAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SourceEvidenceIntakeRawControllerTest {
    private final UUID warehouse = UUID.randomUUID(), submission = UUID.randomUUID(), open = UUID.randomUUID();
    private final UUID original = UUID.randomUUID(), storedId = UUID.randomUUID();
    private final String hash = "a".repeat(64);
    private final byte[] bytes = {0, (byte) 255, 42};
    private final PdaAccess access = mock(PdaAccess.class);
    private final SourceEvidenceIntakeRawObjectService service = mock(SourceEvidenceIntakeRawObjectService.class);
    private MockMvc mvc;

    @BeforeEach void setUp() {
        var state = mock(ServerState.class);
        when(state.current()).thenReturn(new ServerState.State("7", "NORMAL"));
        var responses = new ApiResponses(state, new ObjectMapper().registerModule(new JavaTimeModule()));
        mvc = MockMvcBuilders.standaloneSetup(new SourceEvidenceIntakeRawController(access, service, responses))
            .setControllerAdvice(new ApiErrors(responses)).build();
    }

    @Test void streamsOriginalBytesAndReturnsBoundStorageMetadataWithoutPrivateFilePath() throws Exception {
        when(service.store(any())).thenAnswer(call -> {
            SourceEvidenceIntakeRawObjectService.RawRequest request = call.getArgument(0);
            assertThat(request.input().readAllBytes()).isEqualTo(bytes);
            assertThat(request.warehouseId()).isEqualTo(warehouse);
            assertThat(request.submissionId()).isEqualTo(submission);
            assertThat(request.openOriginalKey()).isEqualTo(open);
            assertThat(request.originalObjectId()).isEqualTo(original);
            assertThat(request.transportRecoveryEpoch()).isEqualTo("7");
            return new SourceEvidenceIntakeRawObjects.RawObject(storedId, original,
                new SourceEvidenceIntakeObjectFiles.Stored("private-file-path", hash, 3));
        });
        mvc.perform(request("CAPTURE_RECORDS", "3", hash, "7")).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(jsonPath("$.data.objectId").value(storedId.toString()))
            .andExpect(jsonPath("$.data.bytesSha256").value(hash))
            .andExpect(jsonPath("$.data.localDeletionAllowed").value(false))
            .andExpect(jsonPath("$.data.signedReceipt").doesNotExist())
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-file-path"))));
        verify(access).require("pda:recover");
    }

    @ParameterizedTest
    @CsvSource({"UNKNOWN,3,aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa,7",
        "CAPTURE_RECORDS,0,aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa,7",
        "CAPTURE_RECORDS,33554433,aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa,7",
        "CAPTURE_RECORDS,4,aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa,7",
        "CAPTURE_RECORDS,3,invalid,7", "CAPTURE_RECORDS,3,aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa,07"})
    void invalidMetadataNeverReadsOrStoresObjects(String kind, String length, String sha, String epoch) throws Exception {
        mvc.perform(request(kind, length, sha, epoch)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void deniedPermissionNeverReachesTheStorageService() throws Exception {
        doThrow(new BusinessException(403, "PERMISSION_DENIED", "没有权限")).when(access).require("pda:recover");
        mvc.perform(request("CAPTURE_RECORDS", "3", hash, "7")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void missingRecoveryEpochFailsBeforeStorage() throws Exception {
        var request = request("CAPTURE_RECORDS", "3", hash, "7");
        request.with(input -> { input.removeHeader("X-Server-Recovery-Epoch"); return input; });
        mvc.perform(request).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    private MockHttpServletRequestBuilder request(String kind, String length, String sha, String epoch) {
        return post("/api/v1/pda/temporary-source-evidence/raw").queryParam("warehouseId", warehouse.toString())
            .queryParam("submissionId", submission.toString()).queryParam("workRound", "1")
            .queryParam("openOriginalKey", open.toString()).queryParam("originalObjectId", original.toString())
            .queryParam("kind", kind).queryParam("byteCount", length).queryParam("bytesSha256", sha)
            .header("X-Server-Recovery-Epoch", epoch).contentType("application/octet-stream").content(bytes);
    }
}
