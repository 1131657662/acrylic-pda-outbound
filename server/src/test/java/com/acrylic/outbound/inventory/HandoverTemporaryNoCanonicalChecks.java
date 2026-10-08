package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionStore;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.WebApplicationContext;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

/** An actual independently frozen unaccepted source must remain outside handover REQUEST. */
public final class HandoverTemporaryNoCanonicalChecks {
    public static void verify(WebApplicationContext app,Path attempt) throws Exception {
        var jdbc=app.getBean(JdbcTemplate.class);var result=parse(Files.readAllBytes(attempt.resolve("android-result.json")));
        var id=uuid(result,"submissionId");var head=app.getBean(SubmissionStore.class).required(id);
        assertThat(head.status()).isEqualTo("ABORTED");assertThat(head.order()).isNull();
        assertThat(app.getBean(SubmissionStore.class).payloadHash(id)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM source_evidence_object WHERE submission_id=? AND kind='LOCAL_FROZEN_PAYLOAD'",Long.class,Ids.bytes(id))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM source_evidence_set e JOIN source_evidence_verification v ON v.set_id=e.id "
            +"WHERE e.submission_id=? AND v.result='VERIFIED_COMPLETE_AT_STOP'",Long.class,Ids.bytes(id))).isEqualTo(1);
        UUID scope=jdbc.queryForObject("SELECT BIN_TO_UUID(scope_id) FROM submission WHERE id=?",(rs,n)->UUID.fromString(rs.getString(1)),Ids.bytes(id));
        assertThat(jdbc.queryForObject("SELECT canonical_order_id FROM submission_scope WHERE id=?",byte[].class,Ids.bytes(scope))).isNull();
        var identity=IdentityFixture.create(jdbc,app.getBean(PasswordEncoder.class),"SUPERVISOR");
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)",Ids.bytes(identity.user().id()),Ids.bytes(head.warehouse()));
        var directory=Files.createDirectory(attempt.resolve("no-canonical-handover"));var http=new HandoverApprovalHttp(identity.user().username(),directory);
        var sql=new TemporaryDispositionSqlChecks(jdbc,directory.resolve("sql"));var before=sql.snapshot("before");
        var local=new HandoverTemporaryLocalSql(jdbc,directory);var roots=local.snapshot("before");
        reject(http,head,scope);
        assertThat(sql.snapshot("after")).isEqualTo(before);assertThat(local.snapshot("after")).isEqualTo(roots);
    }
    private static void reject(HandoverApprovalHttp http,SubmissionStore.Head head,UUID scope) throws Exception {
        http.get("/handovers/scopes/"+scope+"/context?warehouseId="+head.warehouse(),404,"HANDOVER_CASE_NOT_FOUND");
        var request=object().put("commandVersion",1).put("commandType","REQUEST").put("commandId",UUID.randomUUID().toString())
            .put("warehouseId",head.warehouse().toString()).put("scopeId",scope.toString()).put("transportRecoveryEpoch","1")
            .put("reason","无canonical的LOCAL原件不能授予交接");request.set("expected",object());
        http.command(request,404,"HANDOVER_CASE_NOT_FOUND");
    }
}
