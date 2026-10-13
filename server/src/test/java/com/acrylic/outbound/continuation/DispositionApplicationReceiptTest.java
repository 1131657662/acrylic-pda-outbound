package com.acrylic.outbound.continuation;

import com.acrylic.outbound.pda.OfflineSigner;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.Mockito.*;

class DispositionApplicationReceiptTest {
    @Test void productionDefaultCannotWriteOrSignAnApplicationOriginal() {
        var jdbc=mock(JdbcTemplate.class);var signer=mock(OfflineSigner.class);
        new DispositionApplicationReceipt(jdbc,signer,false).record(null,null,null);
        verifyNoInteractions(jdbc,signer);
    }
}
