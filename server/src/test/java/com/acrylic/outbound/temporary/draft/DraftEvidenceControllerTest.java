package com.acrylic.outbound.temporary.draft;

import com.acrylic.outbound.common.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DraftEvidenceControllerTest {
    private final DraftEvidenceAccess access=mock(DraftEvidenceAccess.class);
    private final DraftEvidenceOpenService opens=mock(DraftEvidenceOpenService.class);
    private final DraftEvidenceController controller=new DraftEvidenceController(opens,mock(DraftEvidenceItemService.class),
        mock(DraftEvidenceVerifyService.class),mock(DraftEvidenceReadService.class),access);
    @Test void deniedPermissionNeverReadsBody() throws Exception {
        var request=mock(HttpServletRequest.class);when(access.current()).thenThrow(new BusinessException(403,"DENIED","denied"));
        assertThatThrownBy(()->controller.open(UUID.randomUUID(),UUID.randomUUID(),"1",request)).isInstanceOf(BusinessException.class);
        verify(request,never()).getInputStream();verifyNoInteractions(opens);
    }
    @Test void oversizedKnownLengthRejectedBeforeAllocatingStream() throws Exception {
        var request=mock(HttpServletRequest.class);when(request.getContentLengthLong()).thenReturn(262145L);
        assertThatThrownBy(()->controller.open(UUID.randomUUID(),UUID.randomUUID(),"1",request)).isInstanceOf(BusinessException.class);
        verify(request,never()).getInputStream();verifyNoInteractions(opens);
    }
    @Test void unknownLengthStillBounded() {
        var request=new MockHttpServletRequest() { @Override public long getContentLengthLong() { return -1; } };
        request.setContent(new byte[262145]);
        assertThatThrownBy(()->controller.open(UUID.randomUUID(),UUID.randomUUID(),"1",request)).isInstanceOf(BusinessException.class);verifyNoInteractions(opens);
    }
    @Test void validRawResponseRemainsNoStoreAndNosniff() throws Exception {
        UUID evidence=UUID.randomUUID(),key=UUID.randomUUID();var request=new MockHttpServletRequest();byte[] bytes={123,125};request.setContent(bytes);
        when(opens.open(evidence,key,"1",bytes)).thenReturn(bytes);var result=controller.open(evidence,key,"1",request);
        assertThat(result.getBody()).isEqualTo(bytes);assertThat(result.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(result.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
    }
}
