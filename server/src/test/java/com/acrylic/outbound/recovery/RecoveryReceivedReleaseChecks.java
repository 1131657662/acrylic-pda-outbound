package com.acrylic.outbound.recovery;

import com.acrylic.outbound.handover.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.web.context.WebApplicationContext;

/** Uses the retained actual original; only recovery authority and reviewing identities are synthetic. */
public final class RecoveryReceivedReleaseChecks {
    public static void verify(WebApplicationContext app,Path attempt,Path original) throws Exception {
        var context=RecoveryReceivedArchiveSetup.create(app.getBean(JdbcTemplate.class),attempt,original);
        var binding=new HandoverRecoveredTemporaryBinding(context.consumer(),context.request(),context.nativeFacts());
        var transfer=RecoveryControlFormat.readCanonical(Files.readAllBytes(original.resolve("received-transfer.json")));
        var keys=new RecoveryReceiptKeys(List.of(Base64.getDecoder().decode(RecoveryControlFormat.text(transfer,"serverPublicKey"))));
        var foreign=new RecoveryReceivedTemporaryArchiveConsumer(new DelegatingDataSource(context.jdbc().getDataSource()),
            RecoveryClosedTestSupply.of(context.source()).authority(),keys);
        HandoverRecoveredReviewChecks.verify(app,attempt,new HandoverRecoveredTemporaryChecks.Bindings(binding,
            new HandoverRecoveredTemporaryBinding(foreign,context.request(),context.nativeFacts())));
    }
}
