package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import java.nio.file.Path;
import java.util.UUID;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

/** Actual installed/manifest-only receiving states cannot open another case before verified STOP selects the round. */
final class HandoverTemporaryPendingRequestChecks {
    static void verify(HandoverTemporaryTargetFixture f,Path directory,String phase) throws Exception {
        UUID scope=f.source.jdbc.queryForObject("SELECT BIN_TO_UUID(scope_id) FROM submission WHERE id=?",(rs,n)->UUID.fromString(rs.getString(1)),
            Ids.bytes(f.source.head.control().id()));
        assertThat(f.source.jdbc.queryForObject("SELECT current_submission_id FROM submission_scope WHERE id=?",byte[].class,Ids.bytes(scope))).isNull();
        assertThat(f.source.jdbc.queryForObject("SELECT phase FROM order_submission_slot WHERE scope_id=?",String.class,Ids.bytes(scope))).isEqualTo("CONTINUING");
        var view=parse(f.context.reviewer().get("/handovers/scopes/"+scope+"/context?warehouseId="+f.source.head.warehouse()));
        var request=object().put("commandVersion",1).put("commandType","REQUEST").put("commandId",UUID.randomUUID().toString())
            .put("warehouseId",f.source.head.warehouse().toString()).put("scopeId",scope.toString()).put("transportRecoveryEpoch","1")
            .put("reason","未结束接收轮不能新建交接");request.set("expected",view.path("expected"));
        var sql=new TemporaryDispositionSqlChecks(f.source.jdbc,directory.resolve("pending-"+phase));var before=sql.snapshot("before");
        var local=new HandoverTemporaryLocalSql(f.source.jdbc,directory.resolve("pending-"+phase));var roots=local.snapshot("before");
        f.context.reviewer().command(request,409,"HANDOVER_CONTEXT_CHANGED");
        assertThat(sql.snapshot("after")).isEqualTo(before);assertThat(local.snapshot("after")).isEqualTo(roots);
    }
}
