package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.handover.HandoverApprovalEvidenceProbe;
import com.acrylic.outbound.submissions.SubmissionFiles;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.web.context.WebApplicationContext;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

/** Starts a different receiving device from the actual R06 third-round body and STOP. */
public final class HandoverTemporaryR06NextHop {
    public static HandoverTemporaryAndroidBridge receiving(WebApplicationContext app, Path attempt) throws Exception {
        var output=attempt.resolve("temporary-handover-android");
        var result=parse(Files.readAllBytes(output.resolve("continuationStop-result.json")));
        var input=parse(Files.readAllBytes(attempt.resolve("private/temporary-handover-input.json")));
        var body=parse(Files.readAllBytes(output.resolve("android-continuation-payload.json")));
        var source=new HandoverTemporaryStopFixture.Source(uuid(result,"submissionId"),text(input.path("session"),"accessToken"),
            "temporary-handover-r06-round-"+number(body,"workRound"));
        var f=new HandoverTemporaryStopFixture(app,attempt,source);
        assertThat(f.head.status()).isEqualTo("ABORTED");assertThat(f.head.control().round()).isEqualTo(3);
        assertThat(body.has("temporaryContinuation")).isTrue();assertThat(body.has("temporaryHandover")).isFalse();
        byte[] stopped=f.stop(f.command,200,null);Files.write(f.directory.resolve("original-receipt.json"),stopped);
        assertThat(HandoverApprovalEvidenceProbe.consume(app,f.kase,number(f.command,"orderScanHighWatermark"))).isEqualTo(body);
        assertThat(parse(app.getBean(SubmissionFiles.class).read(sha(body)))).isEqualTo(body);
        var identity=IdentityFixture.create(f.jdbc,app.getBean(org.springframework.security.crypto.password.PasswordEncoder.class),"SUPERVISOR");
        f.jdbc.update("INSERT INTO user_warehouse VALUES(?,?)",Ids.bytes(identity.user().id()),Ids.bytes(f.head.warehouse()));
        var reviewer=new HandoverApprovalHttp(identity.user().username(),f.directory);
        var approval=approve(f,body);f.web.command(approval,403,"HANDOVER_SELF_APPROVAL_DENIED");
        assertThat(reviewer.command(approval,200,null)).isEqualTo(reviewer.command(approval,200,null));
        f.sync();preserve(attempt,output);
        var context=new HandoverTemporaryApprovalChecks.TargetContext(f,reviewer,body,identity.user().username());
        return new HandoverTemporaryAndroidBridge(app,context,attempt);
    }

    private static void preserve(Path attempt,Path output) throws Exception {
        Files.move(output,attempt.resolve("temporary-handover-android-round-3"));
        Files.move(attempt.resolve("private/temporary-handover-input.json"),attempt.resolve("private/temporary-handover-round-3.json"));
    }

    private static ObjectNode approve(HandoverTemporaryStopFixture f,JsonNode body) throws Exception {
        var context=parse(f.web.get("/handovers/"+f.kase+"/context?warehouseId="+f.head.warehouse()+"&commandType=APPROVE"));
        var request=object().put("commandVersion",1).put("commandType","APPROVE").put("commandId",UUID.randomUUID().toString())
            .put("warehouseId",f.head.warehouse().toString()).put("caseId",f.kase.toString()).put("transportRecoveryEpoch","1")
            .put("reason","独立主管核对实际R06停止原件后交接").put("stopState","DEVICE_ACKED").put("integrityState","COMPLETE")
            .put("originalResultState","ABORTED").put("physicalState","MATCHED")
            .put("inputHighWatermark",text(f.command,"orderScanHighWatermark"));request.set("expected",context.path("expected"));
        var checklist=request.putArray("checklist");
        for(var scan:array(body,"scans"))if("ACTIVE".equals(text(scan,"status")))checklist.addObject()
            .put("baleId",text(scan,"baleId")).put("deliveryCode",text(scan,"deliveryCode")).put("state","RETAINED")
            .put("dispatchCycle",text(scan,"expectedDispatchCycle"));
        return request;
    }
}
