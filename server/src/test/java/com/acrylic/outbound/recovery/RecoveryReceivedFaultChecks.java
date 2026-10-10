package com.acrylic.outbound.recovery;

import com.acrylic.outbound.handover.*;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.Base64;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.web.context.WebApplicationContext;

/** Binds the restored original archive to the actual application database, then exercises its existing command kernel. */
public final class RecoveryReceivedFaultChecks {
    public static void verify(WebApplicationContext app,Path attempt,Path original) throws Exception {
        var context=RecoveryReceivedArchiveSetup.create(app.getBean(JdbcTemplate.class),attempt,original);
        var binding=new HandoverRecoveredTemporaryBinding(context.consumer(),context.request(),context.nativeFacts());
        var transfer=RecoveryControlFormat.readCanonical(Files.readAllBytes(original.resolve("received-transfer.json")));
        var keys=new RecoveryReceiptKeys(List.of(Base64.getDecoder().decode(RecoveryControlFormat.text(transfer,"serverPublicKey"))));
        var foreign=new RecoveryReceivedTemporaryArchiveConsumer(new DelegatingDataSource(context.jdbc().getDataSource()),
            RecoveryClosedTestSupply.of(context.source()).authority(),keys);
        var foreignBinding=new HandoverRecoveredTemporaryBinding(foreign,context.request(),context.nativeFacts());
        HandoverRecoveredTemporaryChecks.verify(app,attempt,new HandoverRecoveredTemporaryChecks.Bindings(binding,foreignBinding));
    }
}
