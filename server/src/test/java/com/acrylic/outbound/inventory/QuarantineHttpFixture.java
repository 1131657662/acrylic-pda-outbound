package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

final class QuarantineHttpFixture {
    final JdbcTemplate jdbc;
    final Path evidence,publicKey;
    final String warehouse,bale;
    final CorrectionAttachmentHttp maker,checker;
    final String makerUser,checkerUser;
    final JsonNode attachment;
    final String sourceCommandType;
    String caseId;
    QuarantineHttpFixture(WebApplicationContext context,Path attempt) throws Exception {
        jdbc=context.getBean(JdbcTemplate.class); evidence=attempt.resolve("quarantine-http"); Files.createDirectory(evidence);
        publicKey=attempt.resolve("private/server-public.der");
        var row=jdbc.queryForMap("SELECT BIN_TO_UUID(bale_id) bale,BIN_TO_UUID(source_warehouse_id) warehouse FROM bale_commitment WHERE state='QUARANTINED'");
        warehouse=row.get("warehouse").toString(); bale=row.get("bale").toString();
        sourceCommandType=jdbc.queryForObject("SELECT IF(d.close_mode,'CLOSE_DISPOSITION','PHYSICAL_DISPOSITION') "
            +"FROM bale_commitment k JOIN temporary_physical_disposition d ON d.id=k.disposition_id WHERE k.bale_id=UUID_TO_BIN(?)",String.class,bale);
        var passwords=context.getBean(PasswordEncoder.class);
        makerUser=actor(passwords,"outbound:correct","inventory:reconcile"); checkerUser=actor(passwords,"correction:approve");
        maker=new CorrectionAttachmentHttp(makerUser,evidence.resolve("maker-http.log")); checker=new CorrectionAttachmentHttp(checkerUser,evidence.resolve("checker-http.log"));
        byte[] bytes=("ISOLATED actual quarantine inspection for original P05 bale "+bale).getBytes(StandardCharsets.UTF_8);
        Files.write(evidence.resolve("inspection-original.bin"),bytes);
        attachment=maker.upload(warehouse,UUID.randomUUID().toString(),bytes,CorrectionAttachmentHttp.sha(bytes),200,false).path("data");
        Files.writeString(evidence.resolve("browser-input.json"),SubmissionJson.canonical(SubmissionJson.object().put("warehouseId",warehouse)
            .put("baleId",bale).put("makerUsername",makerUser).put("checkerUsername",checkerUser).put("password","Test-password-123")));
    }

    private String actor(PasswordEncoder passwords,String... permissions) {
        UUID user=Ids.next(),role=Ids.next(); String username="quarantine-"+user;
        jdbc.update("INSERT INTO app_user(id,username,display_name,password_hash,created_at) VALUES(?,?,?,?,UTC_TIMESTAMP(3))",
            Ids.bytes(user),username,"隔离开发核验人员",passwords.encode("Test-password-123"));
        jdbc.update("INSERT INTO role VALUES(?,?,?)",Ids.bytes(role),"Q_"+role.toString().substring(24),"隔离开发核验权限");
        for(String permission:permissions) jdbc.update("INSERT INTO role_permission SELECT ?,id FROM permission WHERE code=?",Ids.bytes(role),permission);
        jdbc.update("INSERT INTO user_role VALUES(?,?)",Ids.bytes(user),Ids.bytes(role));
        jdbc.update("INSERT INTO user_warehouse VALUES(?,UUID_TO_BIN(?))",Ids.bytes(user),warehouse); return username;
    }

    ObjectNode command(String suffix,CorrectionAttachmentHttp actor) throws Exception {
        String kind="RECONCILE_QUARANTINE_"+suffix;
        var context=SubmissionJson.parse(actor.get("/corrections/quarantine/context?warehouseId="+warehouse+"&commandType="+kind
            +(suffix.equals("DISCOVER")?"&baleId="+bale:"&caseId="+caseId),200));
        var command=SubmissionJson.object().put("commandVersion",1).put("commandType",kind).put("commandId",UUID.randomUUID().toString())
            .put("warehouseId",warehouse).put("transportRecoveryEpoch","1").put("reason","ISOLATED physical quarantine clearance inspection");
        command.set("expected",context.path("expected"));
        if(suffix.equals("DISCOVER")) command.put("baleId",bale); else command.put("caseId",caseId);
        if(suffix.equals("SUBMIT")) command.set("evidence",physical()); return command;
    }
    ObjectNode physical() {
        var physical=SubmissionJson.object().put("observedAt",Instant.now().minusSeconds(5).toString()).put("place","ISOLATED warehouse bay")
            .put("observation","QUARANTINE_CLEARED_IN_WAREHOUSE").put("reference","ISOLATED inspection of unchanged P05 physical barcode and release conditions");
        physical.putArray("attachments").add(attachment.deepCopy()); return physical;
    }
    byte[] signed(CorrectionAttachmentHttp actor,ObjectNode command) throws Exception {
        byte[] bytes=actor.command(command,200); SupervisorCorrectionReceipt.verify(bytes,publicKey);
        Files.writeString(evidence.resolve(command.path("commandId").asText()+"-command.json"),SubmissionJson.canonical(command));
        Files.write(evidence.resolve(command.path("commandId").asText()+"-receipt.json"),bytes);
        assertThat(actor.command(command,200)).containsExactly(bytes); return bytes;
    }
}
