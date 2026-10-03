package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.assertThat;

/** Product GET contexts drive the commands; authentic original stop/abort supplies the approval prerequisite. */
final class HandoverReadChecks {
    private final HandoverRollbackFixture f;
    private final HandoverRollbackFacts facts;
    private final HandoverReadHttp maker,checker,other,denied,anonymous;
    private final Path attempt,http;
    private final UUID otherWarehouse=Ids.next();
    private final Map<String,byte[]> originals=new LinkedHashMap<>();
    private final Map<String,ObjectNode> commands=new LinkedHashMap<>();
    private int snapshots;
    HandoverReadChecks(ConfigurableApplicationContext app,Path attempt)throws Exception {
        this.attempt=attempt;http=Files.createDirectory(attempt.resolve("handover-http"));f=new HandoverRollbackFixture(app);
        facts=new HandoverRollbackFacts(f,f.jdbc);String password=app.getBean(PasswordEncoder.class).encode("Test-password-123");
        f.jdbc.update("UPDATE app_user SET password_hash=? WHERE id IN (?,?)",password,Ids.bytes(f.requester.id()),Ids.bytes(f.approver.id()));
        maker=new HandoverReadHttp(f.requester.username(),http,"maker");checker=new HandoverReadHttp(f.approver.username(),http,"checker");
        other=new HandoverReadHttp(user(password,true),http,"other");denied=new HandoverReadHttp(user(password,false),http,"denied");
        anonymous=new HandoverReadHttp(null,http,"anonymous");
    }
    void verify()throws Exception {
        var before=facts.snapshot();String path="/handovers/scopes/"+f.scope+"/context?warehouseId="+f.warehouse;
        var scope=SubmissionJson.parse(maker.get(path,200));denied.get(path,403);anonymous.get(path,401);
        other.get(path.replace(f.warehouse.toString(),otherWarehouse.toString()),404);unchanged(before);
        var request=base("REQUEST").put("scopeId",f.scope.toString());request.set("expected",scope.path("expected"));
        byte[] requested=maker.command(request);remember(request,requested);
        f.stopped(UUID.fromString(SubmissionJson.parse(requested).path("businessDecision").path("caseId").asText()));
        for(String kind:List.of("APPROVE","PREPARE","ACTIVATE"))advance(kind);
        verifyAllOriginals();var summary=facts.summary();assertThat(summary.path("caseStatus").asText()).isEqualTo("ACTIVATED");
        for(String field:List.of("activeAssignments","outbox","pendingOutbox","activationReceipts","retained"))assertThat(summary.path(field).asLong()).as(field).isEqualTo(1);
        persist();
    }
    private void advance(String kind)throws Exception {
        var before=facts.snapshot();var context=SubmissionJson.parse(checker.get(casePath(kind),200));
        assertThat(context.path("originalCommand")).isEqualTo(commands.get("REQUEST"));context.path("capabilities").forEach(value->assertThat(value.asBoolean()).isFalse());
        unchanged(before);var command=base(kind).put("caseId",f.caseId.toString());command.set("expected",context.path("expected"));
        if(kind.equals("APPROVE"))f.source.approveFields(command);
        else command.put("targetDeviceId",f.device.toString()).put("targetUserId",f.operator.toString());
        remember(command,checker.command(command));
    }
    private void verifyAllOriginals()throws Exception {
        var before=facts.snapshot();
        for(String kind:commands.keySet()) {
            var owner=kind.equals("REQUEST")?maker:checker;assertThat(owner.get(receiptPath(kind),200)).isEqualTo(originals.get(kind));other.get(receiptPath(kind),404);
        }
        checker.get(receiptPath("REQUEST"),404);maker.get(receiptPath("APPROVE"),404);
        maker.get(receiptPath("REQUEST").replace("commandType=REQUEST","commandType=APPROVE"),404);
        maker.get(receiptPath("REQUEST").replace(f.caseId.toString(),UUID.randomUUID().toString()),404);
        maker.get(receiptPath("REQUEST").replace(commands.get("REQUEST").path("commandId").asText(),UUID.randomUUID().toString()),404);
        other.get(receiptPath("REQUEST").replace(f.warehouse.toString(),otherWarehouse.toString()),404);
        denied.get(casePath("APPROVE"),403);checker.get(casePath("DELETE"),404);
        checker.get(casePath("APPROVE").replace(f.caseId.toString(),UUID.randomUUID().toString()),404);unchanged(before);
    }
    void restart(ConfigurableApplicationContext app)throws Exception {
        var makerAgain=new HandoverReadHttp(f.requester.username(),http,"restart-maker");var checkerAgain=new HandoverReadHttp(f.approver.username(),http,"restart-checker");
        var restarted=new HandoverRollbackFacts(f,app.getBean(JdbcTemplate.class));var before=restarted.snapshot();
        for(String kind:commands.keySet())assertThat((kind.equals("REQUEST")?makerAgain:checkerAgain).get(receiptPath(kind),200)).isEqualTo(originals.get(kind));
        var after=restarted.snapshot();assertThat(after).isEqualTo(before);
        Files.writeString(attempt.resolve("read-restart-before.json"),before.toPrettyString());Files.writeString(attempt.resolve("read-restart-after.json"),after.toPrettyString());
    }
    private String user(String password,boolean supervisor) {
        UUID id=Ids.next();String name="read-other-"+id;
        f.jdbc.update("INSERT INTO app_user(id,username,display_name,password_hash,created_at) VALUES(?,?,?,?,UTC_TIMESTAMP(3))",Ids.bytes(id),name,"read fixture",password);
        f.jdbc.update("INSERT INTO user_warehouse VALUES(?,?)",Ids.bytes(id),Ids.bytes(f.warehouse));
        if(supervisor) {
            assertThat(f.jdbc.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code='SUPERVISOR'",Ids.bytes(id))).isEqualTo(1);
            f.jdbc.update("INSERT INTO warehouse(id,code,name) VALUES(?,?,?)",Ids.bytes(otherWarehouse),"HR-"+id,"read isolation");
            f.jdbc.update("INSERT INTO user_warehouse VALUES(?,?)",Ids.bytes(id),Ids.bytes(otherWarehouse));
        }
        return name;
    }
    private void unchanged(ObjectNode before)throws Exception {
        var after=facts.snapshot();int number=++snapshots;Files.writeString(attempt.resolve("read-"+number+"-before.json"),before.toPrettyString());
        Files.writeString(attempt.resolve("read-"+number+"-after.json"),after.toPrettyString());assertThat(after).isEqualTo(before);
    }
    private ObjectNode base(String kind) {
        return SubmissionJson.object().put("commandVersion",1).put("commandType",kind).put("commandId",UUID.randomUUID().toString())
            .put("warehouseId",f.warehouse.toString()).put("transportRecoveryEpoch","1").put("reason","read-context consumer");
    }
    private String casePath(String kind){return "/handovers/"+f.caseId+"/context?warehouseId="+f.warehouse+"&commandType="+kind;}
    private String receiptPath(String kind){return "/handovers/"+f.caseId+"/commands/"+commands.get(kind).path("commandId").asText()+"?warehouseId="+f.warehouse+"&commandType="+kind;}
    private void remember(ObjectNode command,byte[] receipt)throws Exception {
        String kind=command.path("commandType").asText();commands.put(kind,command);originals.put(kind,receipt);
        Files.write(attempt.resolve("read-original-"+kind+".json"),receipt);Files.writeString(attempt.resolve("read-command-"+kind+".json"),SubmissionJson.canonical(command));
    }
    private void persist()throws Exception {
        Files.writeString(attempt.resolve("real-records.tsv"),f.warehouse+"\t"+f.order+"\t"+f.caseId+"\n");
        Files.writeString(attempt.resolve("read-final-summary.json"),facts.summary().toPrettyString());
    }
}
