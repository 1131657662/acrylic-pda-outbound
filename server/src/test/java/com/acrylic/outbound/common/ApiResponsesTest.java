package com.acrylic.outbound.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Design row 20: the refusal writer answers without reading the database — the instance may be
 * exactly unable to serve what it is refusing — while the normal envelope keeps its state fields.
 */
@ExtendWith(MockitoExtension.class)
class ApiResponsesTest {
    @Mock private ServerState state;
    private ApiResponses responses;
    private MockMvc mvc;

    @BeforeEach void setUp() {
        responses = new ApiResponses(state, new ObjectMapper().registerModule(new JavaTimeModule()));
        mvc = MockMvcBuilders.standaloneSetup(new FailingController())
            .setControllerAdvice(new ApiErrors(responses)).build();
    }

    @Test void aRefusalIsWrittenWithoutReadingTheDatabase() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/outbound/things");
        request.setAttribute("requestId", "refusal-1");
        var response = new MockHttpServletResponse();

        responses.writeWithoutState(request, response, 503, "RECOVERY_LEASE_FENCED", "服务器当前不可写入，请稍后重试");

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString())
            .contains("\"code\":\"RECOVERY_LEASE_FENCED\"")
            .contains("\"requestId\":\"refusal-1\"")
            .as("no state was read, so the envelope reports it as unknown/unavailable")
            .contains("\"serverRecoveryEpoch\":\"unknown\"")
            .contains("\"serviceMode\":\"UNAVAILABLE\"");
        verifyNoInteractions(state);
    }

    @Test void theNormalEnvelopeStillCarriesTheRecoveryState() throws Exception {
        org.mockito.Mockito.when(state.current()).thenReturn(new ServerState.State("7", "NORMAL"));
        var request = new MockHttpServletRequest("GET", "/api/outbound/things");

        var envelope = responses.ok("payload", request);

        assertThat(envelope.serverRecoveryEpoch()).isEqualTo("7");
        assertThat(envelope.serviceMode()).isEqualTo("NORMAL");
        assertThat(envelope.code()).isEqualTo("OK");
    }

    @Test void anOversizedUploadIsRefusedAsTooLargeWithoutReadingTheDatabase() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/probe/upload")
                .contentType(MediaType.MULTIPART_FORM_DATA).content("not-really-multipart".getBytes(StandardCharsets.UTF_8)))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is(413))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(org.hamcrest.Matchers.containsString("FILE_TOO_LARGE")));
        verifyNoInteractions(state);
    }

    @Test void aMalformedMultipartRequestIsRefusedAsInvalidWithoutReadingTheDatabase() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/probe/malformed")
                .contentType(MediaType.MULTIPART_FORM_DATA).content("broken".getBytes(StandardCharsets.UTF_8)))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is(400))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(org.hamcrest.Matchers.containsString("INVALID_REQUEST")));
        verifyNoInteractions(state);
    }

    /** A controller that raises exactly the refusal shapes the entry can produce. */
    @RestController
    static class FailingController {
        @GetMapping("/probe/normal") String normal() { return "ok"; }

        @PostMapping("/probe/upload") String upload() { throw new MaxUploadSizeExceededException(1024L); }

        @PostMapping("/probe/malformed") String malformed() {
            throw new MultipartException("Failed to parse multipart servlet request");
        }
    }
}
