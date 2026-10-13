package com.acrylic.outbound.independent;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.devices.DeviceStore;
import com.acrylic.outbound.pda.PdaSessions;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IndependentCredentialServiceTest {
    private final IndependentCredentialAccess access=mock(IndependentCredentialAccess.class);
    private final IndependentCredentialStore store=mock(IndependentCredentialStore.class);
    private final IndependentCredentialTokens tokens=mock(IndependentCredentialTokens.class);
    @Test void disabledEntryDoesNotIssueAnyIdentity() {
        var service=new IndependentCredentialService(new IndependentEnabled(false),access,store,tokens);
        assertThatThrownBy(()->service.issue(new IndependentCredentialService.Context("1","synthetic-original-key","request"),
            new IndependentCredentialService.Command(UUID.randomUUID(),"a".repeat(64)))).isInstanceOf(BusinessException.class);
        verifyNoInteractions(access,store,tokens);
    }
    @Test void lockedRegistrationMismatchCannotReachSignerOrStore() {
        var actor=new PdaSessions.Principal(UUID.randomUUID(),UUID.randomUUID(),"synthetic",UUID.randomUUID(),UUID.randomUUID(),"1","1");
        when(access.current()).thenReturn(actor);
        var device=new DeviceStore.Device(actor.deviceId(),actor.warehouseId(),"synthetic","fixture",Base64.getEncoder().encodeToString(new byte[]{1}),
            "unused","ACTIVE","1","2026-10-13T00:00:00Z",null,false);
        when(access.lock(actor,"1")).thenReturn(device);
        var service=new IndependentCredentialService(new IndependentEnabled(true),access,store,tokens);
        assertThatThrownBy(()->service.issue(new IndependentCredentialService.Context("1","synthetic-original-key","request"),
            new IndependentCredentialService.Command(actor.deviceId(),"a".repeat(64)))).isInstanceOf(BusinessException.class);
        verifyNoInteractions(store,tokens);
    }
}
