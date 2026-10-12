package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.assertThat;

/** Consumes only the original native product run; SQL never substitutes stop, cancellation or verification facts. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.DEFINED_PORT,properties={
    "server.port=19435","outbound.temporary.commands.enabled=true","server.servlet.session.cookie.secure=false"})
@EnabledIfEnvironmentVariable(named="P05_NATIVE_CONSUMER",matches=".+")
class TemporaryNonzeroCancellationHttpIT {
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;

    @Test void originalNativeCancellationHasTrustedPhysicalPreconditionsAndAtomicTailRollback() throws Exception {
        var input=inputs();var root=Path.of(System.getenv("P05_NATIVE_CONSUMER"));
        var nativeState=SubmissionJson.parse(Files.readString(root.resolve("native-prepared.json")));
        String id=nativeState.path("submissionId").asText(),warehouse=input.path("warehouseId").asText();
        trustedOriginals(id,nativeState);
        var http=new TemporaryNonzeroCancellationHttp(root.resolve("supervisor-http"));http.login(input.path("supervisor"));
        String path="/temporary-commands/disposition-facts?warehouseId="+warehouse+"&submissionId="+id;
        var observed=SubmissionJson.parse(http.get(path,200)).path("data");
        Files.writeString(root.resolve("observed.json"),SubmissionJson.canonical(observed));
        var command=close(observed);accessChecks(input,root,command,path);
        var sql=new TemporaryDispositionSqlChecks(jdbc,root.resolve("sql"));var before=sql.snapshot("before-read");
        assertThat(SubmissionJson.parse(http.get(path,200)).path("data").path("facts")).isEqualTo(observed.path("facts"));
        assertThat(sql.snapshot("after-read")).isEqualTo(before);
        var incomplete=command.deepCopy();((com.fasterxml.jackson.databind.node.ArrayNode)incomplete.at("/dispositionManifest/items")).remove(0);
        ((ObjectNode)incomplete.path("expected")).put("dispositionManifestSha256",SubmissionJson.sha(incomplete.path("dispositionManifest")));
        assertThat(SubmissionJson.parse(http.command(incomplete,409)).path("code").asText()).isEqualTo("DISPOSITION_MANIFEST_INCOMPLETE");
        assertThat(sql.snapshot("after-incomplete")).isEqualTo(before);
        tailRollback(http,command,sql);
        assertThat(sql.snapshot("after-tail")).isEqualTo(before);
        trustedOriginals(id,nativeState);
        Files.writeString(root.resolve("preflight-passed.json"),SubmissionJson.canonical(SubmissionJson.object()
            .put("originalNativeSource",true).put("completeStop",true).put("readOnly",true).put("tailRollback",true)
            .put("localDeletionAllowed",false)));
    }

    private JsonNode inputs() throws Exception {
        var root=Path.of(System.getenv("P05_NATIVE_CONSUMER")).toAbsolutePath().normalize();
        assertThat(root.toString().replace('\\','/')).startsWith("D:/acrylic-pda-outbound/.local/goal-20261012/");
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).matches("acrylic_e2a_cross_20260925_140[0-9]");
        assertThat(jdbc.queryForObject("SELECT @@port",Integer.class)).isEqualTo(19331);
        return SubmissionJson.parse(Files.readString(root.resolve("input.json")));
    }

    private void trustedOriginals(String id,JsonNode nativeState) {
        assertThat(jdbc.queryForObject("SELECT status FROM submission WHERE id=UUID_TO_BIN(?)",String.class,id)).isEqualTo("ABORTED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM source_evidence_verification WHERE submission_id=UUID_TO_BIN(?) "
            +"AND result='VERIFIED_COMPLETE_AT_STOP'",Integer.class,id)).isEqualTo(1);
        var rows=jdbc.queryForList("SELECT BIN_TO_UUID(original_key) original_key,original_response_bytes "
            +"FROM source_evidence_intake_receipt WHERE submission_id=UUID_TO_BIN(?)",id);
        for(var row:rows)assertThat((byte[])row.get("original_response_bytes")).containsExactly(
            Base64.getDecoder().decode(nativeState.path("receipts").path(row.get("original_key").toString()).asText()));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE source_scope_id="
            +"(SELECT scope_id FROM submission WHERE id=UUID_TO_BIN(?))",Integer.class,id)).isEqualTo(2);
    }

    private ObjectNode close(JsonNode view) {
        var body=(ObjectNode)view.path("facts").deepCopy();
        body.put("commandVersion",1).put("commandType","CLOSE_DISPOSITION").put("commandId",UUID.randomUUID().toString())
            .put("transportRecoveryEpoch","1").put("reason","隔离主管逐包卸货核实，关闭原取消来源");
        var manifest=SubmissionJson.object().put("version",1);var items=manifest.putArray("items");
        view.path("items").forEach(row->items.add(SubmissionJson.object().put("baleId",row.path("baleId").asText()).put("disposition","RELEASE")));
        body.set("dispositionManifest",manifest);
        ((ObjectNode)body.path("expected")).put("dispositionManifestVersion","1").put("dispositionManifestSha256",SubmissionJson.sha(manifest));
        return body;
    }

    private void accessChecks(JsonNode input,Path root,ObjectNode command,String path) throws Exception {
        var original=new TemporaryNonzeroCancellationHttp(root.resolve("original-http"));original.login(input.path("operator"));
        assertThat(SubmissionJson.parse(original.get(path,403)).path("code").asText()).isEqualTo("INDEPENDENT_DISPOSITION_REQUIRED");
        var own=command.deepCopy();((ObjectNode)own.path("expected")).put("userAuthVersion","1");
        assertThat(SubmissionJson.parse(original.command(own,403)).path("code").asText()).isEqualTo("INDEPENDENT_DISPOSITION_REQUIRED");
        var reader=IdentityFixture.create(jdbc,passwords,"TEMPORARY_ORDER_CONFIRMER");
        jdbc.update("INSERT INTO user_warehouse VALUES(?,UUID_TO_BIN(?))",Ids.bytes(reader.user().id()),input.path("warehouseId").asText());
        var denied=new TemporaryNonzeroCancellationHttp(root.resolve("reader-http"));denied.login(account(reader));
        assertThat(SubmissionJson.parse(denied.get(path,403)).path("code").asText()).isEqualTo("PERMISSION_DENIED");
        var outsider=IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN");
        var foreign=new TemporaryNonzeroCancellationHttp(root.resolve("foreign-http"));foreign.login(account(outsider));
        assertThat(SubmissionJson.parse(foreign.get(path,404)).path("code").asText()).isEqualTo("COMMAND_RECEIPT_NOT_FOUND");
    }

    private ObjectNode account(IdentityFixture actor) {
        return SubmissionJson.object().put("username",actor.user().username()).put("password","Test-password-123");
    }

    private void tailRollback(TemporaryNonzeroCancellationHttp http,JsonNode command,TemporaryDispositionSqlChecks sql) throws Exception {
        jdbc.execute("CREATE TRIGGER p05_native_tail AFTER INSERT ON operation_event FOR EACH ROW "
            +"BEGIN IF NEW.entity_type='TEMPORARY_COMMAND' AND NEW.event_type='TEMPORARY_CLOSE_DISPOSITION' "
            +"AND EXISTS(SELECT 1 FROM temporary_physical_disposition WHERE close_mode=1) "
            +"THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='P05_NATIVE_CLOSE_TAIL'; END IF; END");
        try {assertThat(SubmissionJson.parse(http.command(command,500)).path("code").asText()).isEqualTo("INTERNAL_ERROR");}
        finally {jdbc.execute("DROP TRIGGER p05_native_tail");}
    }
}
