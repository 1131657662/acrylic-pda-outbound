package com.acrylic.outbound.inventory;

import com.acrylic.outbound.handover.HandoverApprovalEvidenceProbe;
import com.acrylic.outbound.submissions.SubmissionFiles;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.web.context.WebApplicationContext;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

/** Second handoff consumes the actual recipient's signed stop and immutable cumulative body. */
public final class HandoverTemporaryNextHop {
    public static HandoverTemporaryAndroidBridge receiving(WebApplicationContext app, Path attempt) throws Exception {
        var output=attempt.resolve("temporary-handover-android");
        var result=parse(Files.readAllBytes(output.resolve("stop-result.json")));
        var input=parse(Files.readAllBytes(attempt.resolve("private/temporary-handover-input.json")));
        var body=parse(Files.readAllBytes(output.resolve("android-handover-payload.json")));
        var source=new HandoverTemporaryStopFixture.Source(uuid(result,"submissionId"),text(input.path("session"),"accessToken"),
            "temporary-handover-stop-round-"+number(body,"workRound"));
        var f=new HandoverTemporaryStopFixture(app,attempt,source);
        assertThat(f.head.status()).isEqualTo("ABORTED");assertThat(f.head.control().round()).isGreaterThan(1);
        byte[] stopped=f.stop(f.command,200,null);Files.write(f.directory.resolve("original-receipt.json"),stopped);
        assertThat(HandoverApprovalEvidenceProbe.consume(app,f.kase,number(f.command,"orderScanHighWatermark"))).isEqualTo(body);
        assertThat(parse(app.getBean(SubmissionFiles.class).read(sha(body)))).isEqualTo(body);
        var identity=IdentityFixture.create(f.jdbc,app.getBean(org.springframework.security.crypto.password.PasswordEncoder.class),"SUPERVISOR");
        f.jdbc.update("INSERT INTO user_warehouse VALUES(?,?)",com.acrylic.outbound.common.Ids.bytes(identity.user().id()),
            com.acrylic.outbound.common.Ids.bytes(f.head.warehouse()));
        var reviewer=new HandoverApprovalHttp(identity.user().username(),f.directory);
        var approval=approve(f,body);wrongInheritedCycle(f,body,approval,reviewer);
        f.web.command(approval,403,"HANDOVER_SELF_APPROVAL_DENIED");
        assertThat(reviewer.command(approval,200,null)).isEqualTo(reviewer.command(approval,200,null));
        f.sync();
        Files.move(output,attempt.resolve("temporary-handover-android-round-"+number(body,"workRound")));
        Files.move(attempt.resolve("private/temporary-handover-input.json"),attempt.resolve("private/temporary-handover-round-"+number(body,"workRound")+".json"));
        var context=new HandoverTemporaryApprovalChecks.TargetContext(f,reviewer,body,identity.user().username());
        return new HandoverTemporaryAndroidBridge(app,context,attempt);
    }
    private static void wrongInheritedCycle(HandoverTemporaryStopFixture f,JsonNode body,ObjectNode approval,HandoverApprovalHttp reviewer) throws Exception {
        var bale=uuid(array(body,"scans").get(0),"baleId");
        var sql=new TemporaryDispositionSqlChecks(f.jdbc,f.directory.resolve("inherited-owner-sql"));
        var original=sql.snapshot("original");
        f.jdbc.update("UPDATE bale_commitment SET dispatch_cycle=dispatch_cycle+1 WHERE bale_id=?",com.acrylic.outbound.common.Ids.bytes(bale));
        try {
            var before=sql.snapshot("wrong-cycle-before");reviewer.command(approval,409,"DISPOSITION_RESERVATION_CORRUPT");
            assertThat(sql.snapshot("wrong-cycle-after")).isEqualTo(before);
        } finally {f.jdbc.update("UPDATE bale_commitment SET dispatch_cycle=dispatch_cycle-1 WHERE bale_id=?",com.acrylic.outbound.common.Ids.bytes(bale));}
        assertThat(sql.snapshot("restored")).isEqualTo(original);
    }
    private static ObjectNode approve(HandoverTemporaryStopFixture f, JsonNode body) throws Exception {
        var context=parse(f.web.get("/handovers/"+f.kase+"/context?warehouseId="+f.head.warehouse()+"&commandType=APPROVE"));
        var request=object().put("commandVersion",1).put("commandType","APPROVE").put("commandId",UUID.randomUUID().toString())
            .put("warehouseId",f.head.warehouse().toString()).put("caseId",f.kase.toString()).put("transportRecoveryEpoch","1")
            .put("reason","多跳独立主管核对实际接收机原件").put("stopState","DEVICE_ACKED").put("integrityState","COMPLETE")
            .put("originalResultState","ABORTED").put("physicalState","MATCHED")
            .put("inputHighWatermark",text(f.command,"orderScanHighWatermark"));request.set("expected",context.path("expected"));
        var checklist=request.putArray("checklist");
        for(var scan:array(body,"scans"))if("ACTIVE".equals(text(scan,"status")))checklist.addObject()
            .put("baleId",text(scan,"baleId")).put("deliveryCode",text(scan,"deliveryCode")).put("state","RETAINED")
            .put("dispatchCycle",text(scan,"expectedDispatchCycle"));
        return request;
    }
}
