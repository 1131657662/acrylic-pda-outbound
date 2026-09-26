package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.ApiErrors;
import com.acrylic.outbound.common.ApiResponses;
import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.ServerState;
import com.acrylic.outbound.pda.PdaAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SourceEvidenceIntakeControllerTest {
    private static final UUID WAREHOUSE = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID SUBMISSION = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID ORIGINAL = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");
    private static final UUID OPEN = UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd");
    private static final UUID STOP = UUID.fromString("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee");
    private static final byte[] BODY = "{\"operation\":\"OPEN\"}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] RECEIPT = "{\"operation\":\"RECEIPT\"}".getBytes(StandardCharsets.UTF_8);

    @Mock private PdaAccess access;
    @Mock private SourceEvidenceIntakeOpenService opens;
    @Mock private SourceEvidenceIntakePageService pages;
    @Mock private SourceEvidenceIntakeStopService stops;
    @Mock private SourceEvidenceIntakeVerifyService verifies;
    @Mock private ServerState state;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        lenient().when(state.current()).thenReturn(new ServerState.State("7", "NORMAL"));
        var controller = new SourceEvidenceIntakeController(access, opens, pages, stops, verifies);
        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new ApiErrors(new ApiResponses(state, mapper))).build();
    }

    @Test
    void openBindsRouteValuesAndReturnsRawReceiptHeaders() throws Exception {
        when(opens.open(any())).thenReturn(RECEIPT);

        mvc.perform(post("/api/v1/pda/temporary-source-evidence/open")
                .queryParam("warehouseId", WAREHOUSE.toString())
                .queryParam("submissionId", SUBMISSION.toString())
                .queryParam("workRound", "3")
                .queryParam("originalKey", ORIGINAL.toString())
                .header("X-Server-Recovery-Epoch", "7")
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().bytes(RECEIPT))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"));

        var request = ArgumentCaptor.forClass(SourceEvidenceIntakeOpenService.OpenRequest.class);
        verify(access).require("pda:recover");
        verify(opens).open(request.capture());
        assertThat(request.getValue().warehouseId()).isEqualTo(WAREHOUSE);
        assertThat(request.getValue().submissionId()).isEqualTo(SUBMISSION);
        assertThat(request.getValue().workRound()).isEqualTo(3);
        assertThat(request.getValue().originalKey()).isEqualTo(ORIGINAL);
        assertThat(request.getValue().transportRecoveryEpoch()).isEqualTo("7");
        assertThat(request.getValue().requestBytes()).containsExactly(BODY);
    }

    @Test
    void pageBindsBothOriginalKeys() throws Exception {
        when(pages.page(any())).thenReturn(RECEIPT);

        mvc.perform(post("/api/v1/pda/temporary-source-evidence/page")
                .queryParam("warehouseId", WAREHOUSE.toString()).queryParam("submissionId", SUBMISSION.toString())
                .queryParam("workRound", "3").queryParam("originalKey", ORIGINAL.toString())
                .queryParam("openOriginalKey", OPEN.toString()).header("X-Server-Recovery-Epoch", "7")
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isOk());

        var request = ArgumentCaptor.forClass(SourceEvidenceIntakePageService.PageRequest.class);
        verify(pages).page(request.capture());
        assertThat(request.getValue().originalKey()).isEqualTo(ORIGINAL);
        assertThat(request.getValue().openOriginalKey()).isEqualTo(OPEN);
    }

    @Test
    void stopAndVerifyBindTheirDistinctKeys() throws Exception {
        when(stops.stop(any())).thenReturn(RECEIPT);
        when(verifies.verify(any())).thenReturn(RECEIPT);

        mvc.perform(post("/api/v1/pda/temporary-source-evidence/stop")
                .queryParam("warehouseId", WAREHOUSE.toString()).queryParam("submissionId", SUBMISSION.toString())
                .queryParam("workRound", "3").queryParam("originalKey", ORIGINAL.toString())
                .queryParam("openOriginalKey", OPEN.toString()).header("X-Server-Recovery-Epoch", "7")
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/pda/temporary-source-evidence/verify")
                .queryParam("warehouseId", WAREHOUSE.toString()).queryParam("submissionId", SUBMISSION.toString())
                .queryParam("workRound", "3").queryParam("originalKey", ORIGINAL.toString())
                .queryParam("openOriginalKey", OPEN.toString()).queryParam("stopOriginalKey", STOP.toString())
                .header("X-Server-Recovery-Epoch", "7").contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isOk());

        var stopRequest = ArgumentCaptor.forClass(SourceEvidenceIntakeStopService.StopRequest.class);
        var verifyRequest = ArgumentCaptor.forClass(SourceEvidenceIntakeVerifyService.VerifyRequest.class);
        verify(stops).stop(stopRequest.capture());
        verify(verifies).verify(verifyRequest.capture());
        assertThat(stopRequest.getValue().openOriginalKey()).isEqualTo(OPEN);
        assertThat(verifyRequest.getValue().openOriginalKey()).isEqualTo(OPEN);
        assertThat(verifyRequest.getValue().stopOriginalKey()).isEqualTo(STOP);
    }

    @Test
    void unauthenticatedAccessIsReturnedAsApiError() throws Exception {
        doThrow(new BusinessException(401, "UNAUTHENTICATED", "请登录PDA"))
            .when(access).require("pda:recover");

        mvc.perform(post("/api/v1/pda/temporary-source-evidence/open")
                .queryParam("warehouseId", WAREHOUSE.toString()).queryParam("submissionId", SUBMISSION.toString())
                .queryParam("workRound", "3").queryParam("originalKey", ORIGINAL.toString())
                .header("X-Server-Recovery-Epoch", "7").contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isUnauthorized()).andExpect(content().string(org.hamcrest.Matchers.containsString("UNAUTHENTICATED")));
    }

    @Test
    void missingRecoveryPermissionIsReturnedAsApiError() throws Exception {
        doThrow(new BusinessException(403, "PERMISSION_DENIED", "没有执行此PDA操作的权限"))
            .when(access).require("pda:recover");

        mvc.perform(post("/api/v1/pda/temporary-source-evidence/verify")
                .queryParam("warehouseId", WAREHOUSE.toString()).queryParam("submissionId", SUBMISSION.toString())
                .queryParam("workRound", "3").queryParam("originalKey", ORIGINAL.toString())
                .queryParam("openOriginalKey", OPEN.toString()).queryParam("stopOriginalKey", STOP.toString())
                .header("X-Server-Recovery-Epoch", "7").contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden()).andExpect(content().string(org.hamcrest.Matchers.containsString("PERMISSION_DENIED")));
    }
}
