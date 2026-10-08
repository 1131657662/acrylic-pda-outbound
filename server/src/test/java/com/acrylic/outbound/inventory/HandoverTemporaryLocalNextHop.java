package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.handover.HandoverApprovalEvidenceProbe;
import com.acrylic.outbound.submissions.SubmissionStore;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.web.context.WebApplicationContext;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

/** The next receiver consumes B's actual LOCAL original and STOP; B has never uploaded an accepted payload. */
public final class HandoverTemporaryLocalNextHop {
    public static HandoverTemporaryAndroidBridge receiving(WebApplicationContext app,Path attempt) throws Exception {
        var output=attempt.resolve("temporary-handover-android");var result=parse(Files.readAllBytes(output.resolve("localStop-result.json")));
        var input=parse(Files.readAllBytes(attempt.resolve("private/temporary-handover-input.json")));
        var body=parse(Files.readAllBytes(output.resolve("android-handover-payload.json")));
        var source=new HandoverTemporaryStopFixture.Source(uuid(result,"submissionId"),text(input.path("session"),"accessToken"),"temporary-handover-local-stop");
        var f=new HandoverTemporaryStopFixture(app,attempt,source);
        assertThat(f.head.status()).isEqualTo("ABORTED");assertThat(f.head.control().round()).isEqualTo(2);
        assertThat(app.getBean(SubmissionStore.class).payloadHash(f.head.control().id())).isNull();
        var receipt=parse(f.head.receipt());assertThat(receipt.path("payloadPresent").asBoolean()).isFalse();
        assertThat(receipt.path("localFrozenPayloadSha256").asText()).isEqualTo(sha(body));
        Files.write(f.directory.resolve("original-receipt.json"),f.stop(f.command,200,null));
        assertThat(HandoverApprovalEvidenceProbe.consume(app,f.kase,number(f.command,"orderScanHighWatermark"))).isEqualTo(body);
        var identity=IdentityFixture.create(f.jdbc,app.getBean(org.springframework.security.crypto.password.PasswordEncoder.class),"SUPERVISOR");
        f.jdbc.update("INSERT INTO user_warehouse VALUES(?,?)",Ids.bytes(identity.user().id()),Ids.bytes(f.head.warehouse()));
        var reviewer=new HandoverApprovalHttp(identity.user().username(),f.directory);var approval=approve(f,body);
        f.web.command(approval,403,"HANDOVER_SELF_APPROVAL_DENIED");
        HandoverTemporaryLocalBoundaryChecks.approval(f,reviewer,approval);
        assertThat(reviewer.command(approval,200,null)).isEqualTo(reviewer.command(approval,200,null));f.sync();
        Files.move(output,attempt.resolve("temporary-handover-android-round-2"));
        Files.move(attempt.resolve("private/temporary-handover-input.json"),attempt.resolve("private/temporary-handover-round-2.json"));
        return new HandoverTemporaryAndroidBridge(app,new HandoverTemporaryApprovalChecks.TargetContext(f,reviewer,body,identity.user().username()),attempt);
    }
    private static com.fasterxml.jackson.databind.node.ObjectNode approve(HandoverTemporaryStopFixture f,JsonNode body) throws Exception {
        var context=parse(f.web.get("/handovers/"+f.kase+"/context?warehouseId="+f.head.warehouse()+"&commandType=APPROVE"));
        var request=object().put("commandVersion",1).put("commandType","APPROVE").put("commandId",UUID.randomUUID().toString())
            .put("warehouseId",f.head.warehouse().toString()).put("caseId",f.kase.toString()).put("transportRecoveryEpoch","1")
            .put("reason","独立主管核对接收轮LOCAL冻结原件后交接").put("stopState","DEVICE_ACKED").put("integrityState","COMPLETE")
            .put("originalResultState","ABORTED").put("physicalState","MATCHED").put("inputHighWatermark",text(f.command,"orderScanHighWatermark"));
        request.set("expected",context.path("expected"));var checklist=request.putArray("checklist");
        for(var scan:array(body,"scans"))if("ACTIVE".equals(text(scan,"status")))checklist.addObject()
            .put("baleId",text(scan,"baleId")).put("deliveryCode",text(scan,"deliveryCode")).put("state","RETAINED").put("dispatchCycle",text(scan,"expectedDispatchCycle"));
        return request;
    }
}
