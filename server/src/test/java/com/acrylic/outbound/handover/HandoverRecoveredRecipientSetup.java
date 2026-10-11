package com.acrylic.outbound.handover;

import com.acrylic.outbound.auth.SessionUser;
import com.acrylic.outbound.recovery.*;
import java.nio.file.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.WebApplicationContext;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Dependency preparation only; previous fence/physical suites are not mechanically rerun. */
public final class HandoverRecoveredRecipientSetup {
    public static Result create(WebApplicationContext app,Path attempt,HandoverRecoveredTemporaryBinding binding,
            RecoveryReleaseReconciliation.Request reconciliation) throws Exception {
        var f=new HandoverRecoveredTemporaryFixture(app,attempt,binding);
        var requested=f.request(); byte[] requestReceipt=f.execute(f.requester,requested);
        var fault=f.fault(uuid(parse(requestReceipt).path("businessDecision"),"caseId")); byte[] faultReceipt=f.execute(f.supervisor,fault);
        var input=new HandoverRecoveredPhysicalReview.Request(HandoverReceiptCodec.bytes(fault),binding,reconciliation);
        var approval=HandoverRecoveredPhysicalChecks.approval(f,fault,app);
        var result=HandoverRecoveredPhysicalChecks.execute(app.getBean(HandoverRecoveredPhysicalReview.class),f.supervisor,approval,input);
        Files.write(f.directory.resolve("request-command.json"),HandoverReceiptCodec.bytes(requested));
        Files.write(f.directory.resolve("request-receipt.json"),requestReceipt);
        Files.write(f.directory.resolve("fault-command.json"),HandoverReceiptCodec.bytes(fault));
        Files.write(f.directory.resolve("fault-receipt.json"),faultReceipt);
        Files.write(f.directory.resolve("approval-command.json"),HandoverReceiptCodec.bytes(approval));
        Files.write(f.directory.resolve("approval-receipt.json"),result.receipt());
        HandoverRecoveredTemporaryFixture.authenticate(f.supervisor);
        try {
            return new Result(input,app.getBean(HandoverRecoveredOperationsReview.class).inspect(input),f.supervisor,f.unprivileged);
        } finally { SecurityContextHolder.clearContext(); }
    }
    public record Result(HandoverRecoveredPhysicalReview.Request request,RecoveryOperationsApproval.Context reviewed,
        SessionUser supervisor,SessionUser registrar) { }
    private HandoverRecoveredRecipientSetup() { }
}
