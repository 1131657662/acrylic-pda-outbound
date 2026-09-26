package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.ApiErrors;
import com.acrylic.outbound.common.ApiResponses;
import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.ServerState;
import com.acrylic.outbound.pda.PdaAccess;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SourceEvidenceIntakeReadControllerTest {
    private static final UUID WAREHOUSE=UUID.randomUUID(), SUBMISSION=UUID.randomUUID(), KEY=UUID.randomUUID();
    private final PdaAccess access=mock(PdaAccess.class);
    private final SourceEvidenceIntakeReadService service=mock(SourceEvidenceIntakeReadService.class);
    private final ServerState state=mock(ServerState.class);
    private MockMvc mvc;

    @BeforeEach void setUp() {
        var mapper=new ObjectMapper().registerModule(new JavaTimeModule());
        when(state.current()).thenReturn(new ServerState.State("7","NORMAL"));
        var responses=new ApiResponses(state,mapper);
        mvc=MockMvcBuilders.standaloneSetup(new SourceEvidenceIntakeReadController(access,service,responses))
            .setControllerAdvice(new ApiErrors(responses)).build();
    }

    @Test void detailBindsOriginalKeyAndReturnsSeparateEffect() throws Exception {
        var effect=SubmissionJson.object().put("applicable",false);
        when(service.read(WAREHOUSE,SUBMISSION,2,KEY.toString()))
            .thenReturn(new SourceEvidenceIntakeReadService.View("{\"status\":\"PARTIAL\"}","f".repeat(64),
                "UNVERIFIABLE_KEY",effect));

        mvc.perform(request(""))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.data.trustStatus").value("UNVERIFIABLE_KEY"))
            .andExpect(jsonPath("$.data.currentEffect.applicable").value(false));

        verify(access).require("pda:recover");
        verify(service).read(WAREHOUSE,SUBMISSION,2,KEY.toString());
    }

    @Test void originalReturnsExactStoredBytesWithoutEnvelope() throws Exception {
        byte[] original="{\"status\":\"PARTIAL\"}".getBytes(StandardCharsets.UTF_8);
        when(service.original(WAREHOUSE,SUBMISSION,2,KEY.toString())).thenReturn(original);

        mvc.perform(request("/original"))
            .andExpect(status().isOk()).andExpect(content().bytes(original))
            .andExpect(header().string("Cache-Control","no-store"))
            .andExpect(header().string("X-Content-Type-Options","nosniff"));
    }

    @Test void deniedRecoveryPermissionNeverReadsReceipt() throws Exception {
        doThrow(new BusinessException(403,"PERMISSION_DENIED","没有权限")).when(access).require("pda:recover");

        mvc.perform(request(""))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));

        verifyNoInteractions(service);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(String suffix) {
        return get("/api/v1/pda/temporary-source-evidence/receipts/"+KEY+suffix)
            .queryParam("warehouseId",WAREHOUSE.toString()).queryParam("submissionId",SUBMISSION.toString())
            .queryParam("workRound","2");
    }
}
