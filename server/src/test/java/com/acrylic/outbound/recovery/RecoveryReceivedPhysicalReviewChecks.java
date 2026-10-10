package com.acrylic.outbound.recovery;

import com.acrylic.outbound.handover.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Fresh target consumes retained original checkpoint material; no source seed is re-executed. */
public final class RecoveryReceivedPhysicalReviewChecks {
    public static Result verify(WebApplicationContext app,Path attempt,Path original) throws Exception {
        var context=RecoveryReceivedArchiveSetup.createWithHistoryWindow(app.getBean(JdbcTemplate.class),attempt,original);
        var authority=RecoveryClosedTestSupply.of(context.source()).authority();
        var transfer=readCanonical(Files.readAllBytes(original.resolve("received-transfer.json")));
        var keys=new RecoveryReceiptKeys(List.of(Base64.getDecoder().decode(text(transfer,"serverPublicKey"))));
        var binding=new HandoverRecoveredTemporaryBinding(context.consumer(),context.request(),context.nativeFacts());
        var begin=new RecoveryReleaseNumberHistory.Snapshot(original.resolve("checkpoint-snapshot.json"),
            "1e23ca32e6c67da0ab039022ef6d4917453ec961a061aa320de3efe602d5864b");
        var end=new RecoveryReleaseNumberHistory.Snapshot(original.resolve("checkpoint-source-final.json"),
            "3393867cb13b75b9228ff067a5482b60fac6d0ec1d7caa7f9516a70f1f18694c");
        var history=new RecoveryReleaseNumberHistory.Request(context.request(),keys,begin,end);
        RecoveryReceivedPhysicalRestart.save(attempt,context);
        var reconciliation=new RecoveryReleaseReconciliation.Request(authority,history,keys);
        var reviewed=HandoverRecoveredPhysicalChecks.verify(app,attempt,binding,reconciliation);
        var request=new HandoverRecoveredPhysicalReview.Request(Files.readAllBytes(attempt.resolve("recovered-fault/fault-command.json")),binding,reconciliation);
        return new Result(authority,reviewed,request);
    }
    public record Result(RecoveryClosedAuthority authority,RecoveryOperationsApproval.Context reviewed,HandoverRecoveredPhysicalReview.Request request) { }
    private RecoveryReceivedPhysicalReviewChecks() { }
}
